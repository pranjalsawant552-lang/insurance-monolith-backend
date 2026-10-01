package com.insurance.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Identifier (email or mobile) is required")
    private String identifier;      // email or mobile number

    @NotBlank(message = "Password is required")
    private String password;

    private String deviceFingerprint;
    private String deviceName;
    private String totpCode;        // optional — required only if TOTP is enabled
}
