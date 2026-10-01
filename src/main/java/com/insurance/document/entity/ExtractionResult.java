package com.insurance.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One row per extraction attempt against an uploaded document — the record
 * a human reviews and confirms/corrects before the data is trusted enough
 * to create or update a Policy (or KYC record, etc.) elsewhere in the
 * platform. This is the verification step described to the client as
 * "non-negotiable, not a nice-to-have" — nothing downstream should ever
 * consume extractedFields directly without a row here reaching VERIFIED.
 */
@Entity
@Table(name = "extraction_results", indexes = {
        @Index(name = "idx_extraction_status", columnList = "status"),
        @Index(name = "idx_extraction_document", columnList = "document_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "owner_type", nullable = false, length = 50)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    /** The template actually used — null if no matching template existed and extraction fell back to "raw text only, no fields". */
    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "issuer_code", length = 50)
    private String issuerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_method", nullable = false, length = 20)
    private ExtractionMethod extractionMethod;

    /** Field values as automatically extracted — never overwritten after creation, so the
     *  original machine output is always distinguishable from what a human corrected. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_fields", columnDefinition = "jsonb")
    private Map<String, Object> extractedFields;

    /** Per-field confidence 0-100, based on whether the field matched its regex at all and how well-formed the match looks. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_confidence", columnDefinition = "jsonb")
    private Map<String, Integer> fieldConfidence;

    @Column(name = "overall_confidence")
    private Integer overallConfidence;

    /** Set once a reviewer confirms/corrects — this, not extractedFields, is what downstream
     *  services should read once status = VERIFIED. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verified_fields", columnDefinition = "jsonb")
    private Map<String, Object> verifiedFields;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExtractionStatus status = ExtractionStatus.PENDING_REVIEW;

    @Column(name = "raw_text_snapshot", columnDefinition = "text")
    private String rawTextSnapshot;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public enum ExtractionMethod { TEXT, OCR }

    public enum ExtractionStatus { PENDING_REVIEW, VERIFIED, REJECTED, NEEDS_BETTER_SCAN }
}
