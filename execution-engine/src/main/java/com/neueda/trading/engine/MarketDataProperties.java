package com.neueda.trading.engine;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * market-data.* settings. Startup fails if the polling schedule would exceed the daily quota:
 * requests/day = ceil(symbols / batchSize) * ceil(86400 / intervalSeconds).
 */
@ConfigurationProperties("market-data")
public record MarketDataProperties(
        List<String> symbols,
        @DefaultValue("120") int intervalSeconds,
        @DefaultValue("25") int batchSize,
        @DefaultValue("2000") int dailyQuota,
        @DefaultValue("600") int maxQuoteAgeSeconds,
        @DefaultValue("https://data.alpaca.markets") String baseUrl,
        @DefaultValue("") String keyId,
        @DefaultValue("") String secretKey) {

    private static final int SECONDS_PER_DAY = 86_400;

    public MarketDataProperties {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("market-data.symbols must not be empty");
        }
        if (intervalSeconds <= 0 || batchSize <= 0) {
            throw new IllegalArgumentException("market-data interval and batch size must be positive");
        }
        int perDay = requestsPerDay(symbols.size(), batchSize, intervalSeconds);
        if (perDay > dailyQuota) {
            throw new IllegalArgumentException(
                "market-data polling needs " + perDay + " requests/day, quota is " + dailyQuota);
        }
    }

    static int requestsPerDay(int symbols, int batchSize, int intervalSeconds) {
        int batches = (symbols + batchSize - 1) / batchSize;
        int cycles = (SECONDS_PER_DAY + intervalSeconds - 1) / intervalSeconds;
        return batches * cycles;
    }
}
