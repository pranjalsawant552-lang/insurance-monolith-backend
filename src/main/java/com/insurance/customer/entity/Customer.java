package com.insurance.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Customer {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Column(name = "customer_code", nullable = false, unique = true)
    private String customerCode;

    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false)  private String lastName;
    @Column(name = "date_of_birth", nullable = false) private LocalDate dateOfBirth;
    @Column(nullable = false) private String gender;
    @Column(name = "marital_status") private String maritalStatus;
    @Column private String occupation;
    @Column(name = "annual_income", precision = 15, scale = 2) private BigDecimal annualIncome;
    @Column @Builder.Default private String nationality = "Indian";

    // Contact
    @Column(nullable = false) private String email;
    @Column(name = "mobile_number", nullable = false) private String mobileNumber;
    @Column(name = "alternate_mobile") private String alternateMobile;
    @Column(name = "alternate_email") private String alternateEmail;

    // KYC
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    @Builder.Default private KycStatus kycStatus = KycStatus.PENDING;
    @Column(name = "kyc_verified_at") private ZonedDateTime kycVerifiedAt;
    @Column(name = "kyc_verified_by") private UUID kycVerifiedBy;
    @Column(name = "kyc_rejection_reason") private String kycRejectionReason;

    // Identity
    // Identity — encrypted at rest using AES-256-GCM
    @Convert(converter = com.insurance.customer.config.EncryptedStringConverter.class)
    @Column(name = "pan_number") private String panNumber;
    @Convert(converter = com.insurance.customer.config.EncryptedStringConverter.class)
    @Column(name = "aadhaar_number") private String aadhaarNumber;
    @Convert(converter = com.insurance.customer.config.EncryptedStringConverter.class)
    @Column(name = "passport_number") private String passportNumber;
    @Convert(converter = com.insurance.customer.config.EncryptedStringConverter.class)
    @Column(name = "driving_license") private String drivingLicense;

    // Health
    @Column(name = "blood_group") private String bloodGroup;
    @Column(name = "height_cm", precision = 5, scale = 1) private BigDecimal heightCm;
    @Column(name = "weight_kg", precision = 5, scale = 1) private BigDecimal weightKg;
    @Builder.Default @Column private Boolean smoker = false;
    @Column(name = "pre_existing_conditions") private String preExistingConditions;

    // Multi-tenant
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "branch_id") private UUID branchId;
    @Column(name = "assigned_agent_id") private UUID assignedAgentId;

    // Soft delete & audit
    @Column(name = "is_deleted") @Builder.Default private boolean deleted = false;
    @Column(name = "deleted_at") private ZonedDateTime deletedAt;
    @Column(name = "deleted_by") private UUID deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
    @Column(name = "updated_at", nullable = false) @Builder.Default private ZonedDateTime updatedAt = ZonedDateTime.now();
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "updated_by") private UUID updatedBy;

    // Relationships
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default private List<CustomerAddress> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default private List<KycDocument> kycDocuments = new ArrayList<>();

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CustomerPreference preferences;

    @PreUpdate public void preUpdate() { this.updatedAt = ZonedDateTime.now(); }

    public String getFullName() { return firstName + " " + lastName; }
    public boolean isKycVerified() { return kycStatus == KycStatus.VERIFIED; }
}
