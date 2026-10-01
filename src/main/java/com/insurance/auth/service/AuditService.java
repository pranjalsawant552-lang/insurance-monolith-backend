package com.insurance.auth.service;

import com.insurance.auth.entity.AuthAuditLog;
import com.insurance.auth.repository.AuthAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuthAuditLogRepository auditLogRepository;

    @Async
    public void log(String eventType, UUID userId, String ipAddress,
                    String userAgent, Map<String, Object> details) {
        try {
            AuthAuditLog entry = AuthAuditLog.builder()
                    .eventType(eventType)
                    .userId(userId)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .details(details)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log for event {}: {}", eventType, e.getMessage());
        }
    }

    @Async
    public void log(String eventType, UUID userId, String ipAddress, String userAgent) {
        log(eventType, userId, ipAddress, userAgent, Map.of());
    }
}
