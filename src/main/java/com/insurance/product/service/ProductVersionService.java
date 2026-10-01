package com.insurance.product.service;

import com.insurance.product.entity.*;
import com.insurance.product.repository.ProductVersionRepository;
import com.insurance.product.repository.RatingFactorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

/**
 * Owns the "editing a product creates a new version, never mutates history"
 * rule described in the Phase 1 gap analysis (§4.2) and the Master Backlog
 * (B4/A4). Every product gets version 1 at creation; every subsequent
 * ProductService.update() call goes through {@link #createNewVersion} instead
 * of only mutating the live row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVersionService {

    private final ProductVersionRepository versionRepository;
    private final RatingFactorRepository ratingFactorRepository;

    @Transactional
    public ProductVersion createInitialVersion(InsuranceProduct product, UUID createdBy) {
        ProductVersion version = ProductVersion.builder()
                .productId(product.getId())
                .versionNumber(1)
                .status(ProductVersion.ProductVersionStatus.ACTIVE)
                .configSnapshot(snapshot(product))
                .createdBy(createdBy)
                .build();
        version = versionRepository.save(version);

        seedDefaultRatingFactors(product, version);

        log.info("Product version 1 created for {}", product.getCode());
        return version;
    }

    /**
     * Marks the currently-ACTIVE version SUPERSEDED and creates a new ACTIVE
     * version snapshotting the product's state AFTER the update has been
     * applied to the in-memory entity (caller applies field changes first,
     * then calls this so the new snapshot reflects the new values, while the
     * previous version row remains an untouched historical record of the old
     * values). Rating factors are copied forward from the previous version
     * so an edit doesn't silently wipe out an admin's rating configuration —
     * admins can then adjust the new version's factors independently.
     */
    @Transactional
    public ProductVersion createNewVersion(InsuranceProduct product, UUID updatedBy) {
        ProductVersion previous = versionRepository
                .findByProductIdAndStatus(product.getId(), ProductVersion.ProductVersionStatus.ACTIVE)
                .orElse(null);

        int nextNumber = (previous != null ? previous.getVersionNumber() : 0) + 1;

        if (previous != null) {
            previous.setStatus(ProductVersion.ProductVersionStatus.SUPERSEDED);
            previous.setEffectiveTo(java.time.ZonedDateTime.now());
            versionRepository.save(previous);
        }

        ProductVersion newVersion = ProductVersion.builder()
                .productId(product.getId())
                .versionNumber(nextNumber)
                .status(ProductVersion.ProductVersionStatus.ACTIVE)
                .configSnapshot(snapshot(product))
                .createdBy(updatedBy)
                .build();
        newVersion = versionRepository.save(newVersion);

        if (previous != null) {
            copyRatingFactors(previous.getId(), newVersion, product.getId());
        } else {
            seedDefaultRatingFactors(product, newVersion);
        }

        product.setCurrentVersion(nextNumber);
        log.info("Product {} updated to version {}", product.getCode(), nextNumber);
        return newVersion;
    }

    public ProductVersion getActiveVersion(UUID productId) {
        return versionRepository.findByProductIdAndStatus(productId, ProductVersion.ProductVersionStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active version found for product " + productId));
    }

    public ProductVersion getVersion(UUID productId, int versionNumber) {
        return versionRepository.findByProductIdAndVersionNumber(productId, versionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Version " + versionNumber + " not found for product " + productId));
    }

    public org.springframework.data.domain.Page<ProductVersion> listVersions(UUID productId, int page, int size) {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100));
        return versionRepository.findByProductIdOrderByVersionNumberDesc(productId, pageable);
    }

    private Map<String, Object> snapshot(InsuranceProduct p) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("code", p.getCode());
        s.put("name", p.getName());
        s.put("insuranceType", p.getInsuranceType().name());
        s.put("category", p.getCategory());
        s.put("description", p.getDescription());
        s.put("keyFeatures", p.getKeyFeatures());
        s.put("basePremium", p.getBasePremium() != null ? p.getBasePremium().toString() : null);
        s.put("minSumAssured", p.getMinSumAssured() != null ? p.getMinSumAssured().toString() : null);
        s.put("maxSumAssured", p.getMaxSumAssured() != null ? p.getMaxSumAssured().toString() : null);
        s.put("minAge", p.getMinAge());
        s.put("maxAge", p.getMaxAge());
        s.put("minTermYears", p.getMinTermYears());
        s.put("maxTermYears", p.getMaxTermYears());
        s.put("gracePeriodDays", p.getGracePeriodDays());
        s.put("freeLookDays", p.getFreeLookDays());
        List<Map<String, Object>> riders = new ArrayList<>();
        if (p.getRiders() != null) {
            p.getRiders().forEach(r -> riders.add(Map.of(
                    "id", r.getId().toString(),
                    "name", r.getName(),
                    "code", r.getCode(),
                    "additionalPremium", r.getAdditionalPremium().toString(),
                    "active", r.isActive()
            )));
        }
        s.put("riders", riders);
        return s;
    }

    private void copyRatingFactors(UUID fromVersionId, ProductVersion toVersion, UUID productId) {
        List<RatingFactor> existing = ratingFactorRepository.findByProductVersionId(fromVersionId);
        for (RatingFactor f : existing) {
            RatingFactor copy = RatingFactor.builder()
                    .productId(productId)
                    .productVersionId(toVersion.getId())
                    .factorType(f.getFactorType())
                    .minValue(f.getMinValue())
                    .maxValue(f.getMaxValue())
                    .conditionValue(f.getConditionValue())
                    .label(f.getLabel())
                    .description(f.getDescription())
                    .adjustmentType(f.getAdjustmentType())
                    .multiplier(f.getMultiplier())
                    .active(f.isActive())
                    .build();
            ratingFactorRepository.save(copy);
        }
    }

    /**
     * Sensible defaults so the engine works out of the box, matching the
     * behaviour the old hardcoded formula had (term-based discount) plus the
     * age banding the brief explicitly calls out as missing. Admins can edit
     * or remove any of these afterward through RatingFactorController — this
     * is a starting point, not a fixed rule set.
     */
    private void seedDefaultRatingFactors(InsuranceProduct product, ProductVersion version) {
        List<RatingFactor> defaults = new ArrayList<>();

        switch (product.getInsuranceType()) {
            case LIFE -> {
                defaults.add(termFactor(product, version, new BigDecimal("30"), null, "1.10 factor becomes 0.85x for 30yr+ term", "0.85"));
                defaults.add(termFactor(product, version, new BigDecimal("20"), new BigDecimal("29.99"), "20-29yr term", "0.90"));
                defaults.add(termFactor(product, version, new BigDecimal("10"), new BigDecimal("19.99"), "10-19yr term", "0.95"));
                defaults.add(ageBand(product, version, null, new BigDecimal("30"), "Age up to 30", "0.90"));
                defaults.add(ageBand(product, version, new BigDecimal("31"), new BigDecimal("45"), "Age 31-45", "1.00"));
                defaults.add(ageBand(product, version, new BigDecimal("46"), new BigDecimal("60"), "Age 46-60", "1.25"));
                defaults.add(ageBand(product, version, new BigDecimal("61"), null, "Age 60+", "1.60"));
            }
            case HEALTH -> {
                defaults.add(termFactor(product, version, new BigDecimal("3"), null, "3yr+ term", "0.95"));
                defaults.add(ageBand(product, version, null, new BigDecimal("35"), "Age up to 35", "0.95"));
                defaults.add(ageBand(product, version, new BigDecimal("36"), new BigDecimal("50"), "Age 36-50", "1.10"));
                defaults.add(ageBand(product, version, new BigDecimal("51"), null, "Age 51+", "1.45"));
            }
            case MOTOR -> {
                defaults.add(termFactor(product, version, new BigDecimal("3"), null, "3yr+ term", "0.90"));
                defaults.add(RatingFactor.builder().productId(product.getId()).productVersionId(version.getId())
                        .factorType(RatingFactor.FactorType.VEHICLE_TYPE).conditionValue("SUV")
                        .label("SUV").adjustmentType(RatingFactor.AdjustmentType.PERCENT_ADD)
                        .multiplier(new BigDecimal("8")).build());
                defaults.add(RatingFactor.builder().productId(product.getId()).productVersionId(version.getId())
                        .factorType(RatingFactor.FactorType.VEHICLE_TYPE).conditionValue("COMMERCIAL")
                        .label("Commercial vehicle").adjustmentType(RatingFactor.AdjustmentType.PERCENT_ADD)
                        .multiplier(new BigDecimal("20")).build());
            }
        }

        ratingFactorRepository.saveAll(defaults);
    }

    private RatingFactor termFactor(InsuranceProduct p, ProductVersion v, BigDecimal min, BigDecimal max, String label, String mult) {
        return RatingFactor.builder().productId(p.getId()).productVersionId(v.getId())
                .factorType(RatingFactor.FactorType.TERM_BAND).minValue(min).maxValue(max)
                .label(label).adjustmentType(RatingFactor.AdjustmentType.MULTIPLIER)
                .multiplier(new BigDecimal(mult)).build();
    }

    private RatingFactor ageBand(InsuranceProduct p, ProductVersion v, BigDecimal min, BigDecimal max, String label, String mult) {
        return RatingFactor.builder().productId(p.getId()).productVersionId(v.getId())
                .factorType(RatingFactor.FactorType.AGE_BAND).minValue(min).maxValue(max)
                .label(label).adjustmentType(RatingFactor.AdjustmentType.MULTIPLIER)
                .multiplier(new BigDecimal(mult)).build();
    }
}
