package com.neueda.app.services;

import tools.jackson.databind.ObjectMapper;
import com.neueda.events.OrderExecutedEvent;
import com.neueda.events.OrderRejectedEvent;
import com.neueda.app.enums.OrderStatus;
import com.neueda.events.EventEnvelope;
import com.neueda.events.EventTypes;
import com.neueda.events.Topics;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.exceptions.InsufficientHoldingsException;
import com.neueda.app.exceptions.OrderNotFoundException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Position;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Processes ORDER_EXECUTED and ORDER_REJECTED events from the execution-engine via Kafka.
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
    @KafkaListener(
        topics = Topics.ORDER_EXECUTION,
        groupId = "order-settlement"
    )
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

        if (EventTypes.ORDER_REJECTED.equals(envelope.eventType())) {
            processOrderRejection(envelope);
            return;
        }

        if (!EventTypes.ORDER_EXECUTED.equals(envelope.eventType())) {
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
     * Resolves a PENDING order the engine could not price: PENDING -> REJECTED with the reason.
     * An order that is no longer PENDING is left alone.
     */
    private void processOrderRejection(EventEnvelope<?> envelope) {
        OrderRejectedEvent event;
        try {
            event = objectMapper.convertValue(envelope.payload(), OrderRejectedEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize ORDER_REJECTED payload: payload={}", envelope.payload(), e);
            return;
        }

        int updated = orderRepository.reject(event.orderId(), OrderStatus.PENDING,
                OrderStatus.REJECTED, event.reason());
        if (updated == 0) {
            log.warn("Ignoring ORDER_REJECTED, order is not PENDING or unknown: orderId={}", event.orderId());
            return;
        }
        log.info("Order rejected: orderId={}, reason={}", event.orderId(), event.reason());
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
        UUID orderId = event.orderId();

        log.info("Received ORDER_EXECUTED event: orderId={}", orderId);

        try {
            // Guard: only one delivery can move the order out of PENDING. It is the first
            // write, so a duplicate stops here before touching cash or positions.
            int updated = orderRepository.transition(
                    orderId, OrderStatus.PENDING, OrderStatus.FILLED,
                    event.executionPrice().setScale(2, RoundingMode.HALF_UP));
            if (updated == 0) {
                if (!orderRepository.existsById(orderId)) {
                    throw new OrderNotFoundException("Order not found: " + orderId);
                }
                log.warn("Ignoring ORDER_EXECUTED, order is not PENDING (duplicate or already closed): orderId={}", orderId);
                return;
            }

            Account account = accountRepository.findByIdForUpdate(event.accountId())
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Account not found: " + event.accountId()
                    ));

            Instrument instrument = instrumentRepository.findBySymbol(event.symbol())
                    .orElseThrow(() -> new InstrumentNotFoundException(
                            "Instrument not found: " + event.symbol()
                    ));

            if ("BUY".equalsIgnoreCase(event.side())) {
                handleBuy(account, instrument, event);
                log.info(
                    "BUY execution processed: orderId={}, symbol={}, quantity={}, executionPrice={}, totalValue={}",
                    orderId,
                    event.symbol(),
                    event.quantity(),
                    event.executionPrice(),
                    event.totalValue()
                );
            } else if ("SELL".equalsIgnoreCase(event.side())) {
                handleSell(account, instrument, event);
                log.info(
                    "SELL execution processed: orderId={}, symbol={}, quantity={}, executionPrice={}, totalValue={}",
                    orderId,
                    event.symbol(),
                    event.quantity(),
                    event.executionPrice(),
                    event.totalValue()
                );
            } else {
                throw new IllegalArgumentException(
                    "Unknown order side: " + event.side()
                );
            }

            log.info(
                "Order execution processed successfully: orderId={}, status=FILLED, executionPrice={}",
                orderId,
                event.executionPrice()
            );

        } catch (InsufficientFundsException | InsufficientHoldingsException e) {
            // The fill cannot be settled. Nothing else was written, so flip the guard's FILLED
            // back and resolve the order as REJECTED instead of leaving it for the dead-letter topic.
            log.warn("Rejecting unsettleable fill: orderId={}, reason={}", orderId, e.getMessage());
            orderRepository.reject(orderId, OrderStatus.FILLED, OrderStatus.REJECTED, e.getMessage());
        } catch (Exception e) {
            log.error("Error processing ORDER_EXECUTED event: orderId={}", orderId, e);
            throw new RuntimeException("Failed to process order execution: " + orderId, e);
        }
    }

    /**
     * Handles BUY execution: debits cash and updates position.
     */
    private void handleBuy(Account account, Instrument instrument, OrderExecutedEvent event) {
        account.debitCash(event.totalValue());

        Position position = positionRepository
                .findByAccountIdAndSymbol(account.getAccountId(), instrument.getSymbol())
                .orElse(new Position(account, instrument, 0, BigDecimal.ZERO));

        position.updateOnBuy(event.quantity(), event.executionPrice());

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
                        "No position in " + event.symbol() + " for account " + account.getAccountId()
                ));

        position.updateOnSell(event.quantity());
        account.creditCash(event.totalValue());

        positionRepository.save(position);
        accountRepository.save(account);
    }
}
