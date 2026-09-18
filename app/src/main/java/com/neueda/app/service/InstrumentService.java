package com.neueda.app.service;

import com.neueda.app.dto.InstrumentResponse;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.model.Instrument;
import com.neueda.app.repository.InstrumentRepository;
import java.util.ArrayList;
import java.util.List;
import com.neueda.app.model.AssetClass;

public class InstrumentService {
    
    private InstrumentRepository instrumentRepository;
    
    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }
    

    public InstrumentResponse getInstrument(String symbol) {
        Instrument instrument = instrumentRepository.findBySymbol(symbol)
            .orElseThrow(() -> new InstrumentNotFoundException(
                "Instrument not found: " + symbol
            ));
        
        return new InstrumentResponse(instrument);
    }
    

    public boolean isTradable(String symbol) {
        Instrument instrument = instrumentRepository.findBySymbol(symbol)
            .orElseThrow(() -> new InstrumentNotFoundException(
                "Instrument not found: " + symbol
            ));
        
        return instrument.isTradable();
    }
    
    
    public List<InstrumentResponse> getAllTradable() {
        List<Instrument> instruments = instrumentRepository.findAllTradable();
        List<InstrumentResponse> responses = new ArrayList<>();
        
        for (Instrument instrument : instruments) {
            responses.add(new InstrumentResponse(instrument));
        }
        
        return responses;
    }
}