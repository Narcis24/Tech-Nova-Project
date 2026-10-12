package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderType;
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
import com.neueda.app.repositories.PositionRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import org.springframework.stereotype.Service;

/**
 * The checks an order must pass before it is placed: well-formed, an active account, a tradable
 * instrument, a price, and the cash or shares to cover it. Shared by OrderService (placing) and
 * RiskService (checking first), so both refuse the same orders with the same errors.
 */
@Service
public class OrderValidator {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final PriceService priceService;

    public OrderValidator(AccountRepository accountRepository, PositionRepository positionRepository,
                          InstrumentRepository instrumentRepository, PriceService priceService) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
        this.priceService = priceService;
    }

    /** An order that passed every check, with what was looked up along the way. */
    public record ValidatedOrder(Account account, Instrument instrument, OrderSide side, OrderType orderType,
                                 BigDecimal price) {
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

        return new ValidatedOrder(account, instrument, side, orderType, price);
    }

    /** Parses the order type without leaking the enum's class name on bad input. */
    private OrderType parseOrderType(String value) {
        try {
            return OrderType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("orderType must be one of " + Arrays.toString(OrderType.values()));
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

    private void requireCash(Account account, BigDecimal totalValue) {
        if (account.getCashBalance().compareTo(totalValue) < 0) {
            throw new InsufficientFundsException("Account " + account.getAccountId() + " has $"
                + account.getCashBalance() + " but order requires $" + totalValue);
        }
    }

    private void requireShares(String accountId, String symbol, int quantity) {
        int held = positionRepository.findByAccountIdAndSymbol(accountId, symbol)
            .map(Position::getQuantity).orElse(0);
        if (held < quantity) {
            throw new InsufficientHoldingsException("Cannot sell " + quantity
                + " shares of " + symbol + ". Only " + held + " held.");
        }
    }
}
