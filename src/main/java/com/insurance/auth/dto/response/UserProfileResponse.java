package com.insurance.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.auth.entity.User;
import com.insurance.auth.entity.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String mobileNumber;
    private UserRole role;
    private UUID branchId;
    private UUID companyId;
    private boolean emailVerified;
    private boolean mobileVerified;
    private boolean active;
    private boolean totpEnabled;
    private ZonedDateTime lastLoginAt;
    private ZonedDateTime createdAt;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .role(user.getRole())
                .branchId(user.getBranchId())
                .companyId(user.getCompanyId())
                .emailVerified(user.isEmailVerified())
                .mobileVerified(user.isMobileVerified())
                .active(user.isActive())
                .totpEnabled(user.isTotpEnabled())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
