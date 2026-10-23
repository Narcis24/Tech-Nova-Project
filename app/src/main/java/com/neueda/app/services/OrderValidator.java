package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderType;
import com.neueda.app.enums.TimeInForce;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.exceptions.InsufficientHoldingsException;
import com.neueda.app.exceptions.TradingException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Position;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.utils.MarketHours;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.springframework.stereotype.Service;

/**
 * The checks an order must pass before it is placed: well-formed, an active account, a tradable
 * instrument, a price, the cash or shares to cover it that open orders have not already locked,
 * and for a LIMIT order when it expires. Shared by OrderService (placing) and
 * RiskService (checking first), so both refuse the same orders with the same errors.
 */
@Service
public class OrderValidator {

    /** The longest a LIMIT order stays open: GTC orders, and the furthest GTD date. */
    static final int MAX_DAYS_OPEN = 90;

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final PriceService priceService;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderValidator(AccountRepository accountRepository, PositionRepository positionRepository,
                          InstrumentRepository instrumentRepository, PriceService priceService,
                          OrderRepository orderRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
        this.priceService = priceService;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /**
     * An order that passed every check, with what was looked up along the way. A LIMIT order also
     * has its time in force and expiry, both null for a MARKET order.
     */
    public record ValidatedOrder(Account account, Instrument instrument, OrderSide side, OrderType orderType,
                                 BigDecimal price, TimeInForce timeInForce, LocalDateTime expiresAt) {
    }

    /** The rules that need no database lookup. */
    public void checkInputs(PlaceOrderRequest request) {
        if (request.getSide() == null || request.getOrderType() == null || request.getQuantity() == null) {
            throw new IllegalArgumentException("side, orderType and quantity are required");
        }
        OrderType orderType = parseOrderType(request.getOrderType());
        if (orderType == OrderType.LIMIT && request.getPrice() == null) {
            throw new IllegalArgumentException("price is required for LIMIT orders");
        }
        if (orderType == OrderType.MARKET && request.getPrice() != null) {
            throw new IllegalArgumentException("price must not be set for MARKET orders");
        }
        if (orderType == OrderType.MARKET && (request.getTimeInForce() != null || request.getExpiresOn() != null)) {
            throw new IllegalArgumentException("timeInForce and expiresOn are only for LIMIT orders");
        }
        TimeInForce timeInForce = parseTimeInForce(request.getTimeInForce());
        if (timeInForce == TimeInForce.GTD && request.getExpiresOn() == null) {
            throw new IllegalArgumentException("expiresOn is required for GTD orders");
        }
        if (timeInForce != TimeInForce.GTD && request.getExpiresOn() != null) {
            throw new IllegalArgumentException("expiresOn is only for GTD orders");
        }
    }

    /** Runs every check. Throws the first one that fails. */
    public ValidatedOrder validate(PlaceOrderRequest request) {
        checkInputs(request);
        OrderType orderType = parseOrderType(request.getOrderType());

        Account account = accountRepository.findById(request.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + request.getAccountId()
            ));
        account.validateStatus();

        Instrument instrument = instrumentRepository.findBySymbol(request.getSymbol())
            .orElseThrow(() -> new InstrumentNotFoundException(
                "Instrument not found: " + request.getSymbol()
            ));
        if (!instrument.isTradable()) {
            throw new TradingException("Instrument is not tradable: " + request.getSymbol());
        }

        // For LIMIT orders, use the price from the request
        // For MARKET orders, use the latest stored price as the reference price
        // (throws PriceNotFoundException if none). ExecutionEngine decides the actual fill price.
        BigDecimal price = orderType == OrderType.LIMIT
            ? request.getPrice()
            : priceService.getCurrentPrice(request.getSymbol());

        OrderSide side = parseOrderSide(request.getSide());

        // Fail fast on what we can already see. Settlement re-checks at the real fill price.
        if (side == OrderSide.BUY) {
            requireCash(account, price.multiply(new BigDecimal(request.getQuantity())));
        } else {
            requireShares(request.getAccountId(), request.getSymbol(), request.getQuantity());
        }

        if (orderType == OrderType.MARKET) {
            return new ValidatedOrder(account, instrument, side, orderType, price, null, null);
        }
        TimeInForce timeInForce = parseTimeInForce(request.getTimeInForce());
        return new ValidatedOrder(account, instrument, side, orderType, price, timeInForce,
            expiry(timeInForce, request.getExpiresOn()));
    }

    /**
     * When a LIMIT order expires: the next close for DAY, the close 90 days on for GTC, the close on
     * the chosen date for GTD. Dates are New York ones, as the market's.
     */
    private LocalDateTime expiry(TimeInForce timeInForce, LocalDate expiresOn) {
        LocalDate today = MarketHours.today(clock.instant());
        return switch (timeInForce) {
            case DAY -> MarketHours.nextClose(clock.instant());
            case GTC -> MarketHours.closeOn(today.plusDays(MAX_DAYS_OPEN));
            case GTD -> {
                LocalDateTime expiresAt = MarketHours.closeOn(expiresOn);
                if (!expiresAt.isAfter(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)))) {
                    throw new IllegalArgumentException("expiresOn must be a date whose market close is still ahead");
                }
                if (expiresOn.isAfter(today.plusDays(MAX_DAYS_OPEN))) {
                    throw new IllegalArgumentException("expiresOn can be at most " + MAX_DAYS_OPEN + " days ahead");
                }
                yield expiresAt;
            }
        };
    }

    /** Parses the order type without leaking the enum's class name on bad input. */
    private OrderType parseOrderType(String value) {
        try {
            return OrderType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("orderType must be one of " + Arrays.toString(OrderType.values()));
        }
    }

    /** Parses the time in force, DAY when not given, without leaking the enum's class name on bad input. */
    private TimeInForce parseTimeInForce(String value) {
        if (value == null) {
            return TimeInForce.DAY;
        }
        try {
            return TimeInForce.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("timeInForce must be one of " + Arrays.toString(TimeInForce.values()));
        }
    }

    /** Parses the order side without leaking the enum's class name on bad input. */
    private OrderSide parseOrderSide(String value) {
        try {
            return OrderSide.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("side must be one of " + Arrays.toString(OrderSide.values()));
        }
    }

    /** Cash the account's open buys have not already locked must cover the order. */
    private void requireCash(Account account, BigDecimal totalValue) {
        BigDecimal locked = orderRepository.lockedCash(account.getAccountId());
        BigDecimal available = account.getCashBalance().subtract(locked);
        if (available.compareTo(totalValue) < 0) {
            String lockedNote = locked.signum() > 0 ? " available ($" + locked + " locked in pending orders)" : "";
            throw new InsufficientFundsException("Account " + account.getAccountId() + " has $" + available
                + lockedNote + " but order requires $" + totalValue);
        }
    }

    /** Shares the account's open sells have not already locked must cover the order. */
    private void requireShares(String accountId, String symbol, int quantity) {
        int held = positionRepository.findByAccountIdAndSymbol(accountId, symbol)
            .map(Position::getQuantity).orElse(0);
        long locked = orderRepository.lockedShares(accountId, symbol);
        long available = held - locked;
        if (available < quantity) {
            String reason = locked > 0
                ? available + " available (" + held + " held, " + locked + " in pending sell orders)."
                : held + " held.";
            throw new InsufficientHoldingsException("Cannot sell " + quantity + " shares of " + symbol
                + ". Only " + reason);
        }
    }
}
