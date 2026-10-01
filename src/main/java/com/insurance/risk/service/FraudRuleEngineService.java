package com.insurance.risk.service;

import com.insurance.claims.entity.Claim;
import com.insurance.claims.repository.ClaimRepository;
import com.insurance.risk.client.PolicyServiceClient;
import com.insurance.risk.dto.request.FraudEvaluationRequest;
import com.insurance.risk.entity.FraudEvaluation;
import com.insurance.risk.entity.FraudRuleConfig;
import com.insurance.risk.repository.FraudEvaluationRepository;
import com.insurance.risk.repository.FraudRuleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Automated rule-based fraud checks run at claim submission time.
 *
 * MONOLITH CONVERSION:
 *
 * Originally the risk-fraud-service communicated with claims-service
 * over HTTP.
 *
 * Since the application is now a monolith, claim history is read
 * directly from ClaimRepository.
 *
 * This is intentional because FraudRuleEngineService must NOT depend
 * on ClaimService. ClaimService already depends on
 * RiskFraudServiceClient, and injecting ClaimService here would create
 * a circular dependency.
 *
 * Dependency flow:
 *
 * ClaimService
 *      ↓
 * RiskFraudServiceClient
 *      ↓
 * FraudRuleEngineService
 *      ↓
 * ClaimRepository
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudRuleEngineService {

    private final FraudRuleConfigRepository ruleConfigRepository;

    private final FraudEvaluationRepository evaluationRepository;

    private final PolicyServiceClient policyServiceClient;

    private final ClaimRepository claimRepository;

    private final com.insurance.risk.event.AuditEventPublisher auditEventPublisher;

    private static final int DEFAULT_DUPLICATE_LOOKBACK_DAYS = 30;

    private static final int DEFAULT_FREQUENCY_LOOKBACK_DAYS = 365;

    /**
     * Evaluates fraud rules for a claim.
     */
    @Transactional
    public FraudEvaluation evaluate(FraudEvaluationRequest req) {

        List<FraudRuleConfig> rules =
                ruleConfigRepository.findByActiveTrue();

        Map<FraudRuleConfig.RuleType, FraudRuleConfig> byType =
                rules.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        FraudRuleConfig::getRuleType,
                                        r -> r
                                )
                        );

        BigDecimal score = BigDecimal.ZERO;

        List<Map<String, Object>> triggered =
                new ArrayList<>();

        // ================================================================
        // Rule 1: Early claim after policy issuance
        // ================================================================

        FraudRuleConfig earlyClaimRule =
                byType.get(
                        FraudRuleConfig.RuleType
                                .EARLY_CLAIM_AFTER_ISSUANCE
                );

        if (earlyClaimRule != null) {

            Optional<LocalDate> commencementDate =
                    policyServiceClient.getPolicyCommencementDate(
                            req.policyId()
                    );

            if (commencementDate.isPresent()
                    && req.incidentDate() != null) {

                long daysSinceIssuance =
                        ChronoUnit.DAYS.between(
                                commencementDate.get(),
                                req.incidentDate()
                        );

                int threshold =
                        earlyClaimRule.getThresholdDays() != null
                                ? earlyClaimRule.getThresholdDays()
                                : 90;

                if (daysSinceIssuance >= 0
                        && daysSinceIssuance <= threshold) {

                    score = score.add(
                            earlyClaimRule.getWeight()
                    );

                    triggered.add(
                            Map.of(
                                    "rule",
                                    "EARLY_CLAIM_AFTER_ISSUANCE",

                                    "detail",
                                    "Incident occurred "
                                            + daysSinceIssuance
                                            + " days after policy commenced "
                                            + "(threshold: "
                                            + threshold
                                            + ")",

                                    "weight",
                                    earlyClaimRule
                                            .getWeight()
                                            .toString()
                            )
                    );
                }
            }
        }

        // ================================================================
        // Rule 2: Duplicate claim
        // ================================================================

        FraudRuleConfig duplicateRule =
                byType.get(
                        FraudRuleConfig.RuleType.DUPLICATE_CLAIM
                );

        if (duplicateRule != null
                && req.customerId() != null) {

            int lookback =
                    duplicateRule.getThresholdDays() != null
                            ? duplicateRule.getThresholdDays()
                            : DEFAULT_DUPLICATE_LOOKBACK_DAYS;

            List<Claim> recentClaims =
                    getRecentClaimsForCustomer(
                            req.customerId(),
                            lookback
                    );

            long overlapping =
                    recentClaims.stream()

                            /*
                             * Do not compare the claim currently being
                             * evaluated with itself.
                             */
                            .filter(
                                    claim ->
                                            !claim.getId()
                                                    .equals(req.claimId())
                            )

                            /*
                             * Duplicate claim means the claim belongs
                             * to the same policy.
                             */
                            .filter(
                                    claim ->
                                            claim.getPolicyNumber() != null
                                                    && claim.getPolicyNumber()
                                                    .equals(
                                                            req.policyNumber()
                                                    )
                            )

                            .count();

            if (overlapping > 0) {

                score = score.add(
                        duplicateRule.getWeight()
                );

                triggered.add(
                        Map.of(
                                "rule",
                                "DUPLICATE_CLAIM",

                                "detail",
                                overlapping
                                        + " other claim(s) on the same policy "
                                        + "within "
                                        + lookback
                                        + " days",

                                "weight",
                                duplicateRule
                                        .getWeight()
                                        .toString()
                        )
                );
            }
        }

        // ================================================================
        // Rule 3: High claim frequency
        // ================================================================

        FraudRuleConfig frequencyRule =
                byType.get(
                        FraudRuleConfig.RuleType.HIGH_CLAIM_FREQUENCY
                );

        if (frequencyRule != null
                && req.customerId() != null) {

            int lookback =
                    frequencyRule.getThresholdDays() != null
                            ? frequencyRule.getThresholdDays()
                            : DEFAULT_FREQUENCY_LOOKBACK_DAYS;

            List<Claim> recentClaims =
                    getRecentClaimsForCustomer(
                            req.customerId(),
                            lookback
                    );

            if (recentClaims.size() >= 3) {

                score = score.add(
                        frequencyRule.getWeight()
                );

                triggered.add(
                        Map.of(
                                "rule",
                                "HIGH_CLAIM_FREQUENCY",

                                "detail",
                                recentClaims.size()
                                        + " claims filed in the last "
                                        + lookback
                                        + " days",

                                "weight",
                                frequencyRule
                                        .getWeight()
                                        .toString()
                        )
                );
            }
        }

        // ================================================================
        // Rule 4: Document mismatch
        // ================================================================

        /*
         * Not implemented in this phase.
         *
         * Document-service OCR / identity matching data is not yet
         * wired into the fraud engine.
         *
         * Therefore this rule intentionally contributes zero.
         */

        // ================================================================
        // Maximum score = 100
        // ================================================================

        if (score.compareTo(new BigDecimal("100")) > 0) {
            score = new BigDecimal("100");
        }

        // ================================================================
        // Build evaluation
        // ================================================================

        FraudEvaluation evaluation =
                FraudEvaluation.builder()
                        .claimId(req.claimId())
                        .policyId(req.policyId())
                        .customerId(req.customerId())
                        .fraudScore(score)
                        .triggeredRules(triggered)
                        .recommendFlag(!triggered.isEmpty())
                        .build();

        evaluation =
                evaluationRepository.save(evaluation);

        // ================================================================
        // Audit
        // ================================================================

        auditEventPublisher.publish(
                "FRAUD_EVALUATION",
                evaluation.getId().toString(),
                "CREATE",
                null,
                Map.of(
                        "claimId",
                        String.valueOf(req.claimId()),

                        "fraudScore",
                        score.toString(),

                        "rulesTriggered",
                        String.valueOf(triggered.size())
                ),
                "Fraud evaluated for claim "
                        + req.claimId()
                        + ": score "
                        + score
                        + ", "
                        + triggered.size()
                        + " rule(s) triggered"
        );

        log.info(
                "Fraud evaluation {} for claim {}: score={} rulesTriggered={}",
                evaluation.getId(),
                req.claimId(),
                score,
                triggered.size()
        );

        return evaluation;
    }

    /**
     * Direct repository access for fraud analysis.
     *
     * IMPORTANT:
     * Do not replace this with ClaimService.
     *
     * ClaimService -> RiskFraudServiceClient ->
     * FraudRuleEngineService -> ClaimService
     *
     * would recreate the circular dependency.
     */
    private List<Claim> getRecentClaimsForCustomer(
            UUID customerId,
            int lookbackDays
    ) {

        Page<Claim> page =
                claimRepository.findByCustomerIdAndDeletedFalse(
                        customerId,
                        PageRequest.of(
                                0,
                                50,
                                Sort.by("createdAt").descending()
                        )
                );

        if (page.getContent() == null
                || page.getContent().isEmpty()) {
            return List.of();
        }

        LocalDate cutoff =
                LocalDate.now().minusDays(lookbackDays);

        return page.getContent()
                .stream()
                .filter(
                        claim ->
                                claim.getIncidentDate() == null
                                        || !claim.getIncidentDate()
                                        .isBefore(cutoff)
                )
                .toList();
    }

    /**
     * Gets a fraud evaluation or throws 404.
     */
    public FraudEvaluation getOrThrow(UUID id) {

        return evaluationRepository.findById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Fraud evaluation not found: " + id
                        )
                );
    }
}