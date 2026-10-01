package com.insurance.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentTransaction {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "transaction_number", nullable = false, unique = true)
    private String transactionNumber;

    @Column(name = "policy_id", nullable = false)    private UUID policyId;
    @Column(name = "policy_number", nullable = false) private String policyNumber;
    @Column(name = "customer_id", nullable = false)  private UUID customerId;
    @Column(name = "company_id", nullable = false)   private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Builder.Default @Column private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "payment_method") private String paymentMethod;
    @Column(name = "gateway_name")   private String gatewayName;
    @Column(name = "gateway_order_id")   private String gatewayOrderId;
    @Column(name = "gateway_payment_id") private String gatewayPaymentId;
    @Column(name = "gateway_signature")  private String gatewaySignature;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_response", columnDefinition = "jsonb")
    private Map<String, Object> gatewayResponse;

    @Column(name = "bank_account_number") private String bankAccountNumber;
    @Column(name = "ifsc_code")           private String ifscCode;
    @Column(name = "utr_number")          private String utrNumber;
    @Column                               private String description;
    @Column(name = "failure_reason")      private String failureReason;

    @Column(name = "premium_period_from") private LocalDate premiumPeriodFrom;
    @Column(name = "premium_period_to")   private LocalDate premiumPeriodTo;
    @Column(name = "premium_due_date")    private LocalDate premiumDueDate;
    @Builder.Default @Column(name = "is_late_payment") private boolean latePayment = false;
    @Builder.Default @Column(name = "late_fee", precision = 8, scale = 2)
    private BigDecimal lateFee = BigDecimal.ZERO;

    /** Set only on TransactionType.REFUND rows — links back to the original PREMIUM/CLAIM_SETTLEMENT transaction being refunded. */
    @Column(name = "original_transaction_id")
    private UUID originalTransactionId;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "is_deleted") @Builder.Default private boolean deleted = false;
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
    @Column(name = "updated_at", nullable = false)
    @Builder.Default private ZonedDateTime updatedAt = ZonedDateTime.now();
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "processed_at") private ZonedDateTime processedAt;

    @PreUpdate public void preUpdate() { this.updatedAt = ZonedDateTime.now(); }
}
