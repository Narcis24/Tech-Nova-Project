package com.neueda.app.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSnapshotResponse {
    private String accountId;
    private BigDecimal cashBalance;
    private BigDecimal totalMarketValue;
    private BigDecimal totalPortfolioValue;
    private List<PositionResponse> positions;
}
