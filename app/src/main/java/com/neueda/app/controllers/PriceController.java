
package com.neueda.app.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.Size;
import com.neueda.app.dtos.PriceResponse;
import com.neueda.app.services.PriceService;


@RestController
@RequestMapping("/v1/prices")

public class PriceController {


    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<PriceResponse> getPrice(@PathVariable @Size(min = 1, max = 10, message = "Symbol must be between 1 and 10 characters") String symbol) {
        return ResponseEntity.ok(new PriceResponse(symbol, priceService.getCurrentPrice(symbol)));
    }
}
