package com.insurance.audit.repository;

import com.insurance.audit.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Deliberately extends the bare Spring Data {@link Repository} marker
 * interface — NOT JpaRepository/CrudRepository — and hand-picks only
 * save/find/count methods. There is no delete/deleteById/deleteAll method
 * anywhere on this interface, so it's a compile error for any future code
 * in this service to delete a row through the ORM. Combined with granting
 * the database user this service connects as INSERT + SELECT only (see the
 * Phase 2 deployment notes), audit rows are append-only in both the
 * application layer and the database layer.
 */
public interface AuditLogEntryRepository extends Repository<AuditLogEntry, UUID>,
        JpaSpecificationExecutor<AuditLogEntry> {

    AuditLogEntry save(AuditLogEntry entry);

    Optional<AuditLogEntry> findById(UUID id);

    Page<AuditLogEntry> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId, Pageable pageable);

    long count();
}

