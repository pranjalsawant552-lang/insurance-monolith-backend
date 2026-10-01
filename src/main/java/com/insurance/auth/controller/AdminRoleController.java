package com.insurance.auth.controller;

import com.insurance.auth.dto.request.CreatePermissionRequest;
import com.insurance.auth.dto.request.CreateRoleRequest;
import com.insurance.auth.dto.response.ApiResponse;
import com.insurance.auth.dto.response.PermissionResponse;
import com.insurance.auth.dto.response.RoleResponse;
import com.insurance.auth.entity.AuthAuditLog;
import com.insurance.auth.entity.Permission;
import com.insurance.auth.entity.Role;
import com.insurance.auth.service.AuditService;
import com.insurance.auth.service.RbacService;
import com.insurance.auth.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RBAC administration: lets a Super Admin see every role, see every
 * permission, and grant/revoke permissions on a role — the "manage roles
 * without a code deploy" capability called for in the project brief.
 *
 * Deliberately Super-Admin-only (not shared with plain ADMIN): the ability
 * to edit what any role can do is itself one of the most sensitive actions
 * in the system, so it is gated on ADMIN_ROLE_MANAGE which only SUPER_ADMIN
 * is seeded with (see V2__rbac_roles_permissions.sql).
 */
@RestController
@RequestMapping("/admin/rbac")
@RequiredArgsConstructor
@Tag(name = "Admin – RBAC Management", description = "Manage roles, permissions, and role-permission grants")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminRoleController {

    private final RbacService rbacService;
    private final AuditService auditService;
    private final RequestUtil requestUtil;
    private final com.insurance.auth.event.AuditEventPublisher auditEventPublisher;

    @GetMapping("/roles")
    @Operation(summary = "List all roles with their currently granted permission codes")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles() {
        List<RoleResponse> roles = rbacService.listRoles().stream()
                .map(r -> RoleResponse.from(r, rbacService.getPermissionCodesForRole(r.getCode())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @GetMapping("/roles/{code}")
    @Operation(summary = "Get one role with its granted permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> getRole(@PathVariable String code) {
        Role role = rbacService.getRoleByCode(code.toUpperCase());
        RoleResponse response = RoleResponse.from(role, rbacService.getPermissionCodesForRole(role.getCode()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/roles")
    @Operation(summary = "Create a custom (non-system) role")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest req,
            HttpServletRequest request) {

        UUID actorId = currentUserId(request);
        Role role = rbacService.createCustomRole(req.getCode(), req.getName(), req.getDescription(), actorId);

        auditService.log(AuthAuditLog.ROLE_CREATED, actorId,
                requestUtil.extractIpAddress(request), requestUtil.extractUserAgent(request),
                Map.of("roleCode", role.getCode()));

        return ResponseEntity.ok(ApiResponse.success(
                RoleResponse.from(role, List.of()), "Role created."));
    }

    @GetMapping("/permissions")
    @Operation(summary = "List the full permission catalog")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> listPermissions() {
        List<PermissionResponse> permissions = rbacService.listPermissions().stream()
                .map(PermissionResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    @PostMapping("/permissions")
    @Operation(summary = "Register a new permission code (for a capability not yet in the catalog)")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody CreatePermissionRequest req,
            HttpServletRequest request) {

        UUID actorId = currentUserId(request);
        Permission permission = rbacService.createPermission(req.getCode(), req.getModule(), req.getDescription());

        auditService.log(AuthAuditLog.PERMISSION_CREATED, actorId,
                requestUtil.extractIpAddress(request), requestUtil.extractUserAgent(request),
                Map.of("permissionCode", permission.getCode()));

        return ResponseEntity.ok(ApiResponse.success(
                PermissionResponse.from(permission), "Permission created."));
    }

    @PostMapping("/roles/{roleCode}/permissions/{permissionCode}")
    @Operation(summary = "Grant a permission to a role — takes effect on the role's next issued token")
    public ResponseEntity<ApiResponse<Void>> grantPermission(
            @PathVariable String roleCode,
            @PathVariable String permissionCode,
            HttpServletRequest request) {

        UUID actorId = currentUserId(request);
        rbacService.grantPermission(roleCode.toUpperCase(), permissionCode.toUpperCase(), actorId);

        auditService.log(AuthAuditLog.ROLE_PERMISSION_GRANTED, actorId,
                requestUtil.extractIpAddress(request), requestUtil.extractUserAgent(request),
                Map.of("roleCode", roleCode.toUpperCase(), "permissionCode", permissionCode.toUpperCase()));
        auditEventPublisher.publish("ROLE_PERMISSION", roleCode.toUpperCase() + ":" + permissionCode.toUpperCase(), "GRANT",
                null,
                Map.of("roleCode", roleCode.toUpperCase(), "permissionCode", permissionCode.toUpperCase()),
                "Permission " + permissionCode.toUpperCase() + " granted to role " + roleCode.toUpperCase());

        return ResponseEntity.ok(ApiResponse.success(null,
                "Permission granted. Already-issued tokens keep their old permission set until they refresh."));
    }

    @DeleteMapping("/roles/{roleCode}/permissions/{permissionCode}")
    @Operation(summary = "Revoke a permission from a role — takes effect on the role's next issued token")
    public ResponseEntity<ApiResponse<Void>> revokePermission(
            @PathVariable String roleCode,
            @PathVariable String permissionCode,
            HttpServletRequest request) {

        UUID actorId = currentUserId(request);
        rbacService.revokePermission(roleCode.toUpperCase(), permissionCode.toUpperCase());

        auditService.log(AuthAuditLog.ROLE_PERMISSION_REVOKED, actorId,
                requestUtil.extractIpAddress(request), requestUtil.extractUserAgent(request),
                Map.of("roleCode", roleCode.toUpperCase(), "permissionCode", permissionCode.toUpperCase()));
        auditEventPublisher.publish("ROLE_PERMISSION", roleCode.toUpperCase() + ":" + permissionCode.toUpperCase(), "REVOKE",
                Map.of("roleCode", roleCode.toUpperCase(), "permissionCode", permissionCode.toUpperCase()),
                null,
                "Permission " + permissionCode.toUpperCase() + " revoked from role " + roleCode.toUpperCase());

        return ResponseEntity.ok(ApiResponse.success(null,
                "Permission revoked. Already-issued tokens keep their old permission set until they refresh."));
    }

    private UUID currentUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return userId != null ? UUID.fromString(userId) : null;
    }
}
