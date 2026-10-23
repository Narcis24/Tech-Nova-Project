package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSnapshotResponse {
    private String accountId;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal cashBalance;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal totalMarketValue;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal totalPortfolioValue;
    private List<PositionResponse> positions;
    /** Cash held back by pending buy orders; included in cashBalance. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal lockedCash;
    /** Cash free for new orders and withdrawals: cashBalance minus lockedCash. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal availableCash;
    /** Shares held back by pending sell orders, by symbol; symbols with none are left out. */
    private Map<String, Long> lockedShares;

    /** A snapshot with nothing locked by pending orders. */
    public PortfolioSnapshotResponse(String accountId, BigDecimal cashBalance, BigDecimal totalMarketValue,
                                     BigDecimal totalPortfolioValue, List<PositionResponse> positions) {
        this(accountId, cashBalance, totalMarketValue, totalPortfolioValue, positions,
            BigDecimal.ZERO, cashBalance, Map.of());
    }
}
