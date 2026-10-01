package com.insurance.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TrustDeviceRequest {

    @NotBlank(message = "Device fingerprint is required")
    private String deviceFingerprint;

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 digits")
    private String otpCode;

    private String deviceName;
}
