package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketDataPollerTest {

    private QuoteClient client;
    private EventProducerService producer;
    private MarketDataProperties props;
    private MarketDataPoller poller;

    @BeforeEach
    void setUp() {
        client = mock(QuoteClient.class);
        producer = mock(EventProducerService.class);
        props = new MarketDataProperties(
            Arrays.asList("AAPL", "MSFT", "GOOGL"),
            120,  // interval
            25,   // batchSize
            2000, // maxAge
            600,  // staleDuration
            "http://api.example.com",
            "key123",
            "secret123"
        );
        poller = new MarketDataPoller(client, producer, props);
    }

    @Test
    void pollsFetchesAllSymbols() {
        // Arrange
        List<Quote> mockQuotes = Arrays.asList(
            new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.50"), Instant.now()),
            new Quote("MSFT", new BigDecimal("320.00"), new BigDecimal("320.50"), Instant.now()),
            new Quote("GOOGL", new BigDecimal("2800.00"), new BigDecimal("2800.50"), Instant.now())
        );
        when(client.latest(anyList())).thenReturn(mockQuotes);

        // Act
        poller.poll();

        // Assert
        verify(client).latest(eq(Arrays.asList("AAPL", "MSFT", "GOOGL")));
        verify(producer, times(3)).publishEvent(
            eq("market-data"),
            anyString(),
            eq("MARKET_DATA"),
            eq("MarketDataPoller"),
            any()
        );
    }

    @Test
    void publiishesEachQuoteWithSymbolAsKey() {
        // Arrange
        Quote aapl = new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.50"), Instant.now());
        Quote msft = new Quote("MSFT", new BigDecimal("320.00"), new BigDecimal("320.50"), Instant.now());
        when(client.latest(anyList())).thenReturn(Arrays.asList(aapl, msft));

        // Act
        poller.poll();

        // Assert
        verify(producer).publishEvent(
            eq("market-data"),
            eq("AAPL"),
            eq("MARKET_DATA"),
            eq("MarketDataPoller"),
            eq(aapl)
        );
        verify(producer).publishEvent(
            eq("market-data"),
            eq("MSFT"),
            eq("MARKET_DATA"),
            eq("MarketDataPoller"),
            eq(msft)
        );
    }

    @Test
    void handlesClientErrorGracefully() {
        // Arrange
        when(client.latest(anyList()))
            .thenThrow(new RuntimeException("API connection failed"));

        // Act & Assert - should not throw
        poller.poll();

        // Verify producer was never called due to error
        verify(producer, times(0)).publishEvent(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void batchesSymbolsByConfiguredSize() {
        // Arrange - Use parameters that pass validation
        List<String> fourSymbols = Arrays.asList("A", "B", "C", "D");
        MarketDataProperties batchProps = new MarketDataProperties(
            fourSymbols,
            3600,  // interval - 24 requests per day with batch size 3
            3,     // batchSize = 3
            2000,
            600,
            "http://api.example.com",
            "key123",
            "secret123"
        );
        MarketDataPoller batchPoller = new MarketDataPoller(client, producer, batchProps);

        List<Quote> mockQuotes = new ArrayList<>();
        for (String sym : fourSymbols) {
            mockQuotes.add(new Quote(sym, new BigDecimal("100.00"), new BigDecimal("100.50"), Instant.now()));
        }
        when(client.latest(anyList())).thenReturn(mockQuotes);

        // Act
        batchPoller.poll();

        // Assert - should call client 2 times (batches: 3, 1)
        verify(client, times(2)).latest(anyList());
    }

    @Test
    void publishesEventsToDedicatedTopic() {
        // Arrange
        Quote quote = new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.50"), Instant.now());
        when(client.latest(anyList())).thenReturn(Arrays.asList(quote));

        // Act
        poller.poll();

        // Assert
        verify(producer).publishEvent(
            eq("market-data"),
            anyString(),
            anyString(),
            anyString(),
            any()
        );
    }

    @Test
    void usesCorrectEventType() {
        // Arrange
        Quote quote = new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.50"), Instant.now());
        when(client.latest(anyList())).thenReturn(Arrays.asList(quote));

        // Act
        poller.poll();

        // Assert
        verify(producer).publishEvent(
            anyString(),
            anyString(),
            eq("MARKET_DATA"),
            anyString(),
            any()
        );
    }

    @Test
    void usesPollerAsEventSource() {
        // Arrange
        Quote quote = new Quote("MSFT", new BigDecimal("320.00"), new BigDecimal("320.50"), Instant.now());
        when(client.latest(anyList())).thenReturn(Arrays.asList(quote));

        // Act
        poller.poll();

        // Assert
        verify(producer).publishEvent(
            anyString(),
            anyString(),
            anyString(),
            eq("MarketDataPoller"),
            any()
        );
    }

    @Test
    void rejectsEmptySymbolList() {
        // Arrange & Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            new MarketDataProperties(
                new ArrayList<>(),
                120, 25, 2000, 600, "http://api.example.com", "key123", "secret123"
            )
        );
    }

    @Test
    void continuesBatchingAfterPartialFailure() {
        // Arrange - First batch fails, second succeeds
        List<String> fourSymbols = Arrays.asList("A", "B", "C", "D");
        MarketDataProperties twoPerBatch = new MarketDataProperties(
            fourSymbols,
            3600,  // interval - enough to pass quota validation
            2, 
            2000, 
            600, 
            "http://api.example.com", 
            "key123", 
            "secret123"
        );
        MarketDataPoller batchPoller = new MarketDataPoller(client, producer, twoPerBatch);

        Quote quote = new Quote("A", new BigDecimal("100.00"), new BigDecimal("100.50"), Instant.now());
        when(client.latest(anyList()))
            .thenThrow(new RuntimeException("Batch 1 failed"))
            .thenReturn(Arrays.asList(quote, quote));

        // Act
        batchPoller.poll();

        // Assert - should have attempted both batches despite first failure
        verify(client, times(2)).latest(anyList());
    }
}
