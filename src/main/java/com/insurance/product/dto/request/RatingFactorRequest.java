package com.insurance.product.dto.request;

import com.insurance.product.entity.RatingFactor;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RatingFactorRequest {

    @NotNull private RatingFactor.FactorType factorType;

    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String conditionValue;

    private String label;
    private String description;

    @NotNull private RatingFactor.AdjustmentType adjustmentType;
    @NotNull private BigDecimal multiplier;

    private boolean active = true;
}
