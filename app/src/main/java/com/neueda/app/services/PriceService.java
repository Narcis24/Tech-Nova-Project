package com.neueda.app.services;

import org.springframework.stereotype.Service;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.repositories.PriceRepository;
import java.math.BigDecimal;

@Service
public class PriceService {

    private final PriceRepository priceRepository;

    public PriceService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    /** Latest available close for the symbol. */
    public BigDecimal getCurrentPrice(String symbol) {
        return priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol)
            .orElseThrow(() -> new PriceNotFoundException("No price data for symbol: " + symbol))
            .getPrice();
    }
}
