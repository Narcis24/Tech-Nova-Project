package com.neueda.app.services;

import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.models.Position;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.PriceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Values positions at the latest stored price: market value and unrealized P&L.
 * The one place this calculation lives, so every endpoint that shows a position agrees.
 */
@Service
public class PositionValuator {

    private final PriceRepository priceRepository;

    public PositionValuator(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    /** Values a single position. */
    public PositionResponse value(Position position) {
        BigDecimal price = priceRepository.findFirstBySymbolOrderByTradeDateDesc(position.getSymbol())
            .map(Price::getPrice)
            .orElse(null);
        return value(position, price);
    }

    /** Values many positions, fetching all their prices in one query. */
    public List<PositionResponse> valueAll(List<Position> positions) {
        if (positions.isEmpty()) {
            return List.of();
        }
        Map<String, BigDecimal> prices = priceRepository
            .findLatestBySymbols(positions.stream().map(Position::getSymbol).toList())
            .stream()
            .collect(Collectors.toMap(Price::getSymbol, Price::getPrice));
        return positions.stream()
            .map(position -> value(position, prices.get(position.getSymbol())))
            .toList();
    }

    private PositionResponse value(Position position, BigDecimal price) {
        // No price data: valued at 0, as before. Story 8 changes this here, once.
        BigDecimal currentPrice = price != null ? price : BigDecimal.ZERO;
        return new PositionResponse(
            position.getAccountId(),
            position.getSymbol(),
            position.getQuantity(),
            position.getAverageCost(),
            currentPrice,
            position.getMarketValue(currentPrice),
            position.getUnrealizedPnL(currentPrice)
        );
    }
}
