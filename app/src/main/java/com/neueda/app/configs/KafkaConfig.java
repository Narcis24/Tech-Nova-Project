package com.neueda.app.configs;

import com.neueda.app.events.EventEnvelope;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    private static final String DLT_SUFFIX = "-dlt";


    @Bean
    public ProducerFactory<String, EventEnvelope<?>> producerFactory(
            KafkaProperties kafkaProperties) {

        Map<String, Object> props = new HashMap<>();

        props.put(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaProperties.getBootstrapServers()
        );

        props.put(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class
        );

        props.put(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            JacksonJsonSerializer.class
        );

        props.put(
            JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS,
            false
        );

        return new DefaultKafkaProducerFactory<>(props);
    }


    @Bean
    public KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate(
            ProducerFactory<String, EventEnvelope<?>> producerFactory) {

        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Error handler for all @KafkaListener methods: retries a failed record 3 times, 1 second
     * apart, then publishes it unchanged to "<topic>-dlt" (same partition) instead of skipping it.
     * Uses its own String producer so the original payload is not re-serialized as JSON.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaProperties kafkaProperties) {

        Map<String, Object> props = new HashMap<>();

        props.put(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaProperties.getBootstrapServers()
        );

        props.put(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class
        );

        props.put(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class
        );

        KafkaTemplate<String, String> dltTemplate =
            new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            dltTemplate,
            (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition())
        );

        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }

    @Bean
    public NewTopic orderRequestTopic() {

        return TopicBuilder
            .name("order-request")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic orderExecutionTopic() {

        return TopicBuilder
            .name("order-execution")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic orderExecutionDltTopic() {

        return TopicBuilder
            .name("order-execution" + DLT_SUFFIX)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic marketDataTopic() {

        return TopicBuilder
            .name("market-data")
            .partitions(3)
            .replicas(1)
            .build();
    }
}