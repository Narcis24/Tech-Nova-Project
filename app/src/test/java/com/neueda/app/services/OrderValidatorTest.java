package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderType;
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
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.services.OrderValidator.ValidatedOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderValidatorTest {

    private static final String ACCOUNT = "ACC001";

    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private InstrumentRepository instrumentRepository;
    private PriceService priceService;
    private OrderValidator orderValidator;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        priceService = mock(PriceService.class);
        orderValidator = new OrderValidator(accountRepository, positionRepository, instrumentRepository, priceService);

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
        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
            () -> orderValidator.validate(order("BUY", "MARKET", 11, null)));

        assertEquals("Account ACC001 has $1000.00 but order requires $1100.00", ex.getMessage());
    }

    @Test
    void buyFromEmptyAccountIsRefused() {
        givenAccount("0.00", AccountStatus.ACTIVE);

        assertThrows(InsufficientFundsException.class,
            () -> orderValidator.validate(order("BUY", "MARKET", 1, null)));
    }

    @Test
    void sellOfMoreThanHeldIsRefused() {
        givenShares(3);

        InsufficientHoldingsException ex = assertThrows(InsufficientHoldingsException.class,
            () -> orderValidator.validate(order("SELL", "MARKET", 5, null)));

        assertEquals("Cannot sell 5 shares of AAPL. Only 3 held.", ex.getMessage());
    }

    @Test
    void unknownAccountIsRefused() {
        when(accountRepository.findById(ACCOUNT)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
            () -> orderValidator.validate(order("BUY", "MARKET", 1, null)));
    }

    @Test
    void inactiveAccountIsRefused() {
        givenAccount("1000.00", AccountStatus.INACTIVE);

        assertThrows(AccountNotActiveException.class,
            () -> orderValidator.validate(order("BUY", "MARKET", 1, null)));
    }

    @Test
    void untradableInstrumentIsRefused() {
        when(instrumentRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", false)));

        assertThrows(TradingException.class,
            () -> orderValidator.validate(order("BUY", "MARKET", 1, null)));
    }

    @Test
    void limitWithoutPriceIsRefusedBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class,
            () -> orderValidator.validate(order("BUY", "LIMIT", 1, null)));
        verifyNoInteractions(accountRepository, instrumentRepository, priceService);
    }

    @Test
    void marketWithPriceIsRefusedBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class,
            () -> orderValidator.checkInputs(order("BUY", "MARKET", 1, "100.00")));
        verifyNoInteractions(accountRepository, instrumentRepository, priceService);
    }

    // ========== Helpers ==========

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
