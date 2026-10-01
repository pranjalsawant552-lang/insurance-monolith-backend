package com.insurance.risk.repository;

import com.insurance.risk.entity.FraudEvaluation;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/** Append-only — bare Repository marker, no delete method reachable. */
public interface FraudEvaluationRepository extends Repository<FraudEvaluation, UUID> {
    FraudEvaluation save(FraudEvaluation evaluation);
    Optional<FraudEvaluation> findById(UUID id);
    Optional<FraudEvaluation> findTopByClaimIdOrderByCreatedAtDesc(UUID claimId);
}
