package com.insurance.auth.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Identifier is required")
    private String identifier;

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 digits")
    private String otpCode;

    @NotBlank(message = "OTP type is required")
    private String otpType;
}
