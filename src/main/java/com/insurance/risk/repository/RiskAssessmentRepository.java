package com.insurance.risk.repository;

import com.insurance.risk.entity.RiskAssessment;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/** Append-only — bare Repository marker, no delete method reachable. Same pattern as audit-service's repo (Phase 2). */
public interface RiskAssessmentRepository extends Repository<RiskAssessment, UUID> {
    RiskAssessment save(RiskAssessment assessment);
    Optional<RiskAssessment> findById(UUID id);
    Optional<RiskAssessment> findTopByPolicyIdOrderByCreatedAtDesc(UUID policyId);
}
