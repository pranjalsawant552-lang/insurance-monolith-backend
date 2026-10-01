package com.insurance.claims.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity @Table(name = "claims")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Claim {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "claim_number", nullable = false, unique = true)
    private String claimNumber;

    @Column(name = "policy_id", nullable = false)   private UUID policyId;
    @Column(name = "policy_number", nullable = false) private String policyNumber;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "company_id", nullable = false)  private UUID companyId;
    @Column(name = "branch_id")                     private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type", nullable = false)
    private InsuranceType insuranceType;

    @Column(name = "claim_type", nullable = false) private String claimType;
    @Column(name = "incident_date", nullable = false) private LocalDate incidentDate;
    @Column(name = "reported_date", nullable = false) @Builder.Default private LocalDate reportedDate = LocalDate.now();
    @Column(nullable = false) private String description;

    @Column(name = "claimed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal claimedAmount;
    @Column(name = "approved_amount", precision = 15, scale = 2) private BigDecimal approvedAmount;
    @Column(name = "settled_amount",  precision = 15, scale = 2) private BigDecimal settledAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default private ClaimStatus status = ClaimStatus.SUBMITTED;

    @Column(name = "assigned_to") private UUID assignedTo;
    @Column(name = "surveyor_id") private UUID surveyorId;
    @Column(name = "rejection_reason") private String rejectionReason;
    @Column(name = "settlement_date")  private LocalDate settlementDate;
    @Column(name = "settlement_reference") private String settlementReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default private ClaimPriority priority = ClaimPriority.NORMAL;

    @Column(name = "fraud_flag")  @Builder.Default private boolean fraudFlag = false;
    @Column(name = "fraud_score", precision = 5, scale = 2) private BigDecimal fraudScore;

    /** When the CURRENT stage is due — recomputed on every status transition based on
     *  SlaConfig for (status, priority). Null once the claim reaches a terminal status. */
    @Column(name = "sla_due_at") private ZonedDateTime slaDueAt;
    @Column(name = "sla_breached") @Builder.Default private boolean slaBreached = false;

    @Column(name = "is_deleted")  @Builder.Default private boolean deleted = false;
    @Column(name = "deleted_at")  private ZonedDateTime deletedAt;
    @Column(name = "deleted_by")  private UUID deletedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
    @Column(name = "updated_at", nullable = false)
    @Builder.Default private ZonedDateTime updatedAt = ZonedDateTime.now();
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_by") private UUID updatedBy;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default private List<ClaimWorkflowHistory> workflowHistory = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default private List<ClaimDocument> documents = new ArrayList<>();

    @PreUpdate public void preUpdate() { this.updatedAt = ZonedDateTime.now(); }
}
