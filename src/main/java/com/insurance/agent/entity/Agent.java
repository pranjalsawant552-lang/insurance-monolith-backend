package com.insurance.agent.entity;

import com.insurance.agent.config.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity @Table(name = "agents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Agent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "agent_code", nullable = false, unique = true) private String agentCode;
    @Column(name = "user_id") private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false) private AgentType agentType;

    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name",  nullable = false) private String lastName;
    @Column(nullable = false) private String email;
    @Column(name = "mobile_number", nullable = false) private String mobileNumber;
    @Column(name = "pan_number")     private String panNumber;
    @Column(name = "license_number") private String licenseNumber;
    @Column(name = "license_expiry") private LocalDate licenseExpiry;
    @Column private String qualification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id") private Agent manager;

    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "branch_id") private UUID branchId;
    @Column private String region;

    @Builder.Default @Column(name = "commission_rate_life",   precision = 5, scale = 2) private BigDecimal commissionRateLife   = new BigDecimal("30.00");
    @Builder.Default @Column(name = "commission_rate_health", precision = 5, scale = 2) private BigDecimal commissionRateHealth = new BigDecimal("15.00");
    @Builder.Default @Column(name = "commission_rate_motor",  precision = 5, scale = 2) private BigDecimal commissionRateMotor  = new BigDecimal("10.00");

    @Column(name = "bank_name")       private String bankName;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number")  private String accountNumber;
    @Column(name = "ifsc_code")       private String ifscCode;
    @Column(name = "account_holder")  private String accountHolder;

    @Enumerated(EnumType.STRING)
    @Builder.Default @Column(nullable = false) private AgentStatus status = AgentStatus.ACTIVE;

    @Column(name = "is_deleted") @Builder.Default private boolean deleted = false;
    @Column(name = "deleted_at") private ZonedDateTime deletedAt;
    @Column(name = "deleted_by") private UUID deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
    @Column(name = "updated_at", nullable = false) @Builder.Default private ZonedDateTime updatedAt = ZonedDateTime.now();
    @Column(name = "created_by", nullable = false) private UUID createdBy;

    @PreUpdate public void preUpdate() { this.updatedAt = ZonedDateTime.now(); }
    public String getFullName() { return firstName + " " + lastName; }
}
