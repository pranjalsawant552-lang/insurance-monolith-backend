package com.insurance.product.dto.response;

import com.insurance.product.entity.RatingFactor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class RatingFactorResponse {
    private UUID id;
    private UUID productVersionId;
    private String factorType;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String conditionValue;
    private String label;
    private String description;
    private String adjustmentType;
    private BigDecimal multiplier;
    private boolean active;

    public static RatingFactorResponse from(RatingFactor f) {
        return RatingFactorResponse.builder()
                .id(f.getId())
                .productVersionId(f.getProductVersionId())
                .factorType(f.getFactorType().name())
                .minValue(f.getMinValue())
                .maxValue(f.getMaxValue())
                .conditionValue(f.getConditionValue())
                .label(f.getLabel())
                .description(f.getDescription())
                .adjustmentType(f.getAdjustmentType().name())
                .multiplier(f.getMultiplier())
                .active(f.isActive())
                .build();
    }
}
