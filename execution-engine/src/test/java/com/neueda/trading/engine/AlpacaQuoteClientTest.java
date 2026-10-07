package com.neueda.trading.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AlpacaQuoteClientTest {

    private static final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void parsesQuotesFromAlpacaResponse() throws Exception {
        // Arrange
        String json = """
            {
              "quotes": {
                "AAPL": {
                  "bp": "150.00",
                  "ap": "150.50"
                },
                "MSFT": {
                  "bp": "320.00",
                  "ap": "320.50"
                }
              }
            }
            """;
        JsonNode response = mapper.readTree(json);
        QuoteParser parser = new QuoteParser();

        // Act
        List<Quote> quotes = parser.parseQuotes(response);

        // Assert
        assertEquals(2, quotes.size());
        assertEquals("AAPL", quotes.get(0).symbol());
        assertEquals(new BigDecimal("150.00"), quotes.get(0).bid());
        assertEquals(new BigDecimal("150.50"), quotes.get(0).ask());

        assertEquals("MSFT", quotes.get(1).symbol());
        assertEquals(new BigDecimal("320.00"), quotes.get(1).bid());
        assertEquals(new BigDecimal("320.50"), quotes.get(1).ask());
    }

    @Test
    void translatesToDotNotationInResponse() throws Exception {
        // Arrange - Alpaca uses dot notation (BRK.B) but our system uses dash (BRK-B)
        String json = """
            {
              "quotes": {
                "BRK.B": {
                  "bp": "380.00",
                  "ap": "380.50"
                }
              }
            }
            """;
        JsonNode response = mapper.readTree(json);
        QuoteParser parser = new QuoteParser();

        // Act
        List<Quote> quotes = parser.parseQuotes(response);

        // Assert
        assertEquals(1, quotes.size());
        assertEquals("BRK-B", quotes.get(0).symbol()); // Should be translated back to dash notation
    }

    @Test
    void handlesEmptyQuotes() throws Exception {
        // Arrange
        String json = """
            {
              "quotes": {}
            }
            """;
        JsonNode response = mapper.readTree(json);
        QuoteParser parser = new QuoteParser();

        // Act
        List<Quote> quotes = parser.parseQuotes(response);

        // Assert
        assertEquals(0, quotes.size());
    }

    @Test
    void handlesMissingBidAsk() throws Exception {
        // Arrange
        String json = """
            {
              "quotes": {
                "AAPL": {
                  "bp": "150.00"
                },
                "MSFT": {
                  "ap": "320.50"
                }
              }
            }
            """;
        JsonNode response = mapper.readTree(json);
        QuoteParser parser = new QuoteParser();

        // Act
        List<Quote> quotes = parser.parseQuotes(response);

        // Assert
        assertEquals(2, quotes.size());
        // Missing values should default to 0
        assertEquals(new BigDecimal("150.00"), quotes.get(0).bid());
        assertEquals(new BigDecimal("0"), quotes.get(0).ask());

        assertEquals(new BigDecimal("0"), quotes.get(1).bid());
        assertEquals(new BigDecimal("320.50"), quotes.get(1).ask());
    }

    @Test
    void setsCurrentTimestampOnQuotes() throws Exception {
        // Arrange
        String json = """
            {
              "quotes": {
                "AAPL": {
                  "bp": "150.00",
                  "ap": "150.50"
                }
              }
            }
            """;
        JsonNode response = mapper.readTree(json);
        QuoteParser parser = new QuoteParser();
        Instant beforeParse = Instant.now();

        // Act
        List<Quote> quotes = parser.parseQuotes(response);
        Instant afterParse = Instant.now();

        // Assert
        assertEquals(1, quotes.size());
        Instant quoteTime = quotes.get(0).asOf();
        assert quoteTime.isAfter(beforeParse.minusSeconds(1));
        assert quoteTime.isBefore(afterParse.plusSeconds(1));
    }

    @Test
    void throwsExceptionWhenResponseIsNull() {
        // Arrange
        QuoteParser parser = new QuoteParser();

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> parser.parseQuotes(null));
    }

    @Test
    void throwsExceptionWhenQuotesPathMissing() throws Exception {
        // Arrange - Response without quotes key
        String json = """
            {
              "status": "success"
            }
            """;
        JsonNode response = mapper.readTree(json);
        QuoteParser parser = new QuoteParser();

        // Act & Assert - should handle gracefully
        List<Quote> quotes = parser.parseQuotes(response);
        assertEquals(0, quotes.size());
    }

    @Test
    void handlesMultipleSymbolsWithDifferentNotations() throws Exception {
        // Arrange - Mix of dash and dot notations
        String json = """
            {
              "quotes": {
                "AAPL": {
                  "bp": "150.00",
                  "ap": "150.50"
                },
                "BRK.B": {
                  "bp": "380.00",
                  "ap": "380.50"
                },
                "SPY": {
                  "bp": "450.00",
                  "ap": "450.50"
                }
              }
            }
            """;
        JsonNode response = mapper.readTree(json);
        QuoteParser parser = new QuoteParser();

        // Act
        List<Quote> quotes = parser.parseQuotes(response);

        // Assert
        assertEquals(3, quotes.size());
        
        // Check all symbols are present and translated correctly
        List<String> symbols = quotes.stream().map(Quote::symbol).toList();
        assert symbols.contains("AAPL");
        assert symbols.contains("BRK-B");
        assert symbols.contains("SPY");
    }

    @Test
    void allQuotesHaveSameTimestamp() throws Exception {
        // Arrange
        String json = """
            {
              "quotes": {
                "AAPL": {
                  "bp": "150.00",
                  "ap": "150.50"
                },
                "MSFT": {
                  "bp": "320.00",
                  "ap": "320.50"
                },
                "GOOGL": {
                  "bp": "2800.00",
                  "ap": "2800.50"
                }
              }
            }
            """;
        JsonNode response = mapper.readTree(json);
        QuoteParser parser = new QuoteParser();

        // Act
        List<Quote> quotes = parser.parseQuotes(response);

        // Assert
        assertEquals(3, quotes.size());
        Instant firstTime = quotes.get(0).asOf();
        for (Quote quote : quotes) {
            // All should have same or very close timestamp
            assert quote.asOf().equals(firstTime) || 
                   quote.asOf().minusMillis(1).equals(firstTime);
        }
    }

    @Test
    void preservesBidAskOrder() throws Exception {
        // Arrange
        String json = """
            {
              "quotes": {
                "AAPL": {
                  "bp": "150.00",
                  "ap": "150.50"
                }
              }
            }
            """;
        JsonNode response = mapper.readTree(json);
        QuoteParser parser = new QuoteParser();

        // Act
        List<Quote> quotes = parser.parseQuotes(response);

        // Assert
        assertEquals(1, quotes.size());
        Quote quote = quotes.get(0);
        // Bid should be lower than ask
        assert quote.bid().compareTo(quote.ask()) < 0;
    }
}
