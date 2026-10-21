package com.neueda.app.dtos;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SliceDrift {
    private String symbol;
    private BigDecimal targetPct;
    private BigDecimal currentPct;
    private BigDecimal driftPct;
}
