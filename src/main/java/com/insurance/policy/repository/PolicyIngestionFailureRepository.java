package com.insurance.policy.repository;

import com.insurance.policy.entity.PolicyIngestionFailure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PolicyIngestionFailureRepository extends JpaRepository<PolicyIngestionFailure, UUID> {

    Page<PolicyIngestionFailure> findByCompanyIdAndStatus(
            UUID companyId, PolicyIngestionFailure.FailureStatus status, Pageable pageable);

    Page<PolicyIngestionFailure> findByCompanyId(UUID companyId, Pageable pageable);
}
