package com.insurance.policy.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.policy.entity.*;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolicyResponse {
    private UUID id;
    private String policyNumber;
    private UUID customerId;
    private UUID agentId;
    private UUID companyId;
    private UUID branchId;
    private InsuranceType insuranceType;
    private String productName;
    private String productCode;
    private Integer productVersion;
    private UUID premiumCalculationId;
    private UUID riskAssessmentId;
    private BigDecimal sumAssured;
    private BigDecimal premiumAmount;
    private PremiumFrequency premiumFrequency;
    private int policyTermYears;
    private LocalDate commencementDate;
    private LocalDate maturityDate;
    private LocalDate nextDueDate;
    private LocalDate lastPaidDate;
    private PolicyStatus status;
    private BigDecimal totalPremiumPaid;
    private ZonedDateTime issuedAt;
    private ZonedDateTime createdAt;

    // PDF-ingestion pivot (Phase 9)
    private String insurerName;
    private IngestionSource ingestionSource;
    private UUID sourceExtractionId;
    private UUID sourceDocumentId;
    private UUID renewedFromPolicyId;

    public static PolicyResponse from(Policy p) {
        return PolicyResponse.builder()
                .id(p.getId())
                .policyNumber(p.getPolicyNumber())
                .customerId(p.getCustomerId())
                .agentId(p.getAgentId())
                .companyId(p.getCompanyId())
                .branchId(p.getBranchId())
                .insuranceType(p.getInsuranceType())
                .productName(p.getProductName())
                .productCode(p.getProductCode())
                .productVersion(p.getProductVersion())
                .premiumCalculationId(p.getPremiumCalculationId())
                .riskAssessmentId(p.getRiskAssessmentId())
                .sumAssured(p.getSumAssured())
                .premiumAmount(p.getPremiumAmount())
                .premiumFrequency(p.getPremiumFrequency())
                .policyTermYears(p.getPolicyTermYears())
                .commencementDate(p.getCommencementDate())
                .maturityDate(p.getMaturityDate())
                .nextDueDate(p.getNextDueDate())
                .lastPaidDate(p.getLastPaidDate())
                .status(p.getStatus())
                .totalPremiumPaid(p.getTotalPremiumPaid())
                .issuedAt(p.getIssuedAt())
                .createdAt(p.getCreatedAt())
                .insurerName(p.getInsurerName())
                .ingestionSource(p.getIngestionSource())
                .sourceExtractionId(p.getSourceExtractionId())
                .sourceDocumentId(p.getSourceDocumentId())
                .renewedFromPolicyId(p.getRenewedFromPolicyId())
                .build();
    }
}
