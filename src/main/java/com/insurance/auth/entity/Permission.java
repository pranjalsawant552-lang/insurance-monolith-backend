package com.insurance.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A single granular capability in the system, e.g. POLICY_APPROVE, CLAIM_SETTLE.
 * Permissions are seeded by migration but can also be created by a Super Admin
 * through the admin API, so new capabilities can be introduced without a
 * schema migration.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Stable machine-readable code, e.g. POLICY_APPROVE. Used in @PreAuthorize checks. */
    @Column(nullable = false, unique = true, length = 100)
    private String code;

    /** Logical grouping for the admin UI, e.g. POLICY, CLAIMS, PAYMENTS, AGENT, DOCUMENT, CUSTOMER, ADMIN. */
    @Column(nullable = false, length = 50)
    private String module;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "is_system_permission", nullable = false)
    @Builder.Default
    private boolean systemPermission = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
