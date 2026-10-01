package com.insurance.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Defines how to pull structured fields out of raw extracted text for one
 * (documentType, issuerCode) combination — e.g. "STAR_HEALTH policy
 * schedules put the policy number after the text 'Policy No:'". This is
 * what makes extraction "pluggable, not hardcoded", same principle as the
 * rating-factor and risk-factor engines from earlier phases: an ops team
 * adds/edits templates through the admin API as new insurer document
 * layouts are encountered, without a code change or redeploy.
 *
 * Rules are intentionally simple (regex with a named capture group) rather
 * than a full parsing DSL — insurer policy schedules are fairly consistent,
 * label-value layouts, and regex covers that well without the complexity
 * (and failure modes) of a heavier extraction framework.
 */
@Entity
@Table(name = "extraction_templates", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"document_type", "issuer_code"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** e.g. POLICY_SCHEDULE, AADHAAR, PAN, RC_BOOK, HOSPITAL_BILL — matches Document.documentType. */
    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    /** e.g. STAR_HEALTH, HDFC_ERGO, GOVT_IN, GENERIC — GENERIC is the fallback template tried when no issuer-specific one matches or is known yet. */
    @Column(name = "issuer_code", nullable = false, length = 50)
    private String issuerCode;

    @Column(name = "issuer_name", length = 150)
    private String issuerName;

    /** [{fieldName, regexPattern, required, description}, ...] — each rule's regex must
     *  contain exactly one capture group, whose match becomes the extracted field value. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_rules", columnDefinition = "jsonb", nullable = false)
    private List<FieldRule> fieldRules;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FieldRule {
        private String fieldName;
        private String regexPattern;
        private boolean required;
        private String description;
    }
}
