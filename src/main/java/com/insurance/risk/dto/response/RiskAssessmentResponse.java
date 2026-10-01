package com.insurance.risk.dto.response;

import com.insurance.risk.entity.RiskAssessment;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class RiskAssessmentResponse {
    private UUID id;
    private UUID policyId;
    private UUID customerId;
    private String insuranceType;
    private BigDecimal riskScore;
    private List<Map<String, Object>> factorsApplied;
    private String decision;
    private ZonedDateTime createdAt;

    public static RiskAssessmentResponse from(RiskAssessment a) {
        return RiskAssessmentResponse.builder()
                .id(a.getId())
                .policyId(a.getPolicyId())
                .customerId(a.getCustomerId())
                .insuranceType(a.getInsuranceType())
                .riskScore(a.getRiskScore())
                .factorsApplied(a.getFactorsApplied())
                .decision(a.getDecision().name())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
