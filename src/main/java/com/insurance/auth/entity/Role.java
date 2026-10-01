package com.insurance.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A manageable role. One row exists per {@link UserRole} enum value (system roles,
 * cannot be deleted, name is fixed) plus any custom roles a Super Admin creates
 * later (isSystemRole = false, can be freely renamed/removed).
 *
 * The enum on {@link User#getRole()} remains the source of truth for which
 * Role a user is assigned (keeps the JWT "role" claim simple and stable);
 * this table is what makes *what a role can do* editable without a deploy —
 * admins edit {@link RolePermission} rows against a Role's {@code code}.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Matches a UserRole enum name for system roles (e.g. "CLAIMS_OFFICER"), or a custom upper-snake-case name. */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "is_system_role", nullable = false)
    @Builder.Default
    private boolean systemRole = true;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
