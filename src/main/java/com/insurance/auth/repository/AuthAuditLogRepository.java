package com.insurance.auth.repository;

import com.insurance.auth.entity.AuthAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID> {

    Page<AuthAuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<AuthAuditLog> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);
}
