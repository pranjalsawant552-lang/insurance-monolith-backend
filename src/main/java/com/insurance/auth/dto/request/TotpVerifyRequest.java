package com.insurance.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TotpVerifyRequest {

    @NotBlank(message = "TOTP code is required")
    @Size(min = 6, max = 6, message = "TOTP code must be 6 digits")
    private String totpCode;
}
