package com.neueda.app.services;

import com.neueda.app.dtos.MarketAssetResponse;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.PriceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MarketOverviewService {

    private final PriceRepository priceRepository;
    private final InstrumentRepository instrumentRepository;

    public MarketOverviewService(PriceRepository priceRepository, InstrumentRepository instrumentRepository) {
        this.priceRepository = priceRepository;
        this.instrumentRepository = instrumentRepository;
    }

    /** Every priced instrument with its latest close, change since the previous close and recent closes. */
    public List<MarketAssetResponse> overview() {
        Map<String, List<Price>> pricesBySymbol = priceRepository.findRecentPrices().stream()
            .collect(Collectors.groupingBy(Price::getSymbol));

        return instrumentRepository.findAll().stream()
            .filter(instrument -> pricesBySymbol.containsKey(instrument.getSymbol()))
            .sorted(Comparator.comparing(Instrument::getSymbol))
            .map(instrument -> toAsset(instrument, pricesBySymbol.get(instrument.getSymbol())))
            .toList();
    }

    private MarketAssetResponse toAsset(Instrument instrument, List<Price> prices) {
        List<Price> bars = prices.stream().sorted(Comparator.comparing(Price::getTradeDate)).toList();
        Price latest = bars.getLast();
        BigDecimal previousClose = bars.size() > 1 ? bars.get(bars.size() - 2).getPrice() : null;
        BigDecimal change = previousClose == null ? null : latest.getPrice().subtract(previousClose);
        // A zero previous close comes from a bad bar; leave the percentage unknown rather than failing.
        BigDecimal changePercent = previousClose == null || previousClose.signum() == 0
            ? null
            : change.divide(previousClose, 8, RoundingMode.HALF_UP);
        String assetClass = instrument.getAssetClass() == null ? null : instrument.getAssetClass().toString();

        return new MarketAssetResponse(
            instrument.getSymbol(),
            instrument.getName(),
            assetClass,
            instrument.getCurrency(),
            latest.getPrice(),
            previousClose,
            change,
            changePercent,
            latest.getTradeDate(),
            bars.stream().map(Price::getPrice).toList()
        );
    }
}
