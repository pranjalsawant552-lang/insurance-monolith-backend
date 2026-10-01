package com.insurance.product.service;

import com.insurance.product.entity.*;
import com.insurance.product.repository.PremiumCalculationAuditRepository;
import com.insurance.product.repository.RatingFactorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Replaces the hardcoded switch/case formula that used to live in both
 * product-service and policy-service (two independent copies of the same
 * logic — see the Phase 1 gap analysis, §4.3, and Phase 3's changelog for
 * how policy-service now delegates here instead of maintaining its own
 * copy). Reads whatever {@link RatingFactor} rows exist for a product
 * version and applies them in sequence — admins change pricing by editing
 * rows through RatingFactorController, not by asking a developer to change
 * this class.
 *
 * Every calculation is persisted to {@link PremiumCalculationAudit} before
 * being returned, satisfying the brief's explicit ask for an audit trail of
 * how a premium was derived (§4.3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PremiumEngineService {

    private final RatingFactorRepository ratingFactorRepository;
    private final PremiumCalculationAuditRepository auditRepository;

    private static final BigDecimal GST_RATE = new BigDecimal("0.18");
    private static final BigDecimal SUM_ASSURED_UNIT = new BigDecimal("100000"); // base premium is "per 1 lakh sum assured"

    public record CalculationInput(
            InsuranceProduct product,
            ProductVersion version,
            BigDecimal sumAssured,
            String frequency,
            int termYears,
            List<ProductRider> selectedRiders,
            Integer age,
            BigDecimal riskScore,
            String location,
            String vehicleType,
            PremiumCalculationAudit.CalculatedFor calculatedFor,
            String referenceId,
            UUID requestedBy
    ) {}

    @Transactional
    public PremiumCalculationAudit calculate(CalculationInput in) {
        List<RatingFactor> factors = ratingFactorRepository.findByProductVersionIdAndActiveTrue(in.version().getId());

        BigDecimal saFactor = in.sumAssured().divide(SUM_ASSURED_UNIT, 6, RoundingMode.HALF_UP);
        BigDecimal running = in.product().getBasePremium().multiply(saFactor).setScale(4, RoundingMode.HALF_UP);
        BigDecimal basePremiumRecorded = running.setScale(2, RoundingMode.HALF_UP);

        List<Map<String, Object>> trail = new ArrayList<>();
        trail.add(Map.of(
                "factorType", "BASE", "label", "Base premium × (sum assured / ₹1,00,000)",
                "adjustmentType", "SEED", "adjustmentValue", saFactor.toString(),
                "resultingAmount", running.toString()
        ));

        for (RatingFactor f : factors) {
            if (!matches(f, in)) continue;
            running = applyFactor(running, f);
            trail.add(Map.of(
                    "factorType", f.getFactorType().name(),
                    "label", f.getLabel() != null ? f.getLabel() : "",
                    "adjustmentType", f.getAdjustmentType().name(),
                    "adjustmentValue", f.getMultiplier().toString(),
                    "resultingAmount", running.setScale(2, RoundingMode.HALF_UP).toString()
            ));
        }

        // Frequency factor is always applied last — it converts an annual
        // premium into the installment amount for the chosen payment cadence.
        BigDecimal freqFactor = frequencyFactor(in.frequency());
        running = running.multiply(freqFactor).setScale(2, RoundingMode.HALF_UP);
        trail.add(Map.of(
                "factorType", "FREQUENCY", "label", in.frequency() + " installment factor",
                "adjustmentType", "MULTIPLIER", "adjustmentValue", freqFactor.toString(),
                "resultingAmount", running.toString()
        ));

        BigDecimal riderPremium = BigDecimal.ZERO;
        if (in.selectedRiders() != null) {
            for (ProductRider r : in.selectedRiders()) {
                if (r.isActive()) riderPremium = riderPremium.add(r.getAdditionalPremium());
            }
        }

        BigDecimal beforeGst = running.add(riderPremium).setScale(2, RoundingMode.HALF_UP);
        BigDecimal withGst = beforeGst.multiply(BigDecimal.ONE.add(GST_RATE)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gstAmount = withGst.subtract(beforeGst).setScale(2, RoundingMode.HALF_UP);

        PremiumCalculationAudit audit = PremiumCalculationAudit.builder()
                .productId(in.product().getId())
                .productVersionId(in.version().getId())
                .calculatedFor(in.calculatedFor())
                .referenceId(in.referenceId())
                .sumAssured(in.sumAssured())
                .frequency(in.frequency())
                .termYears(in.termYears())
                .age(in.age())
                .riskScore(in.riskScore())
                .location(in.location())
                .vehicleType(in.vehicleType())
                .riderIds(in.selectedRiders() != null
                        ? in.selectedRiders().stream().map(r -> r.getId().toString()).toList()
                        : List.of())
                .basePremium(basePremiumRecorded)
                .factorsApplied(trail)
                .riderPremium(riderPremium)
                .gstAmount(gstAmount)
                .totalPremium(withGst)
                .requestedBy(in.requestedBy())
                .build();

        audit = auditRepository.save(audit);
        log.debug("Premium calculated for product {} version {}: {} (audit {})",
                in.product().getCode(), in.version().getVersionNumber(), withGst, audit.getId());
        return audit;
    }

    public PremiumCalculationAudit getAuditOrThrow(UUID id) {
        return auditRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Premium calculation record not found: " + id));
    }

    private boolean matches(RatingFactor f, CalculationInput in) {
        return switch (f.getFactorType()) {
            case AGE_BAND -> in.age() != null && inRange(BigDecimal.valueOf(in.age()), f.getMinValue(), f.getMaxValue());
            case SUM_ASSURED_BAND -> inRange(in.sumAssured(), f.getMinValue(), f.getMaxValue());
            case TERM_BAND -> inRange(BigDecimal.valueOf(in.termYears()), f.getMinValue(), f.getMaxValue());
            case RISK_SCORE_BAND -> in.riskScore() != null && inRange(in.riskScore(), f.getMinValue(), f.getMaxValue());
            case VEHICLE_TYPE -> in.vehicleType() != null && in.vehicleType().equalsIgnoreCase(f.getConditionValue());
            case LOCATION -> in.location() != null && in.location().equalsIgnoreCase(f.getConditionValue());
        };
    }

    private boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min != null && value.compareTo(min) < 0) return false;
        if (max != null && value.compareTo(max) > 0) return false;
        return true;
    }

    private BigDecimal applyFactor(BigDecimal running, RatingFactor f) {
        return switch (f.getAdjustmentType()) {
            case MULTIPLIER -> running.multiply(f.getMultiplier());
            case FLAT_ADD -> running.add(f.getMultiplier());
            case PERCENT_ADD -> running.add(running.multiply(f.getMultiplier()).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        };
    }

    private BigDecimal frequencyFactor(String frequency) {
        return switch (frequency.toUpperCase()) {
            case "MONTHLY" -> new BigDecimal("0.09");
            case "QUARTERLY" -> new BigDecimal("0.26");
            case "HALF_YEARLY" -> new BigDecimal("0.51");
            case "YEARLY", "SINGLE" -> BigDecimal.ONE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown frequency: " + frequency);
        };
    }
}
