package com.insurance.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long accessTokenExpiresIn;      // seconds
    private UserProfileResponse user;

    // When account needs further action
    private boolean requiresEmailVerification;
    private boolean requiresMobileVerification;
    private boolean requiresTotpSetup;
    private boolean requiresDeviceTrust;
    private String pendingIdentifier;       // masked email/mobile for UI hint
}
