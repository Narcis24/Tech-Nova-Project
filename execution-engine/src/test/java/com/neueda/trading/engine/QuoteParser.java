package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Helper class for testing quote parsing logic.
 * Mirrors the parsing logic from AlpacaQuoteClient.latest()
 */
class QuoteParser {

    /**
     * Parses quotes from Alpaca API response.
     * Translates symbol notation from dot (BRK.B) to dash (BRK-B).
     */
    List<Quote> parseQuotes(JsonNode body) {
        if (body == null) {
            throw new IllegalStateException("Cannot parse quotes from null response");
        }

        Instant now = Instant.now();
        List<Quote> quotes = new ArrayList<>();
        body.path("quotes").properties().forEach(e -> quotes.add(new Quote(
            e.getKey().replace('.', '-'),
            new BigDecimal(e.getValue().path("bp").asString("0")),
            new BigDecimal(e.getValue().path("ap").asString("0")),
            now)));
        return quotes;
    }
}
