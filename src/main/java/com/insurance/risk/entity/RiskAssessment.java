package com.insurance.risk.entity;

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
 * One row per underwriting risk calculation — append-only, never updated
 * after creation, so an underwriter's decision can always be traced back to
 * exactly what score and factors drove it.
 */
@Entity
@Table(name = "risk_assessments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The policy application this assessment was requested for — set once the policy exists in PENDING/UNDERWRITING. */
    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "insurance_type", nullable = false, length = 20)
    private String insuranceType;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    // ── Inputs ──────────────────────────────────────────────────────────────
    private Integer age;
    @Column(precision = 5, scale = 2) private BigDecimal bmi;
    private Boolean smoker;
    @Column(name = "pre_existing_condition") private Boolean preExistingCondition;
    @Column(name = "occupation_class", length = 50) private String occupationClass;
    @Column(name = "sum_assured", precision = 15, scale = 2) private BigDecimal sumAssured;
    @Column(name = "annual_income", precision = 15, scale = 2) private BigDecimal annualIncome;
    @Column(name = "prior_claims_count") private Integer priorClaimsCount;
    @Column(name = "vehicle_age") private Integer vehicleAge;
    @Column(name = "vehicle_type", length = 50) private String vehicleType;
    @Column(name = "location_risk_zone", length = 50) private String locationRiskZone;

    // ── Output ──────────────────────────────────────────────────────────────
    @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal riskScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factors_applied", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> factorsApplied;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public enum Decision { AUTO_APPROVE, MANUAL_REVIEW, AUTO_DECLINE }
}
