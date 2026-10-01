package com.insurance.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Email or mobile number is required")
    private String identifier;   // email or mobile
}
