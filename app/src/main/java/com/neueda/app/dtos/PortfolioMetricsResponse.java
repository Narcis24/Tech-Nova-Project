package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioMetricsResponse {
    private String accountId;
    private BigDecimal cashBalance;
    private BigDecimal totalMarketValue;
    private BigDecimal totalPortfolioValue;
    private BigDecimal totalUnrealizedPnL;
    private BigDecimal portfolioReturn;
    private List<PositionResponse> positions;
}
