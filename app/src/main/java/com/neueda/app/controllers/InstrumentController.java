package com.neueda.app.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.Size;
import java.util.List;
import com.neueda.app.dtos.FundamentalsResponse;
import com.neueda.app.dtos.InstrumentResponse;
import com.neueda.app.services.FundamentalsService;
import com.neueda.app.services.InstrumentService;


@RestController
@RequestMapping("/v1/instruments")

public class InstrumentController {


    private final InstrumentService instrumentService;
    private final FundamentalsService fundamentalsService;

    public InstrumentController(InstrumentService instrumentService, FundamentalsService fundamentalsService) {
        this.instrumentService = instrumentService;
        this.fundamentalsService = fundamentalsService;
    }

    /** All instruments, or only tradable / non-tradable ones with ?tradable=true|false. */
    @GetMapping
    public ResponseEntity<List<InstrumentResponse>> getInstruments(@RequestParam(required = false) Boolean tradable) {
        return ResponseEntity.ok(instrumentService.getInstruments(tradable));
    }

    /** The company or fund behind the symbol and its latest results, or 204 when none are loaded. */
    @GetMapping("/{symbol}/fundamentals")
    public ResponseEntity<FundamentalsResponse> getFundamentals(@PathVariable String symbol) {
        return fundamentalsService.getFundamentals(symbol.toUpperCase())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<InstrumentResponse> getInstrument(@PathVariable @Size(min = 1, max = 10, message = "Symbol must be between 1 and 10 characters") String symbol) {
        return ResponseEntity.ok(instrumentService.getInstrument(symbol));
    }
}
