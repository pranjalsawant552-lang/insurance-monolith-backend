package com.insurance.policy.client;

import com.insurance.risk.dto.request.RiskAssessmentRequest;
import com.insurance.risk.entity.RiskAssessment;
import com.insurance.risk.service.RiskAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MONOLITH CONVERSION NOTE: originally an HTTP call to risk-fraud-service.
 * Now a direct, in-process call to RiskAssessmentService. The "unreachable
 * risk engine routes to manual underwriting review, never auto-approves"
 * safety behavior is preserved via the catch block, even though an
 * in-process call can now only fail on a genuine runtime exception, not a
 * network/availability issue.
 */
@Slf4j
@Component("policyRiskFraudServiceClient")
@RequiredArgsConstructor
public class RiskFraudServiceClient {

    private final RiskAssessmentService riskAssessmentService;

    public record RiskResult(UUID assessmentId, BigDecimal riskScore, String decision, List<Map<String, Object>> factorsApplied) {
        public static RiskResult unavailable() {
            return new RiskResult(null, null, "MANUAL_REVIEW", List.of());
        }
    }

    public RiskResult assess(UUID policyId, UUID customerId, String insuranceType, UUID productId,
                              Integer age, BigDecimal bmi, Boolean smoker, Boolean preExistingCondition,
                              String occupationClass, BigDecimal sumAssured, BigDecimal annualIncome,
                              Integer priorClaimsCount, Integer vehicleAge, String vehicleType,
                              String locationRiskZone, UUID requestedBy) {
        try {
            RiskAssessment assessment = riskAssessmentService.assess(new RiskAssessmentRequest(
                    policyId, customerId, insuranceType, productId, age, bmi, smoker, preExistingCondition,
                    occupationClass, sumAssured, annualIncome, priorClaimsCount, vehicleAge, vehicleType,
                    locationRiskZone, requestedBy));
            return new RiskResult(assessment.getId(), assessment.getRiskScore(),
                    assessment.getDecision().name(), assessment.getFactorsApplied());
        } catch (RuntimeException e) {
            log.error("Risk assessment failed for policy {} — routing to manual review: {}", policyId, e.getMessage());
            return RiskResult.unavailable();
        }
    }
}
