package com.insurance.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One row per premium calculation performed — whether an anonymous quote or
 * the calculation that priced an actual policy issuance. Answers "how was
 * this premium arrived at" for disputes/regulatory queries, per brief §4.3's
 * explicit callout that this audit trail was missing. Never updated after
 * creation.
 */
@Entity
@Table(name = "premium_calculation_audits")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PremiumCalculationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_version_id", nullable = false)
    private UUID productVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculated_for", nullable = false, length = 20)
    private CalculatedFor calculatedFor;

    /** The Policy id once issued via policy-service; null for an anonymous/quote-only calculation. */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    // ── Inputs ──────────────────────────────────────────────────────────────
    @Column(name = "sum_assured", nullable = false, precision = 15, scale = 2)
    private BigDecimal sumAssured;

    @Column(nullable = false, length = 20)
    private String frequency;

    @Column(name = "term_years", nullable = false)
    private int termYears;

    private Integer age;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    private String location;

    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rider_ids", columnDefinition = "jsonb")
    private List<String> riderIds;

    // ── Output breakdown ────────────────────────────────────────────────────
    @Column(name = "base_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePremium;

    /** [{factorType, label, adjustmentType, adjustmentValue, resultingAmount}, ...] — the full step-by-step trail. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factors_applied", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> factorsApplied;

    @Column(name = "rider_premium", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal riderPremium = BigDecimal.ZERO;

    @Column(name = "gst_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal gstAmount;

    @Column(name = "total_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPremium;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public enum CalculatedFor { QUOTE, POLICY_ISSUANCE }
}
