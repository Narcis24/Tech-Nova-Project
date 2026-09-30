package com.neueda.app.services;

import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.events.ExecutionEvent;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.exceptions.InsufficientHoldingsException;
import com.neueda.app.exceptions.OrderNotFoundException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.models.Position;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Processes execution events from the execution-engine.
 * Updates order status, positions, and cash balance based on filled trades.
 * 
 * Handles both BUY and SELL flows:
 * - BUY: debits cash, updates/creates position
 * - SELL: credits cash, updates position
 */
@Service
@Transactional
public class OrderSettlementService {

    private static final Logger log = LoggerFactory.getLogger(OrderSettlementService.class);

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;

    public OrderSettlementService(OrderRepository orderRepository,
                                  AccountRepository accountRepository,
                                  PositionRepository positionRepository,
                                  InstrumentRepository instrumentRepository) {
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
    }

    /**
     * Processes an execution event from the execution-engine.
     * Updates the order status to FILLED and applies the trade to the account's
     * positions and cash balance.
     *
     * @param execution the execution event from the engine
     * @throws OrderNotFoundException if the order doesn't exist
     * @throws AccountNotFoundException if the account doesn't exist
     * @throws InstrumentNotFoundException if the instrument doesn't exist
     * @throws InsufficientHoldingsException if trying to sell without holdings
     */
    public void processExecution(ExecutionEvent execution) {
        // Fetch order and validate it exists
        Order order = orderRepository.findById(execution.orderId())
                .orElseThrow(() -> new OrderNotFoundException(
                        "Order not found: " + execution.orderId()
                ));

        // Validate order is in a state ready to be filled
        if (order.getStatus() != OrderStatus.PUBLISHED) {
            log.warn("Execution received for order {} in status {}. Skipping.",
                    execution.orderId(), order.getStatus());
            return;
        }

        // Fetch account and instrument
        Account account = accountRepository.findById(order.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + order.getAccountId()
                ));

        Instrument instrument = instrumentRepository.findBySymbol(order.getSymbol())
                .orElseThrow(() -> new InstrumentNotFoundException(
                        "Instrument not found: " + order.getSymbol()
                ));

        BigDecimal totalValue = execution.price().multiply(new BigDecimal(execution.quantity()));

        if (order.getSide() == OrderSide.BUY) {
            handleBuy(account, instrument, execution, totalValue);
        } else {
            handleSell(account, instrument, execution, totalValue);
        }

        // Mark order as filled
        order.setStatus(OrderStatus.FILLED);
        orderRepository.save(order);

        log.info("Settled order {} - {} {} @ {}", execution.orderId(), execution.side(),
                execution.quantity(), execution.price());
    }

    /**
     * Handles BUY execution: debits cash and updates position.
     */
    private void handleBuy(Account account, Instrument instrument, ExecutionEvent execution, BigDecimal totalValue) {
        // Debit cash from account
        account.debitCash(totalValue);

        // Update or create position
        Position position = positionRepository
                .findByAccountIdAndSymbol(account.getAccountId(), instrument.getSymbol())
                .orElse(new Position(account, instrument, 0, BigDecimal.ZERO));

        position.updateOnBuy(execution.quantity(), execution.price());

        accountRepository.save(account);
        positionRepository.save(position);

        log.debug("BUY: Account {} bought {} x {} at {}. New cash: {}",
                account.getAccountId(), execution.quantity(), execution.symbol(),
                execution.price(), account.getCashBalance());
    }

    /**
     * Handles SELL execution: credits cash and updates position.
     */
    private void handleSell(Account account, Instrument instrument, ExecutionEvent execution, BigDecimal totalValue) {
        // Fetch position - must exist for sell
        Position position = positionRepository
                .findByAccountIdAndSymbol(account.getAccountId(), instrument.getSymbol())
                .orElseThrow(() -> new InsufficientHoldingsException(
                        "No position in " + execution.symbol() + " for account " + account.getAccountId()
                ));

        position.updateOnSell(execution.quantity());
        account.creditCash(totalValue);

        positionRepository.save(position);
        accountRepository.save(account);

        log.debug("SELL: Account {} sold {} x {} at {}. New cash: {}",
                account.getAccountId(), execution.quantity(), execution.symbol(),
                execution.price(), account.getCashBalance());
    }
}
