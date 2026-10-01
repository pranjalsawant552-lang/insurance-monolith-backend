package com.insurance.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "life_policy_details")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LifePolicyDetail {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "policy_id") private Policy policy;
    @Column(name = "plan_type") private String planType;
    @Column(name = "death_benefit",    precision = 15, scale = 2) private BigDecimal deathBenefit;
    @Column(name = "survival_benefit", precision = 15, scale = 2) private BigDecimal survivalBenefit;
    @Column(name = "bonus_accrued",    precision = 12, scale = 2)
    @Builder.Default private BigDecimal bonusAccrued = BigDecimal.ZERO;
}
