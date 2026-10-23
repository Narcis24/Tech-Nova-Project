package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderType;
import com.neueda.app.enums.TimeInForce;
import com.neueda.app.exceptions.AccountNotActiveException;
import com.neueda.app.exceptions.AccountNotFoundException;
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
import com.neueda.app.services.OrderValidator.ValidatedOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderValidatorTest {

    private static final String ACCOUNT = "ACC001";
    /** Wednesday 2026-10-21, 10:00 in New York: the market is open. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T14:00:00Z"), ZoneOffset.UTC);

    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private InstrumentRepository instrumentRepository;
    private PriceService priceService;
    private OrderRepository orderRepository;
    private OrderValidator orderValidator;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        priceService = mock(PriceService.class);
        orderRepository = mock(OrderRepository.class);
        when(orderRepository.lockedCash(ACCOUNT)).thenReturn(BigDecimal.ZERO);
        orderValidator = new OrderValidator(accountRepository, positionRepository, instrumentRepository, priceService,
            orderRepository, CLOCK);

        givenAccount("1000.00", AccountStatus.ACTIVE);
        when(instrumentRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true)));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100.00"));
    }

    // ========== Passing orders ==========

    @Test
    void marketBuyUsesLatestPrice() {
        ValidatedOrder valid = orderValidator.validate(order("BUY", "MARKET", 5, null));

        assertEquals(OrderSide.BUY, valid.side());
        assertEquals(OrderType.MARKET, valid.orderType());
        assertEquals(new BigDecimal("100.00"), valid.price());
        assertEquals(ACCOUNT, valid.account().getAccountId());
        assertEquals("AAPL", valid.instrument().getSymbol());
    }

    @Test
    void limitBuyUsesLimitPrice() {
        ValidatedOrder valid = orderValidator.validate(order("BUY", "LIMIT", 5, "90.00"));

        assertEquals(new BigDecimal("90.00"), valid.price());
        verifyNoInteractions(priceService);
    }

    @Test
    void buyCostingExactlyTheCashPasses() {
        // 10 x 100.00 = 1,000.00
        assertDoesNotThrow(() -> orderValidator.validate(order("BUY", "MARKET", 10, null)));
    }

    @Test
    void sellOfSharesHeldPasses() {
        givenShares(5);

        ValidatedOrder valid = orderValidator.validate(order("SELL", "MARKET", 5, null));

        assertEquals(OrderSide.SELL, valid.side());
    }

    // ========== Refused orders ==========

    @Test
    void buyCostingMoreThanCashIsRefused() {
        // 11 x 100.00 = 1,100.00
        PlaceOrderRequest request = order("BUY", "MARKET", 11, null);
        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
            () -> orderValidator.validate(request));

        assertEquals("Account ACC001 has $1000.00 but order requires $1100.00", ex.getMessage());
    }

    @Test
    void buyFromEmptyAccountIsRefused() {
        givenAccount("0.00", AccountStatus.ACTIVE);

        PlaceOrderRequest request = order("BUY", "MARKET", 1, null);
        assertThrows(InsufficientFundsException.class,
            () -> orderValidator.validate(request));
    }

    @Test
    void sellOfMoreThanHeldIsRefused() {
        givenShares(3);

        PlaceOrderRequest request = order("SELL", "MARKET", 5, null);
        InsufficientHoldingsException ex = assertThrows(InsufficientHoldingsException.class,
            () -> orderValidator.validate(request));

        assertEquals("Cannot sell 5 shares of AAPL. Only 3 held.", ex.getMessage());
    }

    @Test
    void unknownAccountIsRefused() {
        when(accountRepository.findById(ACCOUNT)).thenReturn(Optional.empty());

        PlaceOrderRequest request = order("BUY", "MARKET", 1, null);
        assertThrows(AccountNotFoundException.class,
            () -> orderValidator.validate(request));
    }

    @Test
    void inactiveAccountIsRefused() {
        givenAccount("1000.00", AccountStatus.INACTIVE);

        PlaceOrderRequest request = order("BUY", "MARKET", 1, null);
        assertThrows(AccountNotActiveException.class,
            () -> orderValidator.validate(request));
    }

    @Test
    void untradableInstrumentIsRefused() {
        when(instrumentRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", false)));

        PlaceOrderRequest request = order("BUY", "MARKET", 1, null);
        assertThrows(TradingException.class,
            () -> orderValidator.validate(request));
    }

    @Test
    void limitWithoutPriceIsRefusedBeforeAnyLookup() {
        PlaceOrderRequest request = order("BUY", "LIMIT", 1, null);
        assertThrows(IllegalArgumentException.class,
            () -> orderValidator.validate(request));
        verifyNoInteractions(accountRepository, instrumentRepository, priceService);
    }

    @Test
    void marketWithPriceIsRefusedBeforeAnyLookup() {
        PlaceOrderRequest request = order("BUY", "MARKET", 1, "100.00");
        assertThrows(IllegalArgumentException.class,
            () -> orderValidator.checkInputs(request));
        verifyNoInteractions(accountRepository, instrumentRepository, priceService);
    }

    // ========== Cash and shares locked by pending orders ==========

    @Test
    void buyIsCheckedAgainstCashNotLockedByPendingBuys() {
        when(orderRepository.lockedCash(ACCOUNT)).thenReturn(new BigDecimal("600.00"));

        // 5 x 100.00 = 500.00, but only 400.00 is free
        PlaceOrderRequest request = order("BUY", "MARKET", 5, null);
        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
            () -> orderValidator.validate(request));

        assertEquals("Account ACC001 has $400.00 available ($600.00 locked in pending orders)"
            + " but order requires $500.00", ex.getMessage());
    }

    @Test
    void buyCostingExactlyTheUnlockedCashPasses() {
        when(orderRepository.lockedCash(ACCOUNT)).thenReturn(new BigDecimal("600.00"));

        assertDoesNotThrow(() -> orderValidator.validate(order("BUY", "MARKET", 4, null)));
    }

    @Test
    void sellIsCheckedAgainstSharesNotLockedByPendingSells() {
        givenShares(5);
        when(orderRepository.lockedShares(ACCOUNT, "AAPL")).thenReturn(3L);

        PlaceOrderRequest request = order("SELL", "MARKET", 3, null);
        InsufficientHoldingsException ex = assertThrows(InsufficientHoldingsException.class,
            () -> orderValidator.validate(request));

        assertEquals("Cannot sell 3 shares of AAPL. Only 2 available (5 held, 3 in pending sell orders).",
            ex.getMessage());
        assertDoesNotThrow(() -> orderValidator.validate(order("SELL", "MARKET", 2, null)));
    }

    // ========== Time in force ==========

    @Test
    void limitOrderIsADayOrderByDefaultAndExpiresAtTodaysClose() {
        ValidatedOrder valid = orderValidator.validate(order("BUY", "LIMIT", 1, "90.00"));

        assertEquals(TimeInForce.DAY, valid.timeInForce());
        // 16:00 in New York on 2026-10-21 (EDT) is 20:00 UTC
        assertEquals(LocalDateTime.of(2026, 10, 21, 20, 0), valid.expiresAt());
    }

    @Test
    void goodTillCancelledExpiresAtTheClose90DaysOn() {
        ValidatedOrder valid = orderValidator.validate(limit("GTC", null));

        assertEquals(TimeInForce.GTC, valid.timeInForce());
        // 2027-01-19 is in winter time (EST), so 16:00 there is 21:00 UTC
        assertEquals(LocalDateTime.of(2027, 1, 19, 21, 0), valid.expiresAt());
    }

    @Test
    void goodTillDateExpiresAtTheCloseOnThatDate() {
        ValidatedOrder valid = orderValidator.validate(limit("GTD", LocalDate.of(2026, 10, 30)));

        assertEquals(TimeInForce.GTD, valid.timeInForce());
        assertEquals(LocalDateTime.of(2026, 10, 30, 20, 0), valid.expiresAt());
    }

    @Test
    void goodTillDateCanBeTodayBeforeTheCloseAndUpTo90DaysAhead() {
        assertDoesNotThrow(() -> orderValidator.validate(limit("GTD", LocalDate.of(2026, 10, 21))));
        assertDoesNotThrow(() -> orderValidator.validate(limit("GTD", LocalDate.of(2027, 1, 19))));
    }

    @Test
    void goodTillDateInThePastOrMoreThan90DaysAheadIsRefused() {
        PlaceOrderRequest yesterday = limit("GTD", LocalDate.of(2026, 10, 20));
        PlaceOrderRequest tooFar = limit("GTD", LocalDate.of(2027, 1, 20));

        assertThrows(IllegalArgumentException.class, () -> orderValidator.validate(yesterday));
        assertThrows(IllegalArgumentException.class, () -> orderValidator.validate(tooFar));
    }

    @Test
    void timeInForceMustMatchTheOrder() {
        PlaceOrderRequest marketWithTif = order("BUY", "MARKET", 1, null);
        marketWithTif.setTimeInForce("DAY");
        PlaceOrderRequest gtdWithoutDate = limit("GTD", null);
        PlaceOrderRequest dayWithDate = limit("DAY", LocalDate.of(2026, 10, 30));
        PlaceOrderRequest unknown = limit("IOC", null);

        for (PlaceOrderRequest request : List.of(marketWithTif, gtdWithoutDate, dayWithDate, unknown)) {
            assertThrows(IllegalArgumentException.class, () -> orderValidator.checkInputs(request));
        }
        verifyNoInteractions(accountRepository, instrumentRepository, priceService);
    }

    @Test
    void marketOrdersNeverExpire() {
        ValidatedOrder valid = orderValidator.validate(order("BUY", "MARKET", 1, null));

        assertNull(valid.timeInForce());
        assertNull(valid.expiresAt());
    }

    // ========== Helpers ==========

    private static PlaceOrderRequest limit(String timeInForce, LocalDate expiresOn) {
        PlaceOrderRequest request = order("BUY", "LIMIT", 1, "90.00");
        request.setTimeInForce(timeInForce);
        request.setExpiresOn(expiresOn);
        return request;
    }

    private void givenAccount(String cash, AccountStatus status) {
        when(accountRepository.findById(ACCOUNT)).thenReturn(Optional.of(
            new Account(ACCOUNT, "Alice Johnson", new BigDecimal(cash), status, LocalDateTime.now())));
    }

    private void givenShares(int quantity) {
        Position position = mock(Position.class);
        when(position.getQuantity()).thenReturn(quantity);
        when(positionRepository.findByAccountIdAndSymbol(ACCOUNT, "AAPL")).thenReturn(Optional.of(position));
    }

    private static PlaceOrderRequest order(String side, String type, int quantity, String price) {
        return new PlaceOrderRequest(ACCOUNT, "AAPL", side, type, quantity,
            price == null ? null : new BigDecimal(price), "key-1");
    }
}
