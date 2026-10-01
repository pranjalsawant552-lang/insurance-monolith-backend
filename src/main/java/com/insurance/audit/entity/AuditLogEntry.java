package com.insurance.audit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One row per create/update/delete on a sensitive entity anywhere in the
 * platform. Rows are never updated or deleted by the application (see
 * AuditLogRepository — it exposes no update/delete methods, and the DB user
 * this service connects as should be granted INSERT + SELECT only, not
 * UPDATE/DELETE, as a second line of defense — see the deployment notes in
 * the Phase 2 changelog).
 *
 * Populated by {@link com.insurance.audit.consumer.AuditEventConsumer}
 * listening on the shared "audit.entity-changed" Kafka topic that every
 * other service publishes to via its own lightweight AuditEventPublisher.
 */
@Entity
@Table(name = "audit_log_entries", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_actor", columnList = "actor_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_company", columnList = "company_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** e.g. POLICY, CLAIM, CUSTOMER, PAYMENT, PRODUCT, AGENT, DOCUMENT, USER, ROLE_PERMISSION */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /** The primary key of the affected record in its own service (a UUID as text, or a role/permission code pair for RBAC events). */
    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    /** CREATE, UPDATE, DELETE, or a domain-specific action code, e.g. STATUS_CHANGE, APPROVE, SETTLE. */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "branch_id")
    private UUID branchId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private Map<String, Object> afterState;

    /** Free-text human summary, e.g. "Claim approved for ₹45,000". Optional — makes the list screen readable without decoding JSON. */
    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** Correlates this row back to the originating HTTP request across services, once request tracing is wired in (Part B6 of the master plan). */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /** Which service published this event — set from the Kafka message, not guessable from entityType alone (e.g. PAYMENT could be payment-service or claims-service triggering a payout). */
    @Column(name = "source_service", length = 50)
    private String sourceService;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
