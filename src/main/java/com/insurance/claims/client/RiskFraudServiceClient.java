package com.insurance.claims.client;

import com.insurance.risk.dto.request.FraudEvaluationRequest;
import com.insurance.risk.entity.FraudEvaluation;
import com.insurance.risk.service.FraudRuleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MONOLITH CONVERSION NOTE:
 *
 * Originally this class called risk-fraud-service through HTTP.
 *
 * Since the application is now a monolith, it directly invokes
 * FraudRuleEngineService in the same JVM.
 *
 * Dependency direction:
 *
 * ClaimService
 *      ↓
 * RiskFraudServiceClient
 *      ↓
 * FraudRuleEngineService
 *
 * FraudRuleEngineService does NOT call ClaimService.
 *
 * This prevents circular dependencies.
 */
@Slf4j
@Component("claimsRiskFraudServiceClient")
@RequiredArgsConstructor
public class RiskFraudServiceClient {

    private final FraudRuleEngineService fraudRuleEngineService;

    public record FraudResult(
            BigDecimal fraudScore,
            boolean recommendFlag,
            List<Map<String, Object>> triggeredRules
    ) {

        public static FraudResult none() {
            return new FraudResult(
                    BigDecimal.ZERO,
                    false,
                    List.of()
            );
        }
    }

    /**
     * Executes fraud evaluation.
     *
     * Fraud evaluation intentionally uses soft-fail behavior.
     * If the fraud engine fails unexpectedly, claim submission
     * continues without a fraud score.
     */
    public FraudResult evaluate(
            UUID claimId,
            UUID policyId,
            String policyNumber,
            UUID customerId,
            LocalDate incidentDate,
            UUID requestedBy
    ) {

        try {

            FraudEvaluation evaluation =
                    fraudRuleEngineService.evaluate(
                            new FraudEvaluationRequest(
                                    claimId,
                                    policyId,
                                    policyNumber,
                                    customerId,
                                    incidentDate
                            )
                    );

            return new FraudResult(
                    evaluation.getFraudScore(),
                    evaluation.isRecommendFlag(),
                    evaluation.getTriggeredRules()
            );

        } catch (RuntimeException e) {

            log.error(
                    "Fraud evaluation failed for claim {} — proceeding unscored: {}",
                    claimId,
                    e.getMessage(),
                    e
            );

            return FraudResult.none();
        }
    }
}