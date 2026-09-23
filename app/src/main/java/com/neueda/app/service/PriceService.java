package com.neueda.app.service;

import com.neueda.app.exceptions.TradingException;
import com.neueda.app.repository.PriceRepository;
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