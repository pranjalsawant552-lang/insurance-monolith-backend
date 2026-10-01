package com.insurance.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "mobile_number", nullable = false, unique = true)
    private String mobileNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "company_id")
    private UUID companyId;

    // Verification
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "mobile_verified", nullable = false)
    @Builder.Default
    private boolean mobileVerified = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = false;

    // Lockout
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_at")
    private ZonedDateTime lockedAt;

    @Column(name = "locked_until")
    private ZonedDateTime lockedUntil;

    @Column(name = "lock_reason")
    private String lockReason;

    // MFA
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    @Builder.Default
    private boolean totpEnabled = false;

    // OAuth
    @Column(name = "oauth_provider")
    private String oauthProvider;

    @Column(name = "oauth_provider_id")
    private String oauthProviderId;

    // Password history — stored as JSON array of bcrypt hashes
    @Column(name = "password_history", columnDefinition = "TEXT")
    @Builder.Default
    private String passwordHistory = "[]";

    // Soft delete
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    // Audit
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "last_login_at")
    private ZonedDateTime lastLoginAt;

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    @Column(name = "password_changed_at")
    @Builder.Default
    private ZonedDateTime passwordChangedAt = ZonedDateTime.now();

    // Helpers
    public boolean isLocked() {
        if (lockedUntil == null) return false;
        return ZonedDateTime.now().isBefore(lockedUntil);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedAt = null;
        this.lockedUntil = null;
        this.lockReason = null;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
