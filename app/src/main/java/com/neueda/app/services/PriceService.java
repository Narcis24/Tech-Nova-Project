package com.neueda.app.services;

import com.neueda.app.repositories.PriceRepository;
import java.math.BigDecimal;

public class PriceService {
    
    private PriceRepository priceRepository;
    
    public PriceService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }
    
   public BigDecimal getCurrentPrice(String symbol) {
        return priceRepository.findBySymbol(symbol)
            .map(p -> BigDecimal.ZERO)  // TODO: extract actual price from price_data table
            .orElse(BigDecimal.ZERO);
    }
}