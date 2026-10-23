
package com.neueda.app.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.Size;
import com.neueda.app.dtos.PriceResponse;
import com.neueda.app.dtos.PriceHistoryPoint;
import com.neueda.app.dtos.QuoteResponse;
import com.neueda.app.services.EngineClient;
import java.util.List;
import com.neueda.app.services.PriceService;


@RestController
@RequestMapping("/v1/prices")

public class PriceController {


    private final PriceService priceService;
    private final EngineClient engineClient;

    public PriceController(PriceService priceService, EngineClient engineClient) {
        this.priceService = priceService;
        this.engineClient = engineClient;
    }

    @GetMapping("/{symbol}/history")
    public List<PriceHistoryPoint> getPriceHistory(@PathVariable String symbol) {
        return priceService.getHistory(symbol);
    }

    /** The latest live bid and ask, or 204 when the engine has none (e.g. no market data feed). */
    @GetMapping("/{symbol}/quote")
    public ResponseEntity<QuoteResponse> getQuote(@PathVariable String symbol) {
        return engineClient.quote(symbol.toUpperCase())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<PriceResponse> getPrice(@PathVariable @Size(min = 1, max = 10, message = "Symbol must be between 1 and 10 characters") String symbol) {
        return ResponseEntity.ok(new PriceResponse(symbol, priceService.getCurrentPrice(symbol)));
    }
}
