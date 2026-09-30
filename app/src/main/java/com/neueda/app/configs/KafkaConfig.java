package com.neueda.app.configs;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka configuration for the Tech-Nova app.
 * Defines topics and broker configuration.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic orderRequestsTopic(@Value("${kafka.topics.order-requests}") String name,
                                       @Value("${kafka.topics.partitions:3}") int partitions) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderExecutionsTopic(@Value("${kafka.topics.order-executions}") String name,
                                         @Value("${kafka.topics.partitions:3}") int partitions) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
