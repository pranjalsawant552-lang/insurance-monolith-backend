package com.insurance.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_schedule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BillingSchedule {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_id", nullable = false)    private UUID policyId;
    @Column(name = "policy_number", nullable = false) private String policyNumber;
    @Column(name = "customer_id", nullable = false)  private UUID customerId;
    @Column(name = "company_id", nullable = false)   private UUID companyId;

    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Builder.Default @Column private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default private BillingStatus status = BillingStatus.PENDING;

    @Column(name = "installment_no", nullable = false) private int installmentNo;
    @Column(name = "paid_at") private ZonedDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private PaymentTransaction transaction;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
}
