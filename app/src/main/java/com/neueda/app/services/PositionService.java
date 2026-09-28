package com.neueda.app.services;

import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.exceptions.TradingException;
import com.neueda.app.models.Position;
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.repositories.PriceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import com.neueda.app.dtos.PositionMetricsResponse;



public class PositionService {
    private PositionRepository positionRepository;
    private PriceRepository priceRepository;

    public PositionService(PositionRepository positionRepository, PriceRepository priceRepository) {
        this.positionRepository = positionRepository;
        this.priceRepository = priceRepository;
    }

     public PositionResponse getPosition(String accountId, String symbol) {
        Position position = positionRepository
            .findByAccountIdAndSymbol(accountId, symbol)
            .orElseThrow(() -> new TradingException(
                "Position not found for account: " + accountId + ", symbol: " + symbol
            ));
        
        BigDecimal currentPrice = priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol)
            .map(p -> BigDecimal.ZERO)
            .orElse(BigDecimal.ZERO);
        
        BigDecimal marketValue = position.getMarketValue(currentPrice);
        BigDecimal unrealizedPnL = position.getUnrealizedPnL(currentPrice);
        
        return new PositionResponse(
            position.getAccountId(),
            position.getSymbol(),
            position.getQuantity(),
            position.getAverageCost(),
            currentPrice,
            marketValue,
            unrealizedPnL
        );
    }

    public List<PositionResponse> getAccountPositions(String accountId) {
        List<Position> positions = positionRepository.findByAccountId(accountId);
        List<PositionResponse> responses = new ArrayList<>();
        
        for (Position position : positions) {
            responses.add(convertToPositionResponse(position));
        }
        
        return responses;
    }

    private PositionResponse convertToPositionResponse(Position position) {
        BigDecimal currentPrice = priceRepository.findFirstBySymbolOrderByTradeDateDesc(position.getSymbol())
            .map(p -> BigDecimal.ZERO)
            .orElse(BigDecimal.ZERO);  // Default to 0 if price not found

        BigDecimal marketValue = position.getMarketValue(currentPrice);
        BigDecimal unrealizedPnL = position.getUnrealizedPnL(currentPrice);

        return new PositionResponse(
            position.getAccountId(),
            position.getSymbol(),
            position.getQuantity(),
            position.getAverageCost(),
            currentPrice,
            marketValue,
            unrealizedPnL
        );
    }

    public PositionMetricsResponse getPositionMetrics(String accountId, String symbol) {
        Position position = positionRepository
            .findByAccountIdAndSymbol(accountId, symbol)
            .orElseThrow(() -> new TradingException(
                "Position not found for account: " + accountId + ", symbol: " + symbol
            ));
        
        BigDecimal currentPrice = priceRepository.findFirstBySymbolOrderByTradeDateDesc(symbol)
            .map(p -> BigDecimal.ZERO)
            .orElse(BigDecimal.ZERO);
        
        BigDecimal marketValue = position.getMarketValue(currentPrice);
        BigDecimal unrealizedPnL = position.getUnrealizedPnL(currentPrice);
        
        // Calculate return% = unrealizedPnL / (quantity * averageCost)
        BigDecimal costBasis = position.getAverageCost()
            .multiply(new BigDecimal(position.getQuantity()));
        
        BigDecimal returnPercentage = costBasis.compareTo(BigDecimal.ZERO) > 0
            ? unrealizedPnL.divide(costBasis, 4, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        
        return new PositionMetricsResponse(
            position.getAccountId(),
            position.getSymbol(),
            position.getQuantity(),
            position.getAverageCost(),
            currentPrice,
            marketValue,
            unrealizedPnL,
            returnPercentage
        );
    } 

}


