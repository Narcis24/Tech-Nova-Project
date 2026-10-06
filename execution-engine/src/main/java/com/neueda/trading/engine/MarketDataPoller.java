package com.neueda.trading.engine;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** Fetches quotes in batches and publishes one message per symbol, keyed by symbol. */
@Component
@Slf4j
public class MarketDataPoller {

    static final String TOPIC = "market-data";

    private final QuoteClient client;
    private final EventProducerService producer;
    private final MarketDataProperties props;

    public MarketDataPoller(QuoteClient client, EventProducerService producer, MarketDataProperties props) {
        this.client = client;
        this.producer = producer;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${market-data.interval-seconds:120}", timeUnit = TimeUnit.SECONDS)
    public void poll() {
        List<String> symbols = props.symbols();
        for (int i = 0; i < symbols.size(); i += props.batchSize()) {
            List<String> batch = symbols.subList(i, Math.min(i + props.batchSize(), symbols.size()));
            try {
                for (Quote quote : client.latest(batch)) {
                    producer.publishEvent(TOPIC, quote.symbol(), "MARKET_DATA", "MarketDataPoller", quote);
                }
            } catch (RuntimeException e) {
                log.error("Quote fetch failed for {} symbols, will retry next cycle", batch.size(), e);
            }
        }
    }
}
