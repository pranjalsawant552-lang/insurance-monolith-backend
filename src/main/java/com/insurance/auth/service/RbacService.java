package com.insurance.auth.service;

import com.insurance.auth.entity.Permission;
import com.insurance.auth.entity.Role;
import com.insurance.auth.entity.RolePermission;
import com.insurance.auth.entity.UserRole;
import com.insurance.auth.exception.AuthException;
import com.insurance.auth.repository.PermissionRepository;
import com.insurance.auth.repository.RolePermissionRepository;
import com.insurance.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Central place for reading/editing what a role can do.
 *
 * This service is used by the RBAC admin API and by AuthService
 * when permissions are calculated during login/token generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;


    // =============================================================
    // ROLE PERMISSIONS
    // =============================================================

    @Cacheable(
            value = "rolePermissions",
            key = "#roleCode"
    )
    @Transactional(readOnly = true)
    public List<String> getPermissionCodesForRole(String roleCode) {

        return rolePermissionRepository
                .findPermissionCodesByRoleCode(roleCode);
    }


    // =============================================================
    // LIST ROLES
    // =============================================================

    @Transactional(readOnly = true)
    public List<Role> listRoles() {

        return roleRepository
                .findByActiveTrueOrderByNameAsc();
    }


    // =============================================================
    // GET ROLE
    // =============================================================

    @Transactional(readOnly = true)
    public Role getRoleByCode(String code) {

        return roleRepository
                .findByCode(code)
                .orElseThrow(() ->
                        new AuthException(
                                "Role not found: " + code,
                                "ROLE_NOT_FOUND",
                                HttpStatus.NOT_FOUND
                        )
                );
    }


    // =============================================================
    // LIST PERMISSIONS
    // =============================================================

    @Transactional(readOnly = true)
    public List<Permission> listPermissions() {

        return permissionRepository
                .findAllByOrderByModuleAscCodeAsc();
    }


    // =============================================================
    // CREATE CUSTOM ROLE
    // =============================================================

    @Transactional
    public Role createCustomRole(
            String code,
            String name,
            String description,
            UUID createdBy) {

        if (code == null || code.isBlank()) {
            throw new AuthException(
                    "Role code is required",
                    "INVALID_ROLE_CODE",
                    HttpStatus.BAD_REQUEST
            );
        }

        String normalized = code
                .trim()
                .toUpperCase()
                .replace(" ", "_");

        if (roleRepository.existsByCode(normalized)) {
            throw new AuthException(
                    "A role with code " + normalized + " already exists",
                    "ROLE_ALREADY_EXISTS",
                    HttpStatus.CONFLICT
            );
        }

        Role role = Role.builder()
                .code(normalized)
                .name(name)
                .description(description)
                .systemRole(false)
                .active(true)
                .createdBy(createdBy)
                .build();

        return roleRepository.save(role);
    }


    // =============================================================
    // CREATE PERMISSION
    // =============================================================

    @Transactional
    public Permission createPermission(
            String code,
            String module,
            String description) {

        if (code == null || code.isBlank()) {
            throw new AuthException(
                    "Permission code is required",
                    "INVALID_PERMISSION_CODE",
                    HttpStatus.BAD_REQUEST
            );
        }

        String normalized = code
                .trim()
                .toUpperCase()
                .replace(" ", "_");

        if (permissionRepository.existsByCode(normalized)) {
            throw new AuthException(
                    "A permission with code " + normalized + " already exists",
                    "PERMISSION_ALREADY_EXISTS",
                    HttpStatus.CONFLICT
            );
        }

        Permission permission = Permission.builder()
                .code(normalized)
                .module(
                        module == null
                                ? null
                                : module.trim().toUpperCase()
                )
                .description(description)
                .systemPermission(false)
                .build();

        return permissionRepository.save(permission);
    }


    // =============================================================
    // GRANT PERMISSION
    // =============================================================

    @CacheEvict(
            value = "rolePermissions",
            key = "#roleCode"
    )
    @Transactional
    public void grantPermission(
            String roleCode,
            String permissionCode,
            UUID grantedBy) {

        Role role = getRoleByCode(roleCode);

        Permission permission = permissionRepository
                .findByCode(permissionCode)
                .orElseThrow(() ->
                        new AuthException(
                                "Permission not found: " + permissionCode,
                                "PERMISSION_NOT_FOUND",
                                HttpStatus.NOT_FOUND
                        )
                );

        if (rolePermissionRepository
                .existsByRoleIdAndPermissionId(
                        role.getId(),
                        permission.getId())) {

            // Already granted - no operation required.
            return;
        }

        RolePermission grant = RolePermission.builder()
                .role(role)
                .permission(permission)
                .grantedBy(grantedBy)
                .build();

        rolePermissionRepository.save(grant);

        log.info(
                "Permission {} granted to role {} by {}",
                permissionCode,
                roleCode,
                grantedBy
        );
    }


    // =============================================================
    // REVOKE PERMISSION
    // =============================================================

    @CacheEvict(
            value = "rolePermissions",
            key = "#roleCode"
    )
    @Transactional
    public void revokePermission(
            String roleCode,
            String permissionCode) {

        Role role = getRoleByCode(roleCode);

        Permission permission = permissionRepository
                .findByCode(permissionCode)
                .orElseThrow(() ->
                        new AuthException(
                                "Permission not found: " + permissionCode,
                                "PERMISSION_NOT_FOUND",
                                HttpStatus.NOT_FOUND
                        )
                );

        rolePermissionRepository
                .deleteByRoleIdAndPermissionId(
                        role.getId(),
                        permission.getId()
                );

        log.info(
                "Permission {} revoked from role {}",
                permissionCode,
                roleCode
        );
    }


    // =============================================================
    // SYSTEM ROLE GUARD
    // =============================================================

    /**
     * System roles represented by UserRole cannot be deleted
     * or renamed.
     *
     * Their permissions can still be modified.
     */
    public void assertMutable(Role role) {

        if (role == null) {
            throw new AuthException(
                    "Role cannot be null",
                    "ROLE_NOT_FOUND",
                    HttpStatus.NOT_FOUND
            );
        }

        if (role.isSystemRole()) {

            boolean isKnownEnumRole = false;

            for (UserRole userRole : UserRole.values()) {

                if (userRole.name().equals(role.getCode())) {
                    isKnownEnumRole = true;
                    break;
                }
            }

            if (isKnownEnumRole) {

                throw new AuthException(
                        "System role " + role.getCode()
                                + " cannot be deleted or renamed, "
                                + "only its permissions can be edited",
                        "SYSTEM_ROLE_IMMUTABLE",
                        HttpStatus.FORBIDDEN
                );
            }
        }
    }
}