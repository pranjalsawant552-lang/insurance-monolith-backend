package com.insurance.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Join row: grants one Permission to one Role. This is the table an admin
 * edits (via /admin/roles/{roleCode}/permissions) to change what a role can
 * do — no code change or redeploy required.
 */
@Entity
@Table(name = "role_permissions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"role_id", "permission_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(name = "granted_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime grantedAt = ZonedDateTime.now();

    @Column(name = "granted_by")
    private UUID grantedBy;
}
