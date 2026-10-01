package com.neueda.app.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.app.dtos.OrderExecutedEvent;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.events.EventEnvelope;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Processes ORDER_EXECUTED events from the execution-engine via Kafka.
 * Handles deserialization of Kafka messages and updates order status, 
 * positions, and cash balance based on filled trades.
 * 
 * Handles both BUY and SELL flows:
 * - BUY: debits cash, updates/creates position
 * - SELL: credits cash, updates position
 */
@Service
@Transactional
@Slf4j
public class OrderSettlementService {

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final ObjectMapper objectMapper;

    public OrderSettlementService(OrderRepository orderRepository,
                                  AccountRepository accountRepository,
                                  PositionRepository positionRepository,
                                  InstrumentRepository instrumentRepository,
                                  ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a Kafka message containing an ORDER_EXECUTED event.
     * Deserializes the message and updates the order status, positions, and cash balance.
     *
     * @param message the JSON Kafka message
     */
    public void processKafkaMessage(String message) {
        log.info("Processing order execution message from Kafka");

        EventEnvelope<?> envelope;
        try {
            envelope = objectMapper.readValue(
                message,
                EventEnvelope.class
            );
        } catch (Exception e) {
            log.error("Failed to deserialize EventEnvelope: message={}", message, e);
            return;
        }

        if (!"ORDER_EXECUTED".equals(envelope.eventType())) {
            log.debug("Ignoring event type: {}", envelope.eventType());
            return;
        }

        OrderExecutedEvent event;
        try {
            event = objectMapper.convertValue(
                envelope.payload(),
                OrderExecutedEvent.class
            );
        } catch (Exception e) {
            log.error("Failed to deserialize ORDER_EXECUTED payload: payload={}", envelope.payload(), e);
            return;
        }

        processOrderExecution(event);
    }

    /**
     * Processes an ORDER_EXECUTED event.
     * Updates the order status to FILLED and applies the trade to the account's
     * positions and cash balance.
     *
     * @param event the order executed event
     * @throws OrderNotFoundException if the order doesn't exist
     * @throws AccountNotFoundException if the account doesn't exist
     * @throws InstrumentNotFoundException if the instrument doesn't exist
     * @throws InsufficientHoldingsException if trying to sell without holdings
     */
    private void processOrderExecution(OrderExecutedEvent event) {
        UUID orderId = event.getOrderId();

        log.info("Received ORDER_EXECUTED event: orderId={}", orderId);

        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(
                            "Order not found: " + orderId
                    ));

            order.execute();
            order.setExecutionPrice(event.getExecutionPrice());

            Account account = accountRepository.findById(event.getAccountId())
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Account not found: " + event.getAccountId()
                    ));

            Instrument instrument = instrumentRepository.findBySymbol(event.getSymbol())
                    .orElseThrow(() -> new InstrumentNotFoundException(
                            "Instrument not found: " + event.getSymbol()
                    ));

            if ("BUY".equalsIgnoreCase(event.getSide())) {
                handleBuy(account, instrument, event);
                log.info(
                    "BUY execution processed: orderId={}, symbol={}, quantity={}, executionPrice={}, totalValue={}",
                    orderId,
                    event.getSymbol(),
                    event.getQuantity(),
                    event.getExecutionPrice(),
                    event.getTotalValue()
                );
            } else if ("SELL".equalsIgnoreCase(event.getSide())) {
                handleSell(account, instrument, event);
                log.info(
                    "SELL execution processed: orderId={}, symbol={}, quantity={}, executionPrice={}, totalValue={}",
                    orderId,
                    event.getSymbol(),
                    event.getQuantity(),
                    event.getExecutionPrice(),
                    event.getTotalValue()
                );
            } else {
                throw new IllegalArgumentException(
                    "Unknown order side: " + event.getSide()
                );
            }

            orderRepository.save(order);

            log.info(
                "Order execution processed successfully: orderId={}, status=FILLED, executionPrice={}",
                orderId,
                event.getExecutionPrice()
            );

        } catch (Exception e) {
            log.error("Error processing ORDER_EXECUTED event: orderId={}", orderId, e);
            throw new RuntimeException("Failed to process order execution: " + orderId, e);
        }
    }

    /**
     * Handles BUY execution: debits cash and updates position.
     */
    private void handleBuy(Account account, Instrument instrument, OrderExecutedEvent event) {
        account.debitCash(event.getTotalValue());

        Position position = positionRepository
                .findByAccountIdAndSymbol(account.getAccountId(), instrument.getSymbol())
                .orElse(new Position(account, instrument, 0, BigDecimal.ZERO));

        position.updateOnBuy(event.getQuantity(), event.getExecutionPrice());

        accountRepository.save(account);
        positionRepository.save(position);
    }

    /**
     * Handles SELL execution: credits cash and updates position.
     */
    private void handleSell(Account account, Instrument instrument, OrderExecutedEvent event) {
        Position position = positionRepository
                .findByAccountIdAndSymbol(account.getAccountId(), instrument.getSymbol())
                .orElseThrow(() -> new InsufficientHoldingsException(
                        "No position in " + event.getSymbol() + " for account " + account.getAccountId()
                ));

        position.updateOnSell(event.getQuantity());
        account.creditCash(event.getTotalValue());

        positionRepository.save(position);
        accountRepository.save(account);
    }
}
