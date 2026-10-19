package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.dtos.RebalanceLegResponse;
import com.neueda.app.dtos.RebalanceRunResponse;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.PlannedAction;
import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.exceptions.PieNotLinkedException;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.exceptions.RebalanceConflictException;
import com.neueda.app.exceptions.RebalanceRunNotFoundException;
import com.neueda.app.exceptions.TradingException;
import com.neueda.app.events.RebalanceRunFinished;
import com.neueda.app.models.Account;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Order;
import com.neueda.app.models.Pie;
import com.neueda.app.models.RebalanceLeg;
import com.neueda.app.models.RebalanceRun;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.RebalanceLegRepository;
import com.neueda.app.repositories.RebalanceRunRepository;
import com.neueda.app.services.RebalanceCalculator.PlannedLeg;
import com.neueda.app.services.RebalanceCalculator.RebalancePlan;
import com.neueda.app.utils.MarketHours;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a rebalance in two phases: MARKET sells first, then, once every sell is resolved, buys sized
 * from the cash and prices at that moment. Each step is one transaction, so the orders, their legs and
 * the run's status commit together, and a step repeated by a duplicate event or a restart finds the
 * run already moved on and does nothing.
 *
 * A run is started under the account's row lock, which keeps runs, links and unlinks of an account in
 * turn. After that a run is changed under its own row lock, which settlement never takes. A run that ends
 * publishes RebalanceRunFinished; what follows from it happens after the commit, never under these locks.
 */
@Service
@Transactional
@Slf4j
public class RebalanceRunService {

