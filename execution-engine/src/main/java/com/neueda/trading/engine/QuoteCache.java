package com.neueda.trading.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;



/** Latest quote per symbol, updated by ExecutionEngine from the market-data topic. */
@Component
public class QuoteCache {

    private final Map<String, Quote> latest = new ConcurrentHashMap<>();
    void put(Quote quote) {
        latest.put(quote.symbol(), quote);
    }

    /** The quote for the symbol, unless there is none or it is older than maxAge. */
    Optional<Quote> fresh(String symbol, Instant now, Duration maxAge) {
        return Optional.ofNullable(latest.get(symbol))
            .filter(q -> Duration.between(q.asOf(), now).compareTo(maxAge) <= 0);
    }
}
