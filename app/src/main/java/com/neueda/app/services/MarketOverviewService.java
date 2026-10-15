package com.neueda.app.services;

import com.neueda.app.dtos.MarketAssetResponse;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.PriceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MarketOverviewService {
    private final PriceRepository prices;
    private final InstrumentRepository instruments;
    public MarketOverviewService(PriceRepository prices, InstrumentRepository instruments) {
        this.prices = prices; this.instruments = instruments;
    }

    public List<MarketAssetResponse> overview() {
        var bySymbol = prices.findRecentPrices().stream().collect(Collectors.groupingBy(Price::getSymbol));
        return instruments.findAll().stream().filter(i -> bySymbol.containsKey(i.getSymbol()))
            .sorted(Comparator.comparing(i -> i.getSymbol()))
            .map(i -> {
                var bars = bySymbol.get(i.getSymbol()).stream().sorted(Comparator.comparing(Price::getTradeDate)).toList();
                var latest = bars.getLast();
                BigDecimal previous = bars.size() > 1 ? bars.get(bars.size() - 2).getPrice() : null;
                BigDecimal change = previous == null ? null : latest.getPrice().subtract(previous);
                // A zero previous close (bad provider bar) has no meaningful percentage; leave it unknown rather than failing every asset.
                BigDecimal percent = previous == null || previous.signum() == 0 ? null : change.divide(previous, 8, RoundingMode.HALF_UP);
                String assetClass = i.getAssetClass() == null ? null : i.getAssetClass().toString();
                return new MarketAssetResponse(i.getSymbol(), i.getName(), assetClass, i.getCurrency(),
                    latest.getPrice(), previous, change, percent, latest.getTradeDate(), bars.stream().map(Price::getPrice).toList());
            }).toList();
    }
}
