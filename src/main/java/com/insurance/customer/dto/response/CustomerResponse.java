package com.insurance.customer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.customer.entity.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerResponse {
    private UUID id;
    private String customerCode;
    private UUID userId;
    private String firstName;
    private String lastName;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String maritalStatus;
    private String occupation;
    private BigDecimal annualIncome;
    private String email;
    private String mobileNumber;
    private String alternateMobile;
    private KycStatus kycStatus;
    private ZonedDateTime kycVerifiedAt;
    private String panNumber;
    private String bloodGroup;
    private Boolean smoker;
    private String preExistingConditions;
    private UUID companyId;
    private UUID branchId;
    private UUID assignedAgentId;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static CustomerResponse from(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .customerCode(c.getCustomerCode())
                .userId(c.getUserId())
                .firstName(c.getFirstName())
                .lastName(c.getLastName())
                .fullName(c.getFullName())
                .dateOfBirth(c.getDateOfBirth())
                .gender(c.getGender())
                .maritalStatus(c.getMaritalStatus())
                .occupation(c.getOccupation())
                .annualIncome(c.getAnnualIncome())
                .email(c.getEmail())
                .mobileNumber(c.getMobileNumber())
                .alternateMobile(c.getAlternateMobile())
                .kycStatus(c.getKycStatus())
                .kycVerifiedAt(c.getKycVerifiedAt())
                .panNumber(c.getPanNumber())
                .bloodGroup(c.getBloodGroup())
                .smoker(c.getSmoker())
                .preExistingConditions(c.getPreExistingConditions())
                .companyId(c.getCompanyId())
                .branchId(c.getBranchId())
                .assignedAgentId(c.getAssignedAgentId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
