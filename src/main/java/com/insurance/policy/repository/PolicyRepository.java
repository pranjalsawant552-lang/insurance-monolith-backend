package com.insurance.policy.repository;

import com.insurance.policy.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;

@Repository
public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    Optional<Policy> findByPolicyNumberAndDeletedFalse(String policyNumber);

    Page<Policy> findByCustomerIdAndDeletedFalse(UUID customerId, Pageable pageable);

    Page<Policy> findByAgentIdAndDeletedFalse(UUID agentId, Pageable pageable);

    Page<Policy> findByCompanyIdAndDeletedFalse(UUID companyId, Pageable pageable);

    Page<Policy> findByCompanyIdAndStatusAndDeletedFalse(UUID companyId, PolicyStatus status, Pageable pageable);

    Page<Policy> findByCompanyIdAndInsuranceTypeAndDeletedFalse(UUID companyId, InsuranceType type, Pageable pageable);

    // Policies due for renewal in the next N days
    @Query("SELECT p FROM Policy p WHERE p.status = 'ACTIVE' AND p.nextDueDate BETWEEN :from AND :to AND p.deleted = false")
    List<Policy> findPoliciesDueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // Policies due on an exact date — used by the staged renewal-reminder milestones (60/45/30/15/7/1 days out)
    @Query("SELECT p FROM Policy p WHERE p.status = 'ACTIVE' AND p.nextDueDate = :dueDate AND p.deleted = false")
    List<Policy> findPoliciesDueOn(@Param("dueDate") LocalDate dueDate);

    // Lapsed policies (active policies past due date + grace period with no payment)
    @Query("SELECT p FROM Policy p WHERE p.status = 'ACTIVE' AND p.nextDueDate < :graceCutoff AND p.deleted = false")
    List<Policy> findPoliciesPastGrace(@Param("graceCutoff") LocalDate graceCutoff);

    boolean existsByPolicyNumber(String policyNumber);

    // Kafka delivers "policy.document.verified" at-least-once — this guards against
    // creating a duplicate Policy if the same verified extraction is redelivered.
    boolean existsBySourceExtractionId(UUID sourceExtractionId);

    // Renewal auto-linking (Phase 9 ingestion): find the customer's most recent policy
    // with the same insurer + product name whose term is ending, to link a fresh
    // ingestion as a renewal via Policy.renewedFromPolicyId rather than a brand-new,
    // unrelated-looking record.
    @Query("SELECT p FROM Policy p WHERE p.customerId = :customerId AND p.deleted = false " +
           "AND LOWER(p.insurerName) = LOWER(:insurerName) AND LOWER(p.productName) = LOWER(:productName) " +
           "ORDER BY p.maturityDate DESC")
    List<Policy> findPriorTermsForRenewalMatch(@Param("customerId") UUID customerId,
                                                @Param("insurerName") String insurerName,
                                                @Param("productName") String productName);

    @Query("SELECT COUNT(p) FROM Policy p WHERE p.companyId = :companyId AND p.status = :status AND p.deleted = false")
    long countByCompanyIdAndStatus(@Param("companyId") UUID companyId, @Param("status") PolicyStatus status);
}
