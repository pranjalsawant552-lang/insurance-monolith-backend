package com.insurance.auth.repository;

import com.insurance.auth.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCode(String code);
    List<Permission> findByModuleOrderByCodeAsc(String module);
    List<Permission> findAllByOrderByModuleAscCodeAsc();
    boolean existsByCode(String code);
}
