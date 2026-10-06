package com.neueda.app.services;

import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.exceptions.TradingException;
import com.neueda.app.models.Position;
import com.neueda.app.repositories.PositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import com.neueda.app.dtos.PositionMetricsResponse;
import org.springframework.stereotype.Service;

@Service
public class PositionService {
    private final PositionRepository positionRepository;
    private final PositionValuator positionValuator;

    public PositionService(PositionRepository positionRepository, PositionValuator positionValuator) {
        this.positionRepository = positionRepository;
        this.positionValuator = positionValuator;
    }

    public PositionResponse getPosition(String accountId, String symbol) {
        return positionValuator.value(findPosition(accountId, symbol));
    }

    public List<PositionResponse> getAccountPositions(String accountId) {
        return positionValuator.valueAll(positionRepository.findByAccountId(accountId));
    }

    public PositionMetricsResponse getPositionMetrics(String accountId, String symbol) {
        PositionResponse position = positionValuator.value(findPosition(accountId, symbol));

        // Calculate return% = unrealizedPnL / (quantity * averageCost)
        BigDecimal costBasis = position.getAverageCost()
            .multiply(new BigDecimal(position.getQuantity()));

        BigDecimal returnPercentage = costBasis.compareTo(BigDecimal.ZERO) > 0
            ? position.getUnrealizedPnL().divide(costBasis, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new PositionMetricsResponse(
            position.getAccountId(),
            position.getSymbol(),
            position.getQuantity(),
            position.getAverageCost(),
            position.getCurrentPrice(),
            position.getMarketValue(),
            position.getUnrealizedPnL(),
            returnPercentage
        );
    }

    private Position findPosition(String accountId, String symbol) {
        return positionRepository
            .findByAccountIdAndSymbol(accountId, symbol)
            .orElseThrow(() -> new TradingException(
                "Position not found for account: " + accountId + ", symbol: " + symbol
            ));
    }
}
