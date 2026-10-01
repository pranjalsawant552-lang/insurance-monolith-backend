package com.insurance.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class SessionResponse {
    private UUID tokenId;
    private String deviceName;
    private String deviceType;
    private String browser;
    private String os;
    private String ipAddress;
    private ZonedDateTime createdAt;
    private ZonedDateTime expiresAt;
    private boolean current;
}
