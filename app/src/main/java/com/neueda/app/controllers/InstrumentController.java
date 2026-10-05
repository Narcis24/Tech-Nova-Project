
package com.neueda.app.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.Size;
import java.util.List;
import com.neueda.app.dtos.InstrumentResponse;
import com.neueda.app.services.InstrumentService;


@RestController
@RequestMapping("/v1/instruments")

public class InstrumentController {


    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping("/tradable")
    public ResponseEntity<List<InstrumentResponse>> getTradableInstruments() {
        return ResponseEntity.ok(instrumentService.getAllTradable());
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<InstrumentResponse> getInstrument(@PathVariable @Size(min = 3, max = 5, message = "Symbol must be between 3 and 5 characters") String symbol) {
        return ResponseEntity.ok(instrumentService.getInstrument(symbol));
    }

    @GetMapping("/{symbol}/tradable")
    public ResponseEntity<Boolean> isTradable(@PathVariable @Size(min = 3, max = 5, message = "Symbol must be between 3 and 5 characters") String symbol) {
        return ResponseEntity.ok(instrumentService.isTradable(symbol));
    }
}
