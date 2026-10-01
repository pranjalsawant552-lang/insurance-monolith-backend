package com.insurance.risk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * One row = one weighted risk factor for underwriting, scoped to an
 * insurance line. Mirrors the pattern established by product-service's
 * RatingFactor (Phase 3) — admin-editable, no redeploy needed to change
 * what drives a risk score. RiskAssessmentService evaluates whichever rows
 * are active for the given insuranceType against the inputs it receives.
 */
@Entity
@Table(name = "risk_factor_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskFactorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "insurance_type", nullable = false, length = 20)
    private String insuranceType; // LIFE, HEALTH, MOTOR

    @Enumerated(EnumType.STRING)
    @Column(name = "factor_type", nullable = false, length = 50)
    private FactorType factorType;

    /** Inclusive numeric range test for AGE, BMI, SUM_ASSURED_RATIO, PRIOR_CLAIMS_COUNT, VEHICLE_AGE. */
    @Column(name = "min_value", precision = 10, scale = 2)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 10, scale = 2)
    private BigDecimal maxValue;

    /** Exact-match categorical test for SMOKER, PRE_EXISTING_CONDITION, OCCUPATION_CLASS, VEHICLE_TYPE. */
    @Column(name = "condition_value", length = 100)
    private String conditionValue;

    @Column(length = 150)
    private String label;

    /** Points added to the running risk score (0-100 scale) when this factor matches. */
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public enum FactorType {
        AGE, BMI, SMOKER, PRE_EXISTING_CONDITION, OCCUPATION_CLASS,
        SUM_ASSURED_RATIO, PRIOR_CLAIMS_COUNT, VEHICLE_AGE, VEHICLE_TYPE, LOCATION_RISK_ZONE
    }
}
