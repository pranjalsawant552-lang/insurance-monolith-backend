package com.insurance.agent.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.agent.entity.*;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class CommissionResponse {
    private UUID id;
    private UUID agentId;
    private String agentCode;
    private String agentName;
    private String policyNumber;
    private String insuranceType;
    private String commissionType;
    private BigDecimal premiumAmount;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private CommissionStatus status;
    private String paymentReference;
    private ZonedDateTime paidAt;
    private ZonedDateTime createdAt;

    public static CommissionResponse from(AgentCommission c) {
        return CommissionResponse.builder()
                .id(c.getId())
                .agentId(c.getAgent().getId())
                .agentCode(c.getAgent().getAgentCode())
                .agentName(c.getAgent().getFullName())
                .policyNumber(c.getPolicyNumber())
                .insuranceType(c.getInsuranceType())
                .commissionType(c.getCommissionType())
                .premiumAmount(c.getPremiumAmount())
                .commissionRate(c.getCommissionRate())
                .commissionAmount(c.getCommissionAmount())
                .status(c.getStatus())
                .paymentReference(c.getPaymentReference())
                .paidAt(c.getPaidAt())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
