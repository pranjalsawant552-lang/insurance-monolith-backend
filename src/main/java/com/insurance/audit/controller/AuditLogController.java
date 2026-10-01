package com.insurance.audit.controller;

import com.insurance.audit.dto.response.ApiResponse;
import com.insurance.audit.dto.response.AuditLogResponse;
import com.insurance.audit.dto.response.PagedResponse;
import com.insurance.audit.entity.AuditLogEntry;
import com.insurance.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Read-only audit trail API — Auditor and Super Admin only (AUDIT_LOG_READ
 * permission, seeded in Phase 1's RBAC catalog). There is intentionally no
 * write endpoint here: the only way a row enters this table is through the
 * Kafka consumer, so no one — not even a Super Admin — can edit or backdate
 * an audit entry through the API.
 */
@RestController
@RequestMapping("/audit-log")
@RequiredArgsConstructor
@Tag(name = "Audit Log", description = "Cross-service, append-only audit trail")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
    @Operation(summary = "Search the audit trail — filter by entity type/id, actor, action, company, date range")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLogEntry> result = auditLogService.search(
                entityType, entityId, actorId, action, companyId, from, to, page, size);

        Page<AuditLogResponse> mapped = result.map(AuditLogResponse::from);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(mapped)));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
    @Operation(summary = "Full change history for one record — powers a detail page's 'History' tab")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> historyForEntity(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLogEntry> result = auditLogService.historyForEntity(entityType, entityId, page, size);
        Page<AuditLogResponse> mapped = result.map(AuditLogResponse::from);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(mapped)));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
    @Operation(summary = "Total number of audit entries recorded (monitoring/sanity check)")
    public ResponseEntity<ApiResponse<Long>> count() {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.totalCount()));
    }
}
