package com.insurance.risk.dto.request;

import com.insurance.risk.entity.RiskFactorConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RiskFactorConfigRequest {
    @NotBlank private String insuranceType;
    @NotNull private RiskFactorConfig.FactorType factorType;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String conditionValue;
    private String label;
    @NotNull private BigDecimal weight;
    private boolean active = true;
}
