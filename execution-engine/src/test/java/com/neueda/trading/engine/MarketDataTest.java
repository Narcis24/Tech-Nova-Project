package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketDataTest {

    private static List<String> symbols(int n) {
        return IntStream.range(0, n).mapToObj(i -> "S" + i).toList();
    }

    private static MarketDataProperties props(int symbols, int intervalSeconds, int quota) {
        return new MarketDataProperties(symbols(symbols), intervalSeconds, 25, quota, 600, "http://x", "", "");
    }

    @Test
    void quotaArithmetic() {
        // 36 symbols -> 2 batches, every 120s -> 720 cycles -> 1,440 requests/day
        assertEquals(1440, MarketDataProperties.requestsPerDay(36, 25, 120));
        // 25 symbols fit in one batch
        assertEquals(720, MarketDataProperties.requestsPerDay(25, 25, 120));
    }

    @Test
    void startupFailsWhenScheduleExceedsQuota() {
        assertThrows(IllegalArgumentException.class, () -> props(36, 30, 2000)); // 5,760/day
    }

    @Test
    void pollerBatchesSymbolsAndPublishesOneMessagePerSymbolKeyedBySymbol() {
        QuoteClient client = mock(QuoteClient.class);
        EventProducerService producer = mock(EventProducerService.class);
        when(client.latest(anyList())).thenAnswer(inv ->
            ((List<String>) inv.getArgument(0)).stream()
                .map(s -> new Quote(s, BigDecimal.ONE, BigDecimal.TEN, Instant.now())).toList());

        new MarketDataPoller(client, producer, props(36, 120, 2000)).poll();

        ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass(List.class);
        verify(client, times(2)).latest(batches.capture());
        assertEquals(25, batches.getAllValues().get(0).size());
        assertEquals(11, batches.getAllValues().get(1).size());
        verify(producer, times(36)).publishEvent(eq("market-data"), any(), eq("MARKET_DATA"), any(), any());
        verify(producer).publishEvent(eq("market-data"), eq("S35"), any(), any(), any(Quote.class));
    }

    @Test
    void failedBatchDoesNotStopTheNextOne() {
        QuoteClient client = mock(QuoteClient.class);
        EventProducerService producer = mock(EventProducerService.class);
        when(client.latest(anyList()))
            .thenThrow(new RuntimeException("boom"))
            .thenReturn(List.of(new Quote("S30", BigDecimal.ONE, BigDecimal.TEN, Instant.now())));

        new MarketDataPoller(client, producer, props(36, 120, 2000)).poll();

        verify(producer, times(1)).publishEvent(any(), eq("S30"), any(), any(), any());
    }

    @Test
    void cacheIgnoresStaleQuotes() {
        QuoteCache cache = new QuoteCache(new com.fasterxml.jackson.databind.ObjectMapper());
        Instant now = Instant.now();
        cache.put(new Quote("AAPL", BigDecimal.ONE, BigDecimal.TEN, now.minusSeconds(700)));

        assertEquals(true, cache.fresh("AAPL", now, Duration.ofSeconds(600)).isEmpty());
        assertEquals(true, cache.fresh("AAPL", now, Duration.ofSeconds(800)).isPresent());
        assertEquals(true, cache.fresh("MSFT", now, Duration.ofSeconds(800)).isEmpty());
    }
}
