package com.insurance.agent.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.agent.entity.*;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {
    private UUID id;
    private String agentCode;
    private AgentType agentType;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String mobileNumber;
    private String licenseNumber;
    private LocalDate licenseExpiry;
    private String qualification;
    private UUID managerId;
    private String managerName;
    private UUID companyId;
    private UUID branchId;
    private String region;
    private BigDecimal commissionRateLife;
    private BigDecimal commissionRateHealth;
    private BigDecimal commissionRateMotor;
    private AgentStatus status;
    private ZonedDateTime createdAt;

    public static AgentResponse from(Agent a) {
        return AgentResponse.builder()
                .id(a.getId())
                .agentCode(a.getAgentCode())
                .agentType(a.getAgentType())
                .firstName(a.getFirstName())
                .lastName(a.getLastName())
                .fullName(a.getFullName())
                .email(a.getEmail())
                .mobileNumber(a.getMobileNumber())
                .licenseNumber(a.getLicenseNumber())
                .licenseExpiry(a.getLicenseExpiry())
                .qualification(a.getQualification())
                .managerId(a.getManager() != null ? a.getManager().getId() : null)
                .managerName(a.getManager() != null ? a.getManager().getFullName() : null)
                .companyId(a.getCompanyId())
                .branchId(a.getBranchId())
                .region(a.getRegion())
                .commissionRateLife(a.getCommissionRateLife())
                .commissionRateHealth(a.getCommissionRateHealth())
                .commissionRateMotor(a.getCommissionRateMotor())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
