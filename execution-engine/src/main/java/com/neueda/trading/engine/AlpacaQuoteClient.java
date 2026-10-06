package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * Alpaca market data, latest quotes for many symbols in one request (IEX feed).
 * Alpaca writes class shares as BRK.B, our instruments as BRK-B, so symbols are translated both ways.
 */
@Component
public class AlpacaQuoteClient implements QuoteClient {

    private final RestClient http;

    public AlpacaQuoteClient(MarketDataProperties props) {
        this.http = RestClient.builder()
            .baseUrl(props.baseUrl())
            .defaultHeader("APCA-API-KEY-ID", props.keyId())
            .defaultHeader("APCA-API-SECRET-KEY", props.secretKey())
            .build();
    }

    @Override
    public List<Quote> latest(List<String> symbols) {
        JsonNode body = http.get()
            .uri("/v2/stocks/quotes/latest?feed=iex&symbols={symbols}", String.join(",", symbols).replace('-', '.'))
            .retrieve()
            .body(JsonNode.class);

        if (body == null) {
            throw new IllegalStateException("Alpaca returned an empty response for " + symbols.size() + " symbols");
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
