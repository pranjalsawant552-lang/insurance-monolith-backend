package com.insurance.claims.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.claims.entity.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaimResponse {
    private UUID id;
    private String claimNumber;
    private UUID policyId;
    private String policyNumber;
    private UUID customerId;
    private UUID companyId;
    private InsuranceType insuranceType;
    private String claimType;
    private LocalDate incidentDate;
    private LocalDate reportedDate;
    private String description;
    private BigDecimal claimedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal settledAmount;
    private ClaimStatus status;
    private UUID assignedTo;
    private ClaimPriority priority;
    private boolean fraudFlag;
    private BigDecimal fraudScore;
    private String rejectionReason;
    private LocalDate settlementDate;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static ClaimResponse from(Claim c) {
        return ClaimResponse.builder()
                .id(c.getId())
                .claimNumber(c.getClaimNumber())
                .policyId(c.getPolicyId())
                .policyNumber(c.getPolicyNumber())
                .customerId(c.getCustomerId())
                .companyId(c.getCompanyId())
                .insuranceType(c.getInsuranceType())
                .claimType(c.getClaimType())
                .incidentDate(c.getIncidentDate())
                .reportedDate(c.getReportedDate())
                .description(c.getDescription())
                .claimedAmount(c.getClaimedAmount())
                .approvedAmount(c.getApprovedAmount())
                .settledAmount(c.getSettledAmount())
                .status(c.getStatus())
                .assignedTo(c.getAssignedTo())
                .priority(c.getPriority())
                .fraudFlag(c.isFraudFlag())
                .fraudScore(c.getFraudScore())
                .rejectionReason(c.getRejectionReason())
                .settlementDate(c.getSettlementDate())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
