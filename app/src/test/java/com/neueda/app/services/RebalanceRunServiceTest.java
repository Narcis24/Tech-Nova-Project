package com.neueda.app.services;

import com.neueda.app.dtos.PieSliceRequest;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.dtos.RebalanceLegResponse;
import com.neueda.app.dtos.RebalanceRunResponse;
import com.neueda.app.dtos.SavePieRequest;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.events.OrderResolved;
import com.neueda.app.exceptions.PieNotLinkedException;
import com.neueda.app.exceptions.RebalanceConflictException;
import com.neueda.app.models.Account;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.models.Position;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.PieSliceRepository;
import com.neueda.app.repositories.PieVersionRepository;
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.repositories.PriceRepository;
import com.neueda.app.repositories.RebalanceLegRepository;
import com.neueda.app.repositories.RebalanceRunRepository;
import com.neueda.events.EventEnvelope;
import com.neueda.events.OrderExecutedEvent;
import com.neueda.events.OrderRejectedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Rebalance runs against a real (H2) database, with fills and rejections delivered through the real
 * OrderSettlementService so the after-commit events drive the run. Runs without a test transaction,
 * as events only fire when a transaction commits.
 */
@DataJpaTest
@Import({RebalanceRunService.class, RebalanceCoordinator.class, RebalancePlanner.class, RebalanceCalculator.class,
    PortfolioService.class, PositionValuator.class, PriceService.class, OrderService.class, OrderValidator.class,
    OrderSettlementService.class, PieService.class, RebalanceRunServiceTest.JsonConfig.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RebalanceRunServiceTest {

    private static final String ACCOUNT = "REB001";
    private static final String OWNER = "jay";
    private static final Instant MARKET_OPEN = Instant.parse("2026-10-14T15:00:00Z");
    private static final Instant MARKET_CLOSED = Instant.parse("2026-10-16T07:24:00Z");

    @TestConfiguration
    static class JsonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired private RebalanceRunService runService;
    @Autowired private RebalanceCoordinator coordinator;
    @Autowired private OrderSettlementService settlement;
    @Autowired private OrderService orderService;
    @Autowired private PieService pieService;
    @Autowired private ObjectMapper mapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AccountRepository accountRepository;
    @Autowired private InstrumentRepository instrumentRepository;
    @Autowired private PriceRepository priceRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PieRepository pieRepository;
    @Autowired private PieVersionRepository pieVersionRepository;
    @Autowired private PieSliceRepository pieSliceRepository;
    @Autowired private AccountPieRepository accountPieRepository;
    @Autowired private RebalanceRunRepository runRepository;
    @Autowired private RebalanceLegRepository legRepository;

    @MockitoBean private EventProducerService eventProducerService;
    @MockitoBean private Clock clock;

    private UUID pieId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(MARKET_OPEN);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        for (String symbol : List.of("AAPL", "MSFT", "GOOG", "TSLA")) {
            instrumentRepository.save(new Instrument(symbol, symbol + " Inc.", AssetClass.EQUITY, "USD", true));
            priceRepository.save(new Price(symbol, LocalDate.now(), new BigDecimal("100.00")));
        }
        pieId = pieService.create(OWNER, pieRequest("50", "40")).getPieId();
    }

    @AfterEach
    void tearDown() {
        legRepository.deleteAll();
        runRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
        accountPieRepository.deleteAll();
        pieSliceRepository.deleteAll();
        pieVersionRepository.deleteAll();
        pieRepository.deleteAll();
        accountRepository.deleteAll();
        priceRepository.deleteAll();
        instrumentRepository.deleteAll();
    }

    // ---------- the run ----------

    @Test
    void noSells_goesStraightToBuying_thenCompletes() throws Exception {
        account("10000.00");

        RebalanceRunResponse started = runService.start(ACCOUNT);

        assertEquals(RebalanceStatus.BUYING, started.getStatus());
        assertEquals(List.of("BUY AAPL 50", "BUY MSFT 40"), summary(started));
        fillAll(started, "100.00");

        RebalanceRunResponse done = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.COMPLETED, done.getStatus());
        assertNull(done.getFailureSummary());
        assertEquals(1, appliedVersion());
    }

    @Test
    void sellsFirst_thenBuysSizedFromTheCashAndPricesAfterTheSells() throws Exception {
        account("5000.00");
        hold("GOOG", 50);

        RebalanceRunResponse started = runService.start(ACCOUNT);

        assertEquals(RebalanceStatus.SELLING, started.getStatus());
        assertEquals(List.of("SELL GOOG 50"), summary(started));

        // The sell fills above the reference price, so there is more cash than the preview knew about
        fill(started.getLegs().get(0).getOrderId(), "110.00");

        RebalanceRunResponse buying = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.BUYING, buying.getStatus());
        assertEquals(List.of("SELL GOOG 50", "BUY AAPL 52", "BUY MSFT 42"), summary(buying));
        for (RebalanceLegResponse leg : buying.getLegs()) {
            String key = orderRepository.findById(leg.getOrderId()).orElseThrow().getIdempotencyKey();
            assertEquals("rebalance:" + started.getRunId() + ":" + leg.getSide() + ":" + leg.getSymbol(), key);
        }

        fillAll(buying, "100.00");
        assertEquals(RebalanceStatus.COMPLETED, runService.get(started.getRunId()).getStatus());
        assertTrue(accountRepository.findById(ACCOUNT).orElseThrow().getCashBalance().signum() >= 0);
    }

    @Test
    void rejectedSell_buysAreSizedWithoutItsProceeds_andTheRunIsPartial() throws Exception {
        account("5000.00");
        hold("GOOG", 50);
        RebalanceRunResponse started = runService.start(ACCOUNT);

        reject(started.getLegs().get(0).getOrderId(), "No quote");

        RebalanceRunResponse buying = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.BUYING, buying.getStatus());
        // 5000 cash less the 1% buffer buys 49 AAPL, and nothing is left for MSFT
        assertEquals(List.of("SELL GOOG 50", "BUY AAPL 49"), summary(buying));
        assertEquals("No quote", buying.getLegs().get(0).getFailureReason());

        fillAll(buying, "100.00");

        RebalanceRunResponse done = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.PARTIAL, done.getStatus());
        assertEquals("SELL GOOG: No quote", done.getFailureSummary());
        assertEquals(1, appliedVersion());
    }

    @Test
    void rejectedBuy_endsPartialWithTheReason() throws Exception {
        account("10000.00");
        RebalanceRunResponse started = runService.start(ACCOUNT);
        UUID aapl = orderOf(started, "AAPL");
        UUID msft = orderOf(started, "MSFT");

        fill(aapl, "100.00");
        assertEquals(RebalanceStatus.BUYING, runService.get(started.getRunId()).getStatus());
        reject(msft, "No quote");

        RebalanceRunResponse done = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.PARTIAL, done.getStatus());
        assertEquals("BUY MSFT: No quote", done.getFailureSummary());
        assertEquals(1, appliedVersion());
    }

    @Test
    void settlementRejectOfAFill_resolvesTheLegToo() throws Exception {
        account("10000.00");
        RebalanceRunResponse started = runService.start(ACCOUNT);
        // Someone spends the cash first, so the fill can't be settled and is rejected there
        Account account = accountRepository.findById(ACCOUNT).orElseThrow();
        account.debitCash(new BigDecimal("9990.00"));
        accountRepository.save(account);

        fillAll(started, "100.00");

        RebalanceRunResponse done = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.PARTIAL, done.getStatus());
        assertTrue(done.getLegs().stream().allMatch(l -> l.getStatus() == OrderStatus.REJECTED));
    }

    @Test
    void theTargetVersionIsFixedWhenTheRunStarts() throws Exception {
        account("5000.00");
        hold("GOOG", 50);
        RebalanceRunResponse started = runService.start(ACCOUNT);

        pieService.update(pieId, OWNER, pieRequest("10", "10"));  // version 2 while the run is selling
        fill(started.getLegs().get(0).getOrderId(), "110.00");

        RebalanceRunResponse buying = runService.get(started.getRunId());
        assertEquals(1, buying.getTargetVersion());
        assertEquals(List.of("SELL GOOG 50", "BUY AAPL 52", "BUY MSFT 42"), summary(buying));
        fillAll(buying, "100.00");
        assertEquals(1, appliedVersion());
    }

    // ---------- cancelling ----------

    @Test
    void cancelWhileSelling_cancelsTheSell_placesNoBuys_andIgnoresALateFill() throws Exception {
        account("5000.00");
        hold("GOOG", 50);
        RebalanceRunResponse started = runService.start(ACCOUNT);
        UUID sell = started.getLegs().get(0).getOrderId();

        RebalanceRunResponse cancelled = runService.cancel(started.getRunId());

        assertEquals(RebalanceStatus.CANCELLED, cancelled.getStatus());
        assertEquals(OrderStatus.CANCELLED, cancelled.getLegs().get(0).getStatus());
        assertEquals(1, orderRepository.count());
        assertNull(appliedVersion());

        fill(sell, "100.00");  // the engine had already filled it

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(sell).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("5000.00").compareTo(accountRepository.findById(ACCOUNT).orElseThrow().getCashBalance()));
        UUID runId = started.getRunId();
        assertThrows(RebalanceConflictException.class, () -> runService.cancel(runId));
    }

    @Test
    void cancelWhileBuying_keepsFilledBuysAndCancelsTheRest() throws Exception {
        account("10000.00");
        RebalanceRunResponse started = runService.start(ACCOUNT);
        fill(orderOf(started, "AAPL"), "100.00");

        RebalanceRunResponse cancelled = runService.cancel(started.getRunId());

        assertEquals(RebalanceStatus.CANCELLED, cancelled.getStatus());
        assertEquals(OrderStatus.FILLED, statusOf(cancelled, "AAPL"));
        assertEquals(OrderStatus.CANCELLED, statusOf(cancelled, "MSFT"));
        assertNull(appliedVersion());
        assertEquals(RebalanceStatus.CANCELLED, runService.get(started.getRunId()).getStatus());
    }

    @Test
    void cancellingALegOrderByHand_resolvesTheLeg() {
        account("5000.00");
        hold("GOOG", 50);
        RebalanceRunResponse started = runService.start(ACCOUNT);

        orderService.cancelOrder(started.getLegs().get(0).getOrderId());

        RebalanceRunResponse buying = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.BUYING, buying.getStatus());
        assertEquals(OrderStatus.CANCELLED, buying.getLegs().get(0).getStatus());
    }

    // ---------- events ----------

    @Test
    void duplicateAndOutOfOrderEvents_placeTheBuysOnce() throws Exception {
        account("5000.00");
        hold("GOOG", 30);
        hold("TSLA", 20);
        RebalanceRunResponse started = runService.start(ACCOUNT);
        UUID goog = orderOf(started, "GOOG");
        UUID tsla = orderOf(started, "TSLA");

        fill(tsla, "100.00");       // the later leg first
        fill(tsla, "100.00");       // delivered again
        assertEquals(RebalanceStatus.SELLING, runService.get(started.getRunId()).getStatus());
        fill(goog, "100.00");
        fill(goog, "100.00");
        runService.advance(started.getRunId());
        coordinator.recover();
        coordinator.onOrderResolved(new OrderResolved(goog, OrderStatus.FILLED));

        RebalanceRunResponse buying = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.BUYING, buying.getStatus());
        assertEquals(List.of("SELL GOOG 30", "SELL TSLA 20", "BUY AAPL 50", "BUY MSFT 40"), summary(buying));
        assertEquals(4, orderRepository.count());
        assertEquals(0, new BigDecimal("10000.00").compareTo(accountRepository.findById(ACCOUNT).orElseThrow().getCashBalance()));
    }

    @Test
    void eventForAnOrderOutsideAnyRun_isIgnored() {
        account("1000.00");
        UUID manual = orderService.placeOrder(new PlaceOrderRequest(ACCOUNT, "AAPL", "BUY", "MARKET", 1, null, "manual-1"))
            .getOrderId();

        orderService.cancelOrder(manual);

        assertEquals(0, runRepository.count());
    }

    @Test
    void restart_advancesRunsWhoseEventsWereMissed() {
        account("5000.00");
        hold("GOOG", 50);
        RebalanceRunResponse started = runService.start(ACCOUNT);
        // Resolved with no event, as if the app was down when it happened
        resolveQuietly(started.getLegs().get(0).getOrderId(), OrderStatus.CANCELLED);
        assertEquals(RebalanceStatus.SELLING, runService.get(started.getRunId()).getStatus());

        coordinator.recover();

        RebalanceRunResponse buying = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.BUYING, buying.getStatus());

        for (RebalanceLegResponse leg : buying.getLegs()) {
            if (leg.getStatus() == OrderStatus.PENDING) {
                resolveQuietly(leg.getOrderId(), OrderStatus.FILLED);
            }
        }
        coordinator.recover();

        RebalanceRunResponse done = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.PARTIAL, done.getStatus());  // the sell was cancelled
        assertEquals(1, appliedVersion());
    }

    @Test
    void restart_leavesRunsStillWaitingAlone() {
        account("5000.00");
        hold("GOOG", 50);
        RebalanceRunResponse started = runService.start(ACCOUNT);

        coordinator.recover();

        assertEquals(RebalanceStatus.SELLING, runService.get(started.getRunId()).getStatus());
        assertEquals(1, orderRepository.count());
    }

    @Test
    void missingPriceAfterSells_endsPartialInsteadOfGettingStuck() throws Exception {
        account("5000.00");
        hold("GOOG", 50);
        RebalanceRunResponse started = runService.start(ACCOUNT);
        priceRepository.deleteAll();

        fill(started.getLegs().get(0).getOrderId(), "100.00");

        RebalanceRunResponse done = runService.get(started.getRunId());
        assertEquals(RebalanceStatus.PARTIAL, done.getStatus());
        assertTrue(done.getFailureSummary().contains("Buys could not be planned"));
        assertEquals(1, orderRepository.count());
        assertEquals(1, appliedVersion());
        assertEquals(0, new BigDecimal("10000.00").compareTo(accountRepository.findById(ACCOUNT).orElseThrow().getCashBalance()));
    }

    // ---------- starting ----------

    @Test
    void secondRunWhileOneIsActive_conflicts() {
        account("10000.00");
        runService.start(ACCOUNT);

        assertThrows(RebalanceConflictException.class, () -> runService.start(ACCOUNT));
    }

    @Test
    void concurrentStarts_startOneRun() throws Exception {
        account("10000.00");
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    try {
                        runService.start(ACCOUNT);
                        return true;
                    } catch (RebalanceConflictException e) {
                        return false;
                    }
                }));
            }
            go.countDown();
            int started = 0;
            for (Future<Boolean> result : results) {
                started += result.get() ? 1 : 0;
            }
            assertEquals(1, started);
        } finally {
            pool.shutdown();
        }
        assertEquals(1, runRepository.count());
        assertEquals(2, orderRepository.count());
    }

    @Test
    void pendingOrdersOnTheAccount_conflict() {
        account("10000.00");
        orderService.placeOrder(new PlaceOrderRequest(ACCOUNT, "AAPL", "BUY", "MARKET", 1, null, "manual-1"));

        assertThrows(RebalanceConflictException.class, () -> runService.start(ACCOUNT));
        assertEquals(0, runRepository.count());
    }

    @Test
    void accountWithoutAPie_isNotLinked() {
        account("10000.00");
        accountPieRepository.deleteAll();

        assertThrows(PieNotLinkedException.class, () -> runService.start(ACCOUNT));
    }

    @Test
    void alreadyOnTarget_completesAtOnce() {
        account("10000.00");
        hold("AAPL", 50);
        hold("MSFT", 40);
        Account account = accountRepository.findById(ACCOUNT).orElseThrow();
        account.debitCash(new BigDecimal("9000.00"));
        accountRepository.save(account);

        RebalanceRunResponse started = runService.start(ACCOUNT);

        assertEquals(RebalanceStatus.COMPLETED, started.getStatus());
        assertTrue(started.getLegs().isEmpty());
        assertEquals(1, appliedVersion());
        runService.start(ACCOUNT);  // not active any more, so another run may start
    }

    @Test
    void waitingForMarketOpen_isShownOnlyWhileOrdersWaitAndTheMarketIsClosed() {
        account("5000.00");
        hold("GOOG", 50);
        RebalanceRunResponse started = runService.start(ACCOUNT);

        assertFalse(runService.get(started.getRunId()).isWaitingForMarketOpen());
        when(clock.instant()).thenReturn(MARKET_CLOSED);
        assertTrue(runService.get(started.getRunId()).isWaitingForMarketOpen());

        runService.cancel(started.getRunId());
        assertFalse(runService.get(started.getRunId()).isWaitingForMarketOpen());
    }

    // ---------- helpers ----------

    private void account(String cash) {
        accountRepository.save(new Account(ACCOUNT, "Rebalance Tester", new BigDecimal(cash),
            AccountStatus.ACTIVE, LocalDateTime.now()));
        accountPieRepository.save(AccountPie.own(ACCOUNT, pieId));
    }

    private void hold(String symbol, int quantity) {
        positionRepository.save(new Position(accountRepository.findById(ACCOUNT).orElseThrow(),
            instrumentRepository.findBySymbol(symbol).orElseThrow(), quantity, new BigDecimal("90.00")));
    }

    private static SavePieRequest pieRequest(String aapl, String msft) {
        return new SavePieRequest("Tech", null, List.of(
            new PieSliceRequest("AAPL", new BigDecimal(aapl)), new PieSliceRequest("MSFT", new BigDecimal(msft))));
    }

    private Integer appliedVersion() {
        return accountPieRepository.findById(ACCOUNT).orElseThrow().getAppliedVersion();
    }

    private static List<String> summary(RebalanceRunResponse run) {
        return run.getLegs().stream().map(l -> l.getSide() + " " + l.getSymbol() + " " + l.getQuantity()).toList();
    }

    private static UUID orderOf(RebalanceRunResponse run, String symbol) {
        return run.getLegs().stream().filter(l -> l.getSymbol().equals(symbol)).findFirst().orElseThrow().getOrderId();
    }

    private static OrderStatus statusOf(RebalanceRunResponse run, String symbol) {
        return run.getLegs().stream().filter(l -> l.getSymbol().equals(symbol)).findFirst().orElseThrow().getStatus();
    }

    /** Moves an order out of PENDING without the event settlement would publish. */
    private void resolveQuietly(UUID orderId, OrderStatus status) {
        new TransactionTemplate(transactionManager)
            .executeWithoutResult(tx -> orderRepository.changeStatus(orderId, OrderStatus.PENDING, status));
    }

    private void fillAll(RebalanceRunResponse run, String price) throws Exception {
        for (RebalanceLegResponse leg : run.getLegs()) {
            if (leg.getStatus() == OrderStatus.PENDING) {
                fill(leg.getOrderId(), price);
            }
        }
    }

    /** Delivers ORDER_EXECUTED to settlement, as the Kafka listener would. */
    private void fill(UUID orderId, String price) throws Exception {
        Order order = orderRepository.findById(orderId).orElseThrow();
        BigDecimal executionPrice = new BigDecimal(price);
        OrderExecutedEvent event = new OrderExecutedEvent(orderId, ACCOUNT, order.getSymbol(), order.getSide().name(),
            order.getQuantity(), executionPrice, executionPrice.multiply(BigDecimal.valueOf(order.getQuantity())));
        deliver("ORDER_EXECUTED", event);
    }

    private void reject(UUID orderId, String reason) throws Exception {
        Order order = orderRepository.findById(orderId).orElseThrow();
        deliver("ORDER_REJECTED", new OrderRejectedEvent(orderId, ACCOUNT, order.getSymbol(), reason));
    }

    private void deliver(String type, Object payload) throws Exception {
        EventEnvelope<Object> envelope = new EventEnvelope<>(UUID.randomUUID().toString(), type, Instant.now(), "test", 1, payload);
        settlement.processKafkaMessage(mapper.writeValueAsString(envelope));
    }
}
