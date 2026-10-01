package com.insurance.product.repository;

import com.insurance.product.entity.PremiumCalculationAudit;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Deliberately extends the bare Spring Data {@link Repository} marker, not
 * JpaRepository, so there is no delete method reachable on this interface —
 * consistent with the append-only pattern used by audit-service's own
 * repository (Phase 2). This table is a calculation audit trail; nothing in
 * this service should ever remove a row from it.
 */
public interface PremiumCalculationAuditRepository extends Repository<PremiumCalculationAudit, UUID> {

    PremiumCalculationAudit save(PremiumCalculationAudit audit);

    Optional<PremiumCalculationAudit> findById(UUID id);
}
