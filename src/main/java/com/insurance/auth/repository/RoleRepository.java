package com.insurance.auth.repository;

import com.insurance.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);
    List<Role> findByActiveTrueOrderByNameAsc();
    boolean existsByCode(String code);
}