    private final RebalanceRunRepository runRepository;
    private final RebalanceLegRepository legRepository;
    private final AccountRepository accountRepository;
    private final AccountPieRepository accountPieRepository;
    private final OrderRepository orderRepository;
    private final PieRepository pieRepository;
    private final PieService pieService;
    private final RebalancePlanner planner;
    private final OrderValidator orderValidator;
    private final OrderService orderService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RebalanceRunService(RebalanceRunRepository runRepository, RebalanceLegRepository legRepository,
                               AccountRepository accountRepository, AccountPieRepository accountPieRepository,
                               OrderRepository orderRepository, PieRepository pieRepository,
                               PieService pieService, RebalancePlanner planner, OrderValidator orderValidator,
                               OrderService orderService, ApplicationEventPublisher events, Clock clock) {
        this.runRepository = runRepository;
        this.legRepository = legRepository;
        this.accountRepository = accountRepository;
        this.accountPieRepository = accountPieRepository;
        this.orderRepository = orderRepository;
        this.pieRepository = pieRepository;
        this.pieService = pieService;
        this.planner = planner;
        this.orderValidator = orderValidator;
        this.orderService = orderService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Starts a run to the latest version of the pie the account follows, or to what a copy of a pie that is
     * no longer public last applied, and places its sells.
     * Refused while the account has an active run, or orders still waiting that the run could not
     * tell apart from its own: they hold cash and shares the plan counts on.
     */
    public RebalanceRunResponse start(String accountId) {
        Account account = accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        account.validateStatus();
        AccountPie link = accountPieRepository.findById(accountId)
            .orElseThrow(() -> new PieNotLinkedException("Account is not following a pie: " + accountId));
        if (runRepository.existsByAccountIdAndStatusIn(accountId, RebalanceStatus.ACTIVE)) {
            throw new RebalanceConflictException("A rebalance is already running for account " + accountId);
        }
        if (orderRepository.existsByAccountIdAndStatus(accountId, OrderStatus.PENDING)) {
            throw new RebalanceConflictException(
                "Account " + accountId + " has pending orders. Wait for them or cancel them, then rebalance");
        }
        Pie pie = pieRepository.findById(link.getPieId())
            .orElseThrow(() -> new PieNotFoundException("Pie not found: " + link.getPieId()));

        Integer version = link.targetVersion(pie);
        if (version == null) {
            throw new PieNotFoundException("Pie not found: " + pie.getPieId());
        }

        RebalanceRun run = runRepository.save(RebalanceRun.start(accountId, pie.getPieId(), version));
        RebalancePlan plan = planner.plan(accountId, pieService.weightsAt(run.getPieId(), run.getTargetVersion()));
        place(run, plan, PlannedAction.SELL);
        step(run);
        log.info("Rebalance run started: runId={}, accountId={}, version={}", run.getRunId(), accountId,
            run.getTargetVersion());
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public RebalanceRunResponse get(UUID runId) {
        return toResponse(runRepository.findById(runId).orElseThrow(() -> notFound(runId)));
    }

    /** Cancels the run's waiting orders and ends it CANCELLED. Fills already settled stay. */
    public RebalanceRunResponse cancel(UUID runId) {
        RebalanceRun run = runRepository.findByIdForUpdate(runId).orElseThrow(() -> notFound(runId));
        if (!run.isActive()) {
            throw new RebalanceConflictException("Rebalance run " + runId + " is already " + run.getStatus());
        }
        int cancelled = 0;
        for (RebalanceLeg leg : legRepository.findByRunIdOrderBySideDescSymbolAsc(runId)) {
            // Guarded like OrderService.cancelOrder: a fill that got there first stays filled
            cancelled += orderRepository.changeStatus(leg.getOrderId(), OrderStatus.PENDING, OrderStatus.CANCELLED);
        }
        // changeStatus cleared the persistence context, so load the run again to change it
        run = runRepository.findById(runId).orElseThrow(() -> notFound(runId));
        run.cancel("Cancelled by user, " + cancelled + " pending order(s) cancelled");
        events.publishEvent(new RebalanceRunFinished(runId, run.getAccountId(), run.getPieId(), run.getStatus()));
        log.info("Rebalance run cancelled: runId={}, cancelledOrders={}", runId, cancelled);
        return toResponse(run);
    }

    /** Moves the run holding this order on, if the order was the last of its phase to resolve. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderResolved(UUID orderId) {
        legRepository.findById(orderId).ifPresent(leg -> advanceLocked(leg.getRunId()));
    }

    /** Moves the run on if everything it waits for has resolved. Used to recover after a restart. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void advance(UUID runId) {
        advanceLocked(runId);
    }

    private void advanceLocked(UUID runId) {
        runRepository.findByIdForUpdate(runId).filter(RebalanceRun::isActive).ifPresent(this::step);
    }

    /** SELLING with every sell resolved becomes BUYING, and BUYING with every buy resolved ends the run. */
    private void step(RebalanceRun run) {
        List<RebalanceLeg> legs = legRepository.findByRunIdOrderBySideDescSymbolAsc(run.getRunId());
        Map<UUID, Order> orders = ordersOf(legs);
        if (isWaiting(run, legs, orders)) {
            return;
        }
        if (run.getStatus() == RebalanceStatus.SELLING) {
            run.startBuying();
            // Sized from the cash and prices now, without counting sells that did not go through
            try {
                place(run, planner.planBuys(run.getAccountId(),
                    pieService.weightsAt(run.getPieId(), run.getTargetVersion())), PlannedAction.BUY);
            } catch (PriceNotFoundException e) {
                run.addFailure("Buys could not be planned: " + e.getMessage());
                finish(run, legs, orders);
                return;
            }
            step(run);
        } else {
            finish(run, legs, orders);
        }
    }

    private void place(RebalanceRun run, RebalancePlan plan, PlannedAction action) {
        OrderSide side = OrderSide.valueOf(action.name());
        for (PlannedLeg leg : plan.legs()) {
            if (leg.action() != action) {
                continue;
            }
            PlaceOrderRequest request = new PlaceOrderRequest(run.getAccountId(), leg.symbol(), side.name(),
                "MARKET", leg.quantity(), null, "rebalance:" + run.getRunId() + ":" + side + ":" + leg.symbol());
            try {
                // Checked first: a refusal from placeOrder inside this transaction would roll it all back
                orderValidator.validate(request);
            } catch (TradingException | IllegalArgumentException e) {
                run.addFailure(side + " " + leg.symbol() + " not placed: " + e.getMessage());
                continue;
            }
            UUID orderId = orderService.placeOrder(request).getOrderId();
            legRepository.save(new RebalanceLeg(run.getRunId(), orderId, side, leg.symbol(), leg.quantity()));
        }
    }

    private void finish(RebalanceRun run, List<RebalanceLeg> legs, Map<UUID, Order> orders) {
        for (RebalanceLeg leg : legs) {
            String reason = failureReason(orders.get(leg.getOrderId()));
            if (reason != null) {
                run.addFailure(leg.getSide() + " " + leg.getSymbol() + ": " + reason);
            }
        }
        run.finish();
        accountPieRepository.applyVersion(run.getAccountId(), run.getPieId(), run.getTargetVersion());
        events.publishEvent(new RebalanceRunFinished(run.getRunId(), run.getAccountId(), run.getPieId(), run.getStatus()));
        log.info("Rebalance run ended: runId={}, status={}", run.getRunId(), run.getStatus());
    }

    private boolean isWaiting(RebalanceRun run, List<RebalanceLeg> legs, Map<UUID, Order> orders) {
        OrderSide phase = run.getStatus() == RebalanceStatus.SELLING ? OrderSide.SELL : OrderSide.BUY;
        return legs.stream()
            .filter(leg -> leg.getSide() == phase)
            .anyMatch(leg -> !isResolved(orders.get(leg.getOrderId())));
    }

    private Map<UUID, Order> ordersOf(List<RebalanceLeg> legs) {
        return orderRepository.findAllById(legs.stream().map(RebalanceLeg::getOrderId).toList()).stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
    }

    private static boolean isResolved(Order order) {
        return switch (order.getStatus()) {
            case FILLED, REJECTED, CANCELLED, EXPIRED -> true;
            case PENDING, PUBLISHED, PARTIALLY_FILLED -> false;
        };
    }

    /** Why a resolved order did not fill, or null if it filled or is still waiting. */
    private static String failureReason(Order order) {
        return switch (order.getStatus()) {
            case REJECTED -> order.getRejectionReason() != null ? order.getRejectionReason() : "Rejected";
            case CANCELLED -> "Cancelled";
            case EXPIRED -> "Expired";
            case FILLED, PENDING, PUBLISHED, PARTIALLY_FILLED -> null;
        };
    }

    private RebalanceRunResponse toResponse(RebalanceRun run) {
        List<RebalanceLeg> legs = legRepository.findByRunIdOrderBySideDescSymbolAsc(run.getRunId());
        Map<UUID, Order> orders = ordersOf(legs);
        boolean waitingForMarketOpen = run.isActive() && isWaiting(run, legs, orders)
            && !MarketHours.isOpen(clock.instant());
        List<RebalanceLegResponse> legResponses = legs.stream()
            .map(leg -> {
                Order order = orders.get(leg.getOrderId());
                return new RebalanceLegResponse(leg.getOrderId(), leg.getSymbol(),
                    leg.getSide().name(), leg.getQuantity(), order.getStatus(), failureReason(order));
            })
            .toList();
        return new RebalanceRunResponse(run.getRunId(), run.getAccountId(), run.getPieId(), run.getTargetVersion(),
            run.getStatus(), waitingForMarketOpen, run.getFailureSummary(),
            run.getCreatedAt().toInstant(ZoneOffset.UTC), run.getUpdatedAt().toInstant(ZoneOffset.UTC), legResponses);
    }

    private static RebalanceRunNotFoundException notFound(UUID runId) {
        return new RebalanceRunNotFoundException("Rebalance run not found: " + runId);
    }
}
