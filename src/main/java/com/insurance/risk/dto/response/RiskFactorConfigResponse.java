package com.insurance.risk.dto.response;

import com.insurance.risk.entity.RiskFactorConfig;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class RiskFactorConfigResponse {
    private UUID id;
    private String insuranceType;
    private String factorType;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String conditionValue;
    private String label;
    private BigDecimal weight;
    private boolean active;

    public static RiskFactorConfigResponse from(RiskFactorConfig f) {
        return RiskFactorConfigResponse.builder()
                .id(f.getId())
                .insuranceType(f.getInsuranceType())
                .factorType(f.getFactorType().name())
                .minValue(f.getMinValue())
                .maxValue(f.getMaxValue())
                .conditionValue(f.getConditionValue())
                .label(f.getLabel())
                .weight(f.getWeight())
                .active(f.isActive())
                .build();
    }
}
