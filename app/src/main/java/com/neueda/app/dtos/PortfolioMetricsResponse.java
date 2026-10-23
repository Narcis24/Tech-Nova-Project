package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioMetricsResponse {
    private String accountId;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal cashBalance;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal totalMarketValue;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal totalPortfolioValue;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal totalUnrealizedPnL;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal portfolioReturn;
    private List<PositionResponse> positions;
    /** Cash held back by pending buy orders; included in cashBalance. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal lockedCash;
    /** Cash free for new orders and withdrawals: cashBalance minus lockedCash. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal availableCash;

    /** Metrics with nothing locked by pending orders. */
    public PortfolioMetricsResponse(String accountId, BigDecimal cashBalance, BigDecimal totalMarketValue,
                                    BigDecimal totalPortfolioValue, BigDecimal totalUnrealizedPnL,
                                    BigDecimal portfolioReturn, List<PositionResponse> positions) {
        this(accountId, cashBalance, totalMarketValue, totalPortfolioValue, totalUnrealizedPnL, portfolioReturn,
            positions, BigDecimal.ZERO, cashBalance);
    }
}
