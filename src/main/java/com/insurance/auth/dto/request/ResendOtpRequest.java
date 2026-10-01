package com.insurance.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendOtpRequest {

    @NotBlank(message = "Identifier is required")
    private String identifier;

    @NotBlank(message = "OTP type is required")
    private String otpType;
}
