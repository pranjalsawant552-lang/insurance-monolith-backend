package com.insurance.risk.service;

import com.insurance.risk.dto.request.RiskAssessmentRequest;
import com.insurance.risk.entity.RiskAssessment;
import com.insurance.risk.entity.RiskFactorConfig;
import com.insurance.risk.repository.RiskAssessmentRepository;
import com.insurance.risk.repository.RiskFactorConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes a 0-100 underwriting risk score from whatever {@link RiskFactorConfig}
 * rows are active for the given insurance line, then applies a configurable
 * threshold to recommend AUTO_APPROVE / MANUAL_REVIEW / AUTO_DECLINE. This is
 * what feeds policy-service's UNDERWRITING state (Phase 4) — replacing the
 * "no risk assessment exists anywhere" gap flagged in the original audit.
 *
 * Deliberately data-driven, same "pluggable, not hardcoded if/else" principle
 * as product-service's PremiumEngineService (Phase 3): an underwriting team
 * changes what drives risk by editing RiskFactorConfig rows, not by asking a
 * developer to change this class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final RiskFactorConfigRepository factorConfigRepository;
    private final RiskAssessmentRepository assessmentRepository;
    private final com.insurance.risk.event.AuditEventPublisher auditEventPublisher;

    // Thresholds are intentionally simple constants here rather than another
    // config table — three lines of business, three well-understood bands.
    // If per-product thresholds are needed later, this is the one place to change.
    private static final BigDecimal AUTO_APPROVE_MAX = new BigDecimal("35");
    private static final BigDecimal AUTO_DECLINE_MIN = new BigDecimal("80");

    @Transactional
    public RiskAssessment assess(RiskAssessmentRequest req) {
        List<RiskFactorConfig> factors = factorConfigRepository.findByInsuranceTypeAndActiveTrue(req.insuranceType());

        BigDecimal score = BigDecimal.ZERO;
        List<Map<String, Object>> trail = new ArrayList<>();

        for (RiskFactorConfig f : factors) {
            if (!matches(f, req)) continue;
            score = score.add(f.getWeight());
            trail.add(Map.of(
                    "factorType", f.getFactorType().name(),
                    "label", f.getLabel() != null ? f.getLabel() : "",
                    "weight", f.getWeight().toString(),
                    "runningScore", score.toString()
            ));
        }

        // Clamp to 0-100
        if (score.compareTo(BigDecimal.ZERO) < 0) score = BigDecimal.ZERO;
        if (score.compareTo(new BigDecimal("100")) > 0) score = new BigDecimal("100");
        score = score.setScale(2, RoundingMode.HALF_UP);

        RiskAssessment.Decision decision;
        if (score.compareTo(AUTO_APPROVE_MAX) <= 0) decision = RiskAssessment.Decision.AUTO_APPROVE;
        else if (score.compareTo(AUTO_DECLINE_MIN) >= 0) decision = RiskAssessment.Decision.AUTO_DECLINE;
        else decision = RiskAssessment.Decision.MANUAL_REVIEW;

        RiskAssessment assessment = RiskAssessment.builder()
                .policyId(req.policyId())
                .customerId(req.customerId())
                .insuranceType(req.insuranceType())
                .productId(req.productId())
                .age(req.age())
                .bmi(req.bmi())
                .smoker(req.smoker())
                .preExistingCondition(req.preExistingCondition())
                .occupationClass(req.occupationClass())
                .sumAssured(req.sumAssured())
                .annualIncome(req.annualIncome())
                .priorClaimsCount(req.priorClaimsCount())
                .vehicleAge(req.vehicleAge())
                .vehicleType(req.vehicleType())
                .locationRiskZone(req.locationRiskZone())
                .riskScore(score)
                .factorsApplied(trail)
                .decision(decision)
                .requestedBy(req.requestedBy())
                .build();

        assessment = assessmentRepository.save(assessment);
        auditEventPublisher.publish("RISK_ASSESSMENT", assessment.getId().toString(), "CREATE",
                null,
                Map.of("policyId", String.valueOf(req.policyId()), "riskScore", score.toString(), "decision", decision.name()),
                "Risk assessed for policy " + req.policyId() + ": score " + score + " → " + decision);
        log.info("Risk assessment {} for policy {}: score={} decision={}",
                assessment.getId(), req.policyId(), score, decision);
        return assessment;
    }

    public RiskAssessment getOrThrow(java.util.UUID id) {
        return assessmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk assessment not found: " + id));
    }

    public java.util.Optional<RiskAssessment> getLatestForPolicy(java.util.UUID policyId) {
        return assessmentRepository.findTopByPolicyIdOrderByCreatedAtDesc(policyId);
    }

    private boolean matches(RiskFactorConfig f, RiskAssessmentRequest req) {
        return switch (f.getFactorType()) {
            case AGE -> req.age() != null && inRange(BigDecimal.valueOf(req.age()), f.getMinValue(), f.getMaxValue());
            case BMI -> req.bmi() != null && inRange(req.bmi(), f.getMinValue(), f.getMaxValue());
            case SMOKER -> Boolean.TRUE.equals(req.smoker()) && "TRUE".equalsIgnoreCase(f.getConditionValue());
            case PRE_EXISTING_CONDITION -> Boolean.TRUE.equals(req.preExistingCondition()) && "TRUE".equalsIgnoreCase(f.getConditionValue());
            case OCCUPATION_CLASS -> req.occupationClass() != null && req.occupationClass().equalsIgnoreCase(f.getConditionValue());
            case SUM_ASSURED_RATIO -> req.sumAssured() != null && req.annualIncome() != null && req.annualIncome().signum() > 0
                    && inRange(req.sumAssured().divide(req.annualIncome(), 4, RoundingMode.HALF_UP), f.getMinValue(), f.getMaxValue());
            case PRIOR_CLAIMS_COUNT -> req.priorClaimsCount() != null && inRange(BigDecimal.valueOf(req.priorClaimsCount()), f.getMinValue(), f.getMaxValue());
            case VEHICLE_AGE -> req.vehicleAge() != null && inRange(BigDecimal.valueOf(req.vehicleAge()), f.getMinValue(), f.getMaxValue());
            case VEHICLE_TYPE -> req.vehicleType() != null && req.vehicleType().equalsIgnoreCase(f.getConditionValue());
            case LOCATION_RISK_ZONE -> req.locationRiskZone() != null && req.locationRiskZone().equalsIgnoreCase(f.getConditionValue());
        };
    }

    private boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min != null && value.compareTo(min) < 0) return false;
        if (max != null && value.compareTo(max) > 0) return false;
        return true;
    }
}
