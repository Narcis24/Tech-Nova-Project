package com.neueda.app.services;

import com.neueda.app.exceptions.TradingException;
import com.neueda.app.repositories.PriceRepository;
import java.math.BigDecimal;

public class PriceService {
    
    private PriceRepository priceRepository;
    
    public PriceService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }
    
   public BigDecimal getCurrentPrice(String symbol) {
        return priceRepository.findLatestPrice(symbol)
            .orElseThrow(() -> new TradingException(
                "Price not found for symbol: " + symbol
            ));
    }
}