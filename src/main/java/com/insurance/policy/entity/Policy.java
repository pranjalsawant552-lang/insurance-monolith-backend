package com.insurance.policy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "policies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    // Nullable as of the PDF-ingestion pivot (Phase 9): an ingested policy was sold by
    // an outside insurer and very likely has no row in this platform's own product
    // catalog at all. Still populated for the legacy self-issuance path.
    @Column(name = "product_id")
    private UUID productId;

    /** Denormalized from product-service at issuance time — see ProductServiceClient.
     *  This service no longer keeps its own copy of the product catalog (that used to
     *  be a local, never-synced "insurance_products" table — a real data-consistency
     *  bug found while building Phase 3; product-service is now the single source of
     *  truth and these three fields are just a display-friendly snapshot). */
    @Column(name = "product_code")
    private String productCode;

    @Column(name = "product_name")
    private String productName;

    /** The exact product_versions row this policy was priced and issued under —
     *  editing the live product later never changes what this policy's terms were. */
    @Column(name = "product_version")
    private Integer productVersion;

    /** References product-service's premium_calculation_audits row — the full
     *  factor-by-factor breakdown of how premiumAmount was derived is retrievable
     *  there via GET /products/premium-calculations/{id} for disputes/audits. */
    @Column(name = "premium_calculation_id")
    private UUID premiumCalculationId;

    /** References risk-fraud-service's risk_assessments.id — the full underwriting score breakdown. */
    @Column(name = "risk_assessment_id")
    private UUID riskAssessmentId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type", nullable = false)
    private InsuranceType insuranceType;

    @Column(name = "sum_assured", nullable = false, precision = 15, scale = 2)
    private BigDecimal sumAssured;

    @Column(name = "premium_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal premiumAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "premium_frequency", nullable = false)
    private PremiumFrequency premiumFrequency;

    @Column(name = "policy_term_years", nullable = false)
    private int policyTermYears;

    @Column(name = "commencement_date", nullable = false)
    private LocalDate commencementDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "last_paid_date")
    private LocalDate lastPaidDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PolicyStatus status = PolicyStatus.PENDING;

    @Column(name = "total_premium_paid", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalPremiumPaid = BigDecimal.ZERO;

    @Column(name = "loan_outstanding", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal loanOutstanding = BigDecimal.ZERO;

    @Column(name = "grace_period_days")
    @Builder.Default
    private int gracePeriodDays = 30;

    @Column
    private String notes;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    // ── PDF-ingestion pivot (Phase 9) ─────────────────────────────────────
    /** The outside insurer that issued this policy (Star Health, HDFC ERGO, etc.).
     *  Always set for an ingested policy; null for the legacy self-issuance path. */
    @Column(name = "insurer_name")
    private String insurerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_source", nullable = false, length = 20)
    @Builder.Default
    private IngestionSource ingestionSource = IngestionSource.MANUAL;

    /** References document-service's extraction_results.id — the audit trail back to
     *  the original PDF and the reviewer who verified it. Null for MANUAL/SELF_ISSUED. */
    @Column(name = "source_extraction_id")
    private UUID sourceExtractionId;

    /** References document-service's documents.id — the original policy-schedule PDF,
     *  always kept as the source of truth per the client briefing. */
    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    /** Self-reference to the prior term's Policy row when this ingestion was auto-linked
     *  as a renewal (same customer + insurer + product, commencement close to the prior
     *  policy's maturity date). Null for a first-time policy. */
    @Column(name = "renewed_from_policy_id")
    private UUID renewedFromPolicyId;

    // Soft delete
    @Column(name = "is_deleted") @Builder.Default private boolean deleted = false;
    @Column(name = "deleted_at") private ZonedDateTime deletedAt;
    @Column(name = "deleted_by") private UUID deletedBy;

    // Audit
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
    @Column(name = "updated_at", nullable = false)
    @Builder.Default private ZonedDateTime updatedAt = ZonedDateTime.now();
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "issued_at") private ZonedDateTime issuedAt;
    @Column(name = "issued_by") private UUID issuedBy;

    // Relationships
    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PolicyRider> riders = new ArrayList<>();

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PolicyStatusHistory> statusHistory = new ArrayList<>();

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PolicyDocument> documents = new ArrayList<>();

    // Was a fully orphaned entity before Phase 9 — mapped to a real table with no
    // repository or write path anywhere in the service. Wired up as part of the
    // ingestion rework since nominee data is exactly the kind of field the PDF
    // extraction pipeline captures and a reviewer confirms.
    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<PolicyNominee> nominees = new ArrayList<>();

    @PreUpdate
    public void preUpdate() { this.updatedAt = ZonedDateTime.now(); }

    public boolean isActive() { return status == PolicyStatus.ACTIVE; }
    public boolean isLapsed() { return status == PolicyStatus.LAPSED; }
}
