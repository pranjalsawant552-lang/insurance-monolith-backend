package com.insurance.auth.repository;

import com.insurance.auth.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleId(UUID roleId);

    @Query("select rp.permission.code from RolePermission rp where rp.role.code = :roleCode")
    List<String> findPermissionCodesByRoleCode(@Param("roleCode") String roleCode);

    void deleteByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
}
