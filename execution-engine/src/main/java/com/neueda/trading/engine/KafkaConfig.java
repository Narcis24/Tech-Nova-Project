package com.neueda.trading.engine;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final String DLT_SUFFIX = "-dlt";

    /**
     * For all @KafkaListener methods: retry a failed record 3 times, 1 second apart, then
     * publish it unchanged to "<topic>-dlt" (same partition) instead of dropping it.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            template,
            (failed, ex) -> new TopicPartition(failed.topic() + DLT_SUFFIX, failed.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }

    @Bean
    public NewTopic marketDataTopic() {
        return topic(MarketDataPoller.TOPIC);
    }

    @Bean
    public NewTopic marketDataDltTopic() {
        return topic(MarketDataPoller.TOPIC + DLT_SUFFIX);
    }

    @Bean
    public NewTopic orderRequestDltTopic() {
        return topic("order-request" + DLT_SUFFIX);
    }

    // 3 partitions to match the app's topics, so a dead letter keeps its partition
    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
