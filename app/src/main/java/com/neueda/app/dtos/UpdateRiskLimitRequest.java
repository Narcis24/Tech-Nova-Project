package com.neueda.app.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An account holder's setting for one rule.
 * - thresholdPct: 0 to 100; null keeps the default threshold
 * - enabled: false turns the rule off; null means on
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRiskLimitRequest {
    @DecimalMin(value = "0", message = "Threshold must be between 0 and 100")
    @DecimalMax(value = "100", message = "Threshold must be between 0 and 100")
    private BigDecimal thresholdPct;

    private Boolean enabled;
}
