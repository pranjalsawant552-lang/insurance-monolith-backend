package com.insurance.audit.dto.response;

import com.insurance.audit.entity.AuditLogEntry;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class AuditLogResponse {
    private UUID id;
    private String entityType;
    private String entityId;
    private String action;
    private UUID actorId;
    private String actorRole;
    private UUID companyId;
    private UUID branchId;
    private Map<String, Object> beforeState;
    private Map<String, Object> afterState;
    private String summary;
    private String ipAddress;
    private String correlationId;
    private String sourceService;
    private ZonedDateTime createdAt;

    public static AuditLogResponse from(AuditLogEntry e) {
        return AuditLogResponse.builder()
                .id(e.getId())
                .entityType(e.getEntityType())
                .entityId(e.getEntityId())
                .action(e.getAction())
                .actorId(e.getActorId())
                .actorRole(e.getActorRole())
                .companyId(e.getCompanyId())
                .branchId(e.getBranchId())
                .beforeState(e.getBeforeState())
                .afterState(e.getAfterState())
                .summary(e.getSummary())
                .ipAddress(e.getIpAddress())
                .correlationId(e.getCorrelationId())
                .sourceService(e.getSourceService())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
