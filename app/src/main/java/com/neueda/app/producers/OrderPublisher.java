package com.neueda.app.producers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.app.events.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes order events to the order-requests Kafka topic.
 * At-least-once semantics: ensures orders are safely persisted on Kafka
 * before returning to the OrderService.
 */
@Component
public class OrderPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String orderRequestsTopic;

    public OrderPublisher(KafkaTemplate<String, String> kafkaTemplate,
                          ObjectMapper objectMapper,
                          @Value("${kafka.topics.order-requests}") String orderRequestsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.orderRequestsTopic = orderRequestsTopic;
    }

    /**
     * Publishes an order to Kafka, blocking until it's acknowledged.
     * Uses the account ID as the partition key for ordering guarantees per account.
     */
    public void publishOrder(OrderEvent order) throws InterruptedException, ExecutionException, TimeoutException, JsonProcessingException {
        try {
            String message = objectMapper.writeValueAsString(order);
            kafkaTemplate.send(orderRequestsTopic, order.accountId(), message)
                    .get(10, TimeUnit.SECONDS);
            log.info("Published order {} for account {}", order.orderId(), order.accountId());
        } catch (Exception e) {
            log.error("Failed to publish order {}", order.orderId(), e);
            throw e;
        }
    }
}
