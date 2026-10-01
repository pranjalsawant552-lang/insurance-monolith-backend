package com.insurance.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RevokeSessionRequest {

    @NotBlank(message = "Token ID is required")
    private String tokenId;
}
