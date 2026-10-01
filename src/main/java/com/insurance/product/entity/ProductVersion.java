package com.insurance.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable snapshot of a product's pricing/coverage-relevant fields at
 * one point in time. Created automatically whenever a product is first
 * created (version 1) and whenever it's subsequently updated (version N+1,
 * superseding version N) — see ProductVersionService. A Policy created
 * through policy-service pins the exact productVersionId it was priced and
 * issued under, so editing the live product later never silently changes
 * what an already-sold policy's terms were (the gap flagged in the Phase 1
 * gap analysis, §4.2/§10.10).
 */
@Entity
@Table(name = "product_versions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "version_number"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProductVersionStatus status = ProductVersionStatus.ACTIVE;

    /** Full snapshot: name, description, basePremium, min/max sumAssured, min/max age, min/max term, keyFeatures, riders. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> configSnapshot;

    @Column(name = "effective_from", nullable = false)
    @Builder.Default
    private ZonedDateTime effectiveFrom = ZonedDateTime.now();

    @Column(name = "effective_to")
    private ZonedDateTime effectiveTo;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public enum ProductVersionStatus { ACTIVE, SUPERSEDED }
}
