package com.insurance.agent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity @Table(name = "agent_commissions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentCommission {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Version private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false) private Agent agent;

    @Column(name = "policy_id",     nullable = false) private UUID policyId;
    @Column(name = "policy_number", nullable = false) private String policyNumber;
    @Column(name = "customer_id",   nullable = false) private UUID customerId;
    @Column(name = "company_id",    nullable = false) private UUID companyId;

    @Column(name = "insurance_type",  nullable = false) private String insuranceType;
    @Column(name = "commission_type", nullable = false) private String commissionType;

    @Column(name = "premium_amount",    nullable = false, precision = 12, scale = 2) private BigDecimal premiumAmount;
    @Column(name = "commission_rate",   nullable = false, precision = 5, scale = 2)  private BigDecimal commissionRate;
    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2) private BigDecimal commissionAmount;

    @Enumerated(EnumType.STRING)
    @Builder.Default @Column(nullable = false) private CommissionStatus status = CommissionStatus.PENDING;

    @Column(name = "payment_reference") private String paymentReference;
    @Column(name = "paid_at")           private ZonedDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false) @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
    @Column(name = "updated_at", nullable = false) @Builder.Default private ZonedDateTime updatedAt = ZonedDateTime.now();

    @PreUpdate public void preUpdate() { this.updatedAt = ZonedDateTime.now(); }
}
