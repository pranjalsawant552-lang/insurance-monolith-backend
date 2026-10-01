package com.insurance.risk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Admin-configurable thresholds for the automated fraud rule checks the
 * project brief calls for in phase 1 (§4.10): duplicate claims, early
 * claims after issuance, document mismatches. Each rule can be tuned or
 * disabled without a redeploy.
 */
@Entity
@Table(name = "fraud_rule_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FraudRuleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, unique = true, length = 50)
    private RuleType ruleType;

    /** Meaning depends on ruleType: EARLY_CLAIM → days since policy issuance; DUPLICATE_CLAIM → lookback window days. */
    @Column(name = "threshold_days")
    private Integer thresholdDays;

    /** Points added to the fraud score (0-100) when this rule triggers. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 255)
    private String description;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    public enum RuleType { DUPLICATE_CLAIM, EARLY_CLAIM_AFTER_ISSUANCE, DOCUMENT_MISMATCH, HIGH_CLAIM_FREQUENCY }
}
