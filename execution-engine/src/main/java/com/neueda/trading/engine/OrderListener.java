package com.neueda.trading.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Step 2 of the event flow: reads each order from the {@code orders} topic,
 * works it on the {@link SimulatedMarket}, and writes the fill to the
 * {@code executions} topic keyed by account.
 *
 * <p>The fill is sent and acknowledged by Kafka before this method returns,
 * so the order's offset is only committed once its fill is safely on the
 * executions topic: at-least-once. If the engine dies mid-order it works the
 * order again on restart, and the trade API ignores the duplicate fill.
 */
@Component
public class OrderListener {

    private static final Logger log = LoggerFactory.getLogger(OrderListener.class);

    private final SimulatedMarket market;
    private final Pauser pauser;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String executionsTopic;

    public OrderListener(SimulatedMarket market, Pauser pauser, KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper, @Value("${engine.topics.executions}") String executionsTopic) {
        this.market = market;
        this.pauser = pauser;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.executionsTopic = executionsTopic;
    }

    @KafkaListener(topics = "${engine.topics.orders}", groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${engine.topics.partitions}")
    public void onOrder(String message) throws InterruptedException, ExecutionException, TimeoutException,
            JsonProcessingException {
        OrderEvent order;
        try {
            order = objectMapper.readValue(message, OrderEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Skipping unreadable order message: {}", message, e);
            return;
        }

        log.info("Working order {}: {} {} {} limit {}", order.orderId(), order.side(), order.quantity(),
                order.symbol(), order.price());
        pauser.pause(market.nextDelay());
        ExecutionEvent fill = market.execute(order);

        kafkaTemplate.send(executionsTopic, fill.accountId(), objectMapper.writeValueAsString(fill))
                .get(10, TimeUnit.SECONDS);
        log.info("Filled order {} at {} on {}", fill.orderId(), fill.price(), fill.venue());
    }
}
