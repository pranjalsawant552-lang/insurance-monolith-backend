package com.insurance.claims.repository;

import com.insurance.claims.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, UUID>, JpaSpecificationExecutor<Claim> {

    Optional<Claim> findByClaimNumberAndDeletedFalse(String claimNumber);

    Page<Claim> findByCompanyIdAndDeletedFalse(UUID companyId, Pageable pageable);

    Page<Claim> findByCompanyIdAndStatusAndDeletedFalse(UUID companyId, ClaimStatus status, Pageable pageable);

    Page<Claim> findByCustomerIdAndDeletedFalse(UUID customerId, Pageable pageable);

    Page<Claim> findByAssignedToAndDeletedFalse(UUID assignedTo, Pageable pageable);

    Page<Claim> findByCompanyIdAndInsuranceTypeAndDeletedFalse(UUID companyId, InsuranceType type, Pageable pageable);

    boolean existsByClaimNumber(String claimNumber);

    long countByCompanyIdAndStatusAndDeletedFalse(UUID companyId, ClaimStatus status);

    @Query("SELECT c FROM Claim c WHERE c.companyId = :cid AND c.fraudFlag = true AND c.deleted = false")
    Page<Claim> findFlaggedClaims(@Param("cid") UUID companyId, Pageable pageable);

    @Query("SELECT c FROM Claim c WHERE c.companyId = :cid AND c.slaBreached = true AND c.deleted = false")
    Page<Claim> findSlaBreachedClaims(@Param("cid") UUID companyId, Pageable pageable);

    /** Used by the scheduled SlaMonitor — claims past due that haven't yet been marked breached. */
    @Query("SELECT c FROM Claim c WHERE c.slaDueAt IS NOT NULL AND c.slaDueAt < :now AND c.slaBreached = false AND c.deleted = false")
    java.util.List<Claim> findNewlyBreachedClaims(@Param("now") java.time.ZonedDateTime now);
}
