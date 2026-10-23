package com.neueda.trading.engine;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The latest bid and ask the engine holds per symbol, for the app's trade page. Behind InternalTokenFilter. */
@RestController
@RequestMapping("/internal/quotes")
public class QuoteController {

    private final QuoteCache quotes;

    public QuoteController(QuoteCache quotes) {
        this.quotes = quotes;
    }

    @GetMapping("/{symbol}")
    public Quote quote(@PathVariable String symbol) {
        return quotes.latest(symbol.toUpperCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No quote for " + symbol));
    }
}
