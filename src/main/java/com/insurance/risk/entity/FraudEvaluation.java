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
 * One row per automated fraud check run against a claim at submission time.
 * Append-only. claims-service reads the score/triggeredRules back to
 * auto-populate its own fraudScore/fraudFlag fields — a claims officer can
 * still override manually (that manual path from Phase 1/2 is unchanged).
 */
@Entity
@Table(name = "fraud_evaluations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FraudEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "fraud_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal fraudScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "triggered_rules", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> triggeredRules;

    @Column(name = "recommend_flag", nullable = false)
    private boolean recommendFlag;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
