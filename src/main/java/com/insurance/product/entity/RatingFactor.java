package com.insurance.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * One row = one rating rule against a specific product version, e.g.
 * "AGE_BAND 46-60 → MULTIPLIER 1.25" or "VEHICLE_TYPE=SUV → PERCENT_ADD 8".
 * This is the "pluggable rules engine, not hardcoded if/else" the project
 * brief calls out explicitly (§4.3, §9) — admins add/edit/remove rows here
 * through the RatingFactorController without any code change or redeploy,
 * and PremiumEngineService evaluates whatever rows exist for a version
 * rather than a fixed formula.
 *
 * Numeric factors (age, sum assured, term, risk score) use minValue/maxValue
 * as an inclusive range test. Categorical factors (vehicle type, location)
 * use conditionValue as an exact string match instead.
 */
@Entity
@Table(name = "premium_rate_factors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RatingFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Denormalized for fast filtering; product_version_id is the real scoping FK. */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_version_id", nullable = false)
    private UUID productVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "factor_type", nullable = false, length = 50)
    private FactorType factorType;

    /** Inclusive numeric range test — used by AGE_BAND, SUM_ASSURED_BAND, TERM_BAND, RISK_SCORE_BAND. Null bound = unbounded on that side. */
    @Column(name = "min_value", precision = 10, scale = 2)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 10, scale = 2)
    private BigDecimal maxValue;

    /** Exact-match categorical test — used by VEHICLE_TYPE, LOCATION. */
    @Column(name = "condition_value", length = 100)
    private String conditionValue;

    @Column(length = 100)
    private String label;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    @Builder.Default
    private AdjustmentType adjustmentType = AdjustmentType.MULTIPLIER;

    /** Meaning depends on adjustmentType: MULTIPLIER → ×value; FLAT_ADD → +₹value; PERCENT_ADD → +value% of running premium. */
    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal multiplier;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public enum FactorType {
        AGE_BAND, SUM_ASSURED_BAND, TERM_BAND, RISK_SCORE_BAND, VEHICLE_TYPE, LOCATION
    }

    public enum AdjustmentType {
        MULTIPLIER, FLAT_ADD, PERCENT_ADD
    }
}
