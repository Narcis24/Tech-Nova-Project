package com.neueda.trading.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/** Latest quote per symbol, kept up to date from the market-data topic. */
@Component
@Slf4j
public class QuoteCache {

    private final Map<String, Quote> latest = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public QuoteCache(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Own group: every executor instance needs every quote, not a share of them
    @KafkaListener(topics = MarketDataPoller.TOPIC, groupId = "execution-engine-quotes")
    public void onMessage(String message) throws Exception {
        JsonNode payload = objectMapper.readTree(message).path("payload");
        put(objectMapper.treeToValue(payload, Quote.class));
    }

    void put(Quote quote) {
        latest.put(quote.symbol(), quote);
    }

    /** The quote for the symbol, unless there is none or it is older than maxAge. */
    Optional<Quote> fresh(String symbol, Instant now, Duration maxAge) {
        return Optional.ofNullable(latest.get(symbol))
            .filter(q -> Duration.between(q.asOf(), now).compareTo(maxAge) <= 0);
    }
}
