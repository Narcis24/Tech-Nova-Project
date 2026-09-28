package com.neueda.app.configs;

import com.neueda.app.events.EventEnvelope;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import java.util.HashMap;
import java.util.Map;

@Configuration 
@EnableKafka 
public class KafkaConfig {
    
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
            JsonSerializer.class
        );


        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate(ProducerFactory<String, EventEnvelope<?>> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}