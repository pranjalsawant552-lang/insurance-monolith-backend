package com.insurance.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * When an ingestion (automatic, from a Kafka event off document-service's
 * verification queue, or manual via POST /policies/ingest) can't be turned
 * into a valid Policy — a required field was missing or unparseable from
 * the verified extraction — it lands here instead of silently failing or
 * (worse) crashing the Kafka consumer. An ops/agent user fixes it by hand
 * via POST /policies/ingestion-failures/{id}/resolve.
 */
@Entity
@Table(name = "policy_ingestion_failures")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicyIngestionFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_extraction_id")
    private UUID sourceExtractionId;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "customer_id")
    private UUID customerId;

    /** The verified fields exactly as document-service sent them — so a reviewer
     *  fixing this by hand doesn't have to go re-open the extraction elsewhere. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_fields", columnDefinition = "jsonb")
    private Map<String, Object> rawFields;

    @Column(name = "error_message", nullable = false, length = 1000)
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FailureStatus status = FailureStatus.PENDING;

    @Column(name = "resolved_policy_id")
    private UUID resolvedPolicyId;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public enum FailureStatus { PENDING, RESOLVED, DISMISSED }
}
