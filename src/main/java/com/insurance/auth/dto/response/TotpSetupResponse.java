package com.insurance.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TotpSetupResponse {
    private String secret;
    private String qrCodeUri;       // otpauth:// URI for QR scanning
    private String qrCodeBase64;    // base64 PNG of QR code
}
