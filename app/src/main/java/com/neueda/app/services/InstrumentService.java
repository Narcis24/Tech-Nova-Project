package com.neueda.app.services;

import com.neueda.app.dtos.InstrumentResponse;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.models.Instrument;
import com.neueda.app.repositories.InstrumentRepository;
import java.util.ArrayList;
import java.util.List;

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
        List<Instrument> instruments = instrumentRepository.findByTradable(true);
        List<InstrumentResponse> responses = new ArrayList<>();
        
        for (Instrument instrument : instruments) {
            responses.add(new InstrumentResponse(instrument));
        }
        
        return responses;
    }
}