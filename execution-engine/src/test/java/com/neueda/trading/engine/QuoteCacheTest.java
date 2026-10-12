package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuoteCacheTest {

    private QuoteCache cache;
    private Quote quote;
    private Instant now;

    @BeforeEach
    void setUp() {
        cache = new QuoteCache();
        now = Instant.now();
        quote = new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.50"), now);
    }

    @Test
    void storesQuoteSuccessfully() {
        // Act
        cache.put(quote);

        // Assert - verify by retrieving
        Optional<Quote> retrieved = cache.fresh("AAPL", now, Duration.ofSeconds(60));
        assertTrue(retrieved.isPresent());
        assertEquals(quote.symbol(), retrieved.get().symbol());
    }

    @Test
    void retrievesFreshQuote() {
        // Arrange
        cache.put(quote);
        Instant oneSecondLater = now.plusSeconds(1);

        // Act
        Optional<Quote> retrieved = cache.fresh("AAPL", oneSecondLater, Duration.ofSeconds(60));

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(new BigDecimal("150.00"), retrieved.get().bid());
        assertEquals(new BigDecimal("150.50"), retrieved.get().ask());
    }

    @Test
    void returnsEmptyForNonExistentSymbol() {
        // Act
        Optional<Quote> retrieved = cache.fresh("UNKNOWN", now, Duration.ofSeconds(60));

        // Assert
        assertFalse(retrieved.isPresent());
    }

    @Test
    void returnsEmptyForStaleQuote() {
        // Arrange
        cache.put(quote);
        Instant twoMinutesLater = now.plusSeconds(120);
        Duration maxAge = Duration.ofSeconds(60);

        // Act
        Optional<Quote> retrieved = cache.fresh("AAPL", twoMinutesLater, maxAge);

        // Assert
        assertFalse(retrieved.isPresent());
    }

    @Test
    void returnsQuoteAtExactMaxAge() {
        // Arrange
        cache.put(quote);
        Instant exactlyMaxAgeLater = now.plus(Duration.ofSeconds(60));
        Duration maxAge = Duration.ofSeconds(60);

        // Act
        Optional<Quote> retrieved = cache.fresh("AAPL", exactlyMaxAgeLater, maxAge);

        // Assert
        assertTrue(retrieved.isPresent());
    }

    @Test
    void returnsEmptyJustAfterMaxAge() {
        // Arrange
        cache.put(quote);
        Instant oneMillisecondAfterMaxAge = now.plusSeconds(60).plusMillis(1);
        Duration maxAge = Duration.ofSeconds(60);

        // Act
        Optional<Quote> retrieved = cache.fresh("AAPL", oneMillisecondAfterMaxAge, maxAge);

        // Assert
        assertFalse(retrieved.isPresent());
    }

    @Test
    void updatesQuoteForExistingSymbol() {
        // Arrange
        cache.put(quote);
        Instant laterTime = now.plusSeconds(5);
        Quote updatedQuote = new Quote("AAPL", new BigDecimal("151.00"), new BigDecimal("151.50"), laterTime);

        // Act
        cache.put(updatedQuote);
        Optional<Quote> retrieved = cache.fresh("AAPL", laterTime, Duration.ofSeconds(60));

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals(new BigDecimal("151.00"), retrieved.get().bid());
        assertEquals(new BigDecimal("151.50"), retrieved.get().ask());
    }

    @Test
    void cacheMultipleSymbols() {
        // Arrange
        Quote aapl = new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.50"), now);
        Quote msft = new Quote("MSFT", new BigDecimal("320.00"), new BigDecimal("320.50"), now);
        Quote googl = new Quote("GOOGL", new BigDecimal("2800.00"), new BigDecimal("2800.50"), now);

        // Act
        cache.put(aapl);
        cache.put(msft);
        cache.put(googl);

        // Assert
        assertTrue(cache.fresh("AAPL", now, Duration.ofSeconds(60)).isPresent());
        assertTrue(cache.fresh("MSFT", now, Duration.ofSeconds(60)).isPresent());
        assertTrue(cache.fresh("GOOGL", now, Duration.ofSeconds(60)).isPresent());
    }

    @Test
    void handlesConcurrentAccess() throws InterruptedException {
        // Arrange
        Quote[] quotes = new Quote[3];
        quotes[0] = new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.50"), now);
        quotes[1] = new Quote("MSFT", new BigDecimal("320.00"), new BigDecimal("320.50"), now);
        quotes[2] = new Quote("GOOGL", new BigDecimal("2800.00"), new BigDecimal("2800.50"), now);

        // Act - simulate concurrent puts
        Thread t1 = new Thread(() -> cache.put(quotes[0]));
        Thread t2 = new Thread(() -> cache.put(quotes[1]));
        Thread t3 = new Thread(() -> cache.put(quotes[2]));

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        // Assert
        assertTrue(cache.fresh("AAPL", now, Duration.ofSeconds(60)).isPresent());
        assertTrue(cache.fresh("MSFT", now, Duration.ofSeconds(60)).isPresent());
        assertTrue(cache.fresh("GOOGL", now, Duration.ofSeconds(60)).isPresent());
    }

    @Test
    void handlesVeryShortMaxAge() {
        // Arrange
        cache.put(quote);
        Instant oneNanosecondLater = now.plusNanos(1);
        Duration maxAge = Duration.ZERO;

        // Act
        Optional<Quote> retrieved = cache.fresh("AAPL", oneNanosecondLater, maxAge);

        // Assert
        assertFalse(retrieved.isPresent());
    }
}
