package com.neueda.app.services;

import org.springframework.stereotype.Service;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.repositories.PriceRepository;
import java.math.BigDecimal;
import java.util.List;
import com.neueda.app.dtos.PriceHistoryPoint;

@Service
public class PriceService {

    private final PriceRepository priceRepository;

    public PriceService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    /** One year ending at the latest available session. Preserve actual dates and gaps. */
    public List<PriceHistoryPoint> getHistory(String symbol) {
        return priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol)
            .map(latest -> priceRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                symbol, latest.getTradeDate().minusYears(1)).stream()
                .map(price -> new PriceHistoryPoint(
                    price.getTradeDate(), price.getOpen(), price.getHigh(), price.getLow(), price.getPrice(),
                    price.getVolume()))
                .toList())
            .orElse(List.of());
    }

    /** Latest available close for the symbol. */
    public BigDecimal getCurrentPrice(String symbol) {
        return priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol)
            .orElseThrow(() -> new PriceNotFoundException("No price data for symbol: " + symbol))
            .getPrice();
    }
}
