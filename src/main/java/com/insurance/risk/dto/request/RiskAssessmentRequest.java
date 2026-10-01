package com.insurance.risk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RiskAssessmentRequest(
        UUID policyId,
        UUID customerId,
        @NotBlank String insuranceType,
        @NotNull UUID productId,
        Integer age,
        BigDecimal bmi,
        Boolean smoker,
        Boolean preExistingCondition,
        String occupationClass,
        BigDecimal sumAssured,
        BigDecimal annualIncome,
        Integer priorClaimsCount,
        Integer vehicleAge,
        String vehicleType,
        String locationRiskZone,
        UUID requestedBy
) {}
