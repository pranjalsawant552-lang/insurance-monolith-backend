package com.insurance.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UnlockAccountRequest {

    @NotBlank(message = "Identifier is required")
    private String identifier;

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 digits")
    private String otpCode;

    @NotBlank(message = "Channel is required")
    @Pattern(regexp = "EMAIL|MOBILE", message = "Channel must be EMAIL or MOBILE")
    private String channel;
}
