package com.insurance.product.service;

import com.insurance.product.dto.request.CreateProductRequest;
import com.insurance.product.dto.response.*;
import com.insurance.product.entity.*;
import com.insurance.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final com.insurance.product.event.AuditEventPublisher auditEventPublisher;
    private final ProductVersionService productVersionService;
    private final PremiumEngineService premiumEngineService;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse create(CreateProductRequest req, UUID companyId,
                                   UUID branchId, UUID createdBy) {
        if (productRepository.existsByCodeAndDeletedFalse(req.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A product with code '" + req.getCode() + "' already exists.");
        }

        InsuranceProduct product = InsuranceProduct.builder()
                .code(req.getCode().toUpperCase().trim())
                .name(req.getName().trim())
                .insuranceType(req.getInsuranceType())
                .category(req.getCategory())
                .description(req.getDescription())
                .keyFeatures(req.getKeyFeatures())
                .basePremium(req.getBasePremium())
                .minSumAssured(req.getMinSumAssured())
                .maxSumAssured(req.getMaxSumAssured())
                .minAge(req.getMinAge())
                .maxAge(req.getMaxAge())
                .minTermYears(req.getMinTermYears())
                .maxTermYears(req.getMaxTermYears())
                .gracePeriodDays(req.getGracePeriodDays() != null ? req.getGracePeriodDays() : 30)
                .freeLookDays(req.getFreeLookDays()    != null ? req.getFreeLookDays()    : 15)
                .featured(req.isFeatured())
                .companyId(companyId)
                .branchId(branchId)
                .createdBy(createdBy)
                .build();

        // Add riders
        if (req.getRiders() != null) {
            req.getRiders().forEach(r -> {
                ProductRider rider = ProductRider.builder()
                        .product(product)
                        .name(r.getName())
                        .code(r.getCode().toUpperCase())
                        .description(r.getDescription())
                        .additionalPremium(r.getAdditionalPremium())
                        .build();
                product.getRiders().add(rider);
            });
        }

        InsuranceProduct saved = productRepository.save(product);
        productVersionService.createInitialVersion(saved, createdBy);
        auditEventPublisher.publish("PRODUCT", saved.getId().toString(), "CREATE",
                null,
                java.util.Map.of("code", saved.getCode(), "name", saved.getName(),
                        "basePremium", saved.getBasePremium().toString(), "active", String.valueOf(saved.isActive())),
                "Product " + saved.getCode() + " created (version 1)");
        log.info("Product created: {} [{}]", saved.getCode(), saved.getId());
        return ProductResponse.from(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Cacheable(value = "products", key = "#id")
    public ProductResponse getById(UUID id) {
        return ProductResponse.from(findOrThrow(id));
    }

    @Cacheable(value = "products", key = "#code")
    public ProductResponse getByCode(String code) {
        return ProductResponse.from(productRepository.findByCodeAndDeletedFalse(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found: " + code)));
    }

    // ── List ──────────────────────────────────────────────────────────────────

    public PagedResponse<ProductResponse> list(UUID companyId, InsuranceType type,
                                               Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("name").ascending());
        Page<InsuranceProduct> result;
        if (type != null && active != null) {
            result = productRepository.findByCompanyIdAndInsuranceTypeAndDeletedFalse(
                    companyId, type, pageable);
        } else if (active != null) {
            result = productRepository.findByCompanyIdAndActiveAndDeletedFalse(
                    companyId, active, pageable);
        } else {
            result = productRepository.findByCompanyIdAndDeletedFalse(companyId, pageable);
        }
        return PagedResponse.of(result.map(ProductResponse::from));
    }

    public PagedResponse<ProductResponse> getFeatured(int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 20),
                Sort.by("name").ascending());
        return PagedResponse.of(productRepository
                .findByFeaturedAndActiveAndDeletedFalse(true, true, pageable)
                .map(ProductResponse::from));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse update(UUID id, CreateProductRequest req, UUID updatedBy) {
        InsuranceProduct p = findOrThrow(id);

        java.util.Map<String, Object> before = java.util.Map.of(
                "name", p.getName() != null ? p.getName() : "",
                "basePremium", p.getBasePremium() != null ? p.getBasePremium().toString() : "",
                "minSumAssured", p.getMinSumAssured() != null ? p.getMinSumAssured().toString() : "",
                "maxSumAssured", p.getMaxSumAssured() != null ? p.getMaxSumAssured().toString() : ""
        );

        if (req.getName()           != null) p.setName(req.getName());
        if (req.getDescription()    != null) p.setDescription(req.getDescription());
        if (req.getKeyFeatures()    != null) p.setKeyFeatures(req.getKeyFeatures());
        if (req.getBasePremium()    != null) p.setBasePremium(req.getBasePremium());
        if (req.getMinSumAssured()  != null) p.setMinSumAssured(req.getMinSumAssured());
        if (req.getMaxSumAssured()  != null) p.setMaxSumAssured(req.getMaxSumAssured());
        if (req.getMinAge()         != null) p.setMinAge(req.getMinAge());
        if (req.getMaxAge()         != null) p.setMaxAge(req.getMaxAge());
        if (req.getMinTermYears()   != null) p.setMinTermYears(req.getMinTermYears());
        if (req.getMaxTermYears()   != null) p.setMaxTermYears(req.getMaxTermYears());
        if (req.getGracePeriodDays()!= null) p.setGracePeriodDays(req.getGracePeriodDays());
        p.setFeatured(req.isFeatured());
        InsuranceProduct saved = productRepository.save(p);
        productVersionService.createNewVersion(saved, updatedBy);
        saved = productRepository.save(saved); // persist the incremented currentVersion pointer

        auditEventPublisher.publish("PRODUCT", saved.getId().toString(), "UPDATE",
                before,
                java.util.Map.of(
                        "name", saved.getName() != null ? saved.getName() : "",
                        "basePremium", saved.getBasePremium() != null ? saved.getBasePremium().toString() : "",
                        "minSumAssured", saved.getMinSumAssured() != null ? saved.getMinSumAssured().toString() : "",
                        "maxSumAssured", saved.getMaxSumAssured() != null ? saved.getMaxSumAssured().toString() : "",
                        "newVersion", String.valueOf(saved.getCurrentVersion())
                ),
                "Product " + saved.getCode() + " updated → version " + saved.getCurrentVersion()
                        + ". Prior version is preserved and still pinned by any policy already issued under it.");

        return ProductResponse.from(saved);
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse toggleActive(UUID id, boolean active, UUID updatedBy) {
        InsuranceProduct p = findOrThrow(id);
        boolean previous = p.isActive();
        p.setActive(active);
        InsuranceProduct saved = productRepository.save(p);
        auditEventPublisher.publish("PRODUCT", saved.getId().toString(), active ? "ACTIVATE" : "DEACTIVATE",
                java.util.Map.of("active", String.valueOf(previous)),
                java.util.Map.of("active", String.valueOf(active)),
                "Product " + saved.getCode() + (active ? " activated" : " deactivated"));
        return ProductResponse.from(saved);
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void delete(UUID id, UUID deletedBy) {
        InsuranceProduct p = findOrThrow(id);
        p.setDeleted(true);
        p.setActive(false);
        productRepository.save(p);
        auditEventPublisher.publish("PRODUCT", p.getId().toString(), "DELETE",
                java.util.Map.of("deleted", "false"),
                java.util.Map.of("deleted", "true"),
                "Product " + p.getCode() + " soft-deleted");
    }

    // ── Premium preview ───────────────────────────────────────────────────────
    //
    // Delegates to PremiumEngineService (the pluggable, rating-factor-driven
    // engine — see Phase 3) instead of the old hardcoded switch/case formula
    // that used to live here. Every call is persisted as a
    // PremiumCalculationAudit row, so "how was this premium calculated" is
    // always answerable later, per brief §4.3.

    @Transactional
    public Map<String, Object> calculatePremiumPreview(UUID productId, BigDecimal sumAssured,
                                                        String frequency, int termYears,
                                                        List<UUID> riderIds,
                                                        Integer age, BigDecimal riskScore,
                                                        String location, String vehicleType,
                                                        UUID requestedBy) {
        InsuranceProduct product = findOrThrow(productId);
        validateSumAssured(product, sumAssured);
        validateTermYears(product, termYears);

        ProductVersion activeVersion = productVersionService.getActiveVersion(productId);

        List<ProductRider> selectedRiders = new ArrayList<>();
        if (riderIds != null && !riderIds.isEmpty()) {
            for (ProductRider rider : product.getRiders()) {
                if (riderIds.contains(rider.getId()) && rider.isActive()) selectedRiders.add(rider);
            }
        }

        PremiumCalculationAudit result = premiumEngineService.calculate(new PremiumEngineService.CalculationInput(
                product, activeVersion, sumAssured, frequency, termYears, selectedRiders,
                age, riskScore, location, vehicleType,
                PremiumCalculationAudit.CalculatedFor.QUOTE, null, requestedBy
        ));

        return Map.ofEntries(
                Map.entry("calculationId",  result.getId()),
                Map.entry("productCode",    product.getCode()),
                Map.entry("productName",    product.getName()),
                Map.entry("insuranceType",  product.getInsuranceType().name()),
                Map.entry("productVersion", activeVersion.getVersionNumber()),
                Map.entry("sumAssured",     sumAssured),
                Map.entry("frequency",      frequency),
                Map.entry("termYears",      termYears),
                Map.entry("basePremium",    result.getBasePremium()),
                Map.entry("factorsApplied", result.getFactorsApplied()),
                Map.entry("riderPremium",   result.getRiderPremium()),
                Map.entry("gstAmount",      result.getGstAmount()),
                Map.entry("totalPremium",   result.getTotalPremium()),
                Map.entry("currency",       "INR")
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private InsuranceProduct findOrThrow(UUID id) {
        return productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found: " + id));
    }

    private void validateSumAssured(InsuranceProduct p, BigDecimal sa) {
        if (p.getMinSumAssured() != null && sa.compareTo(p.getMinSumAssured()) < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sum assured below minimum: ₹" + p.getMinSumAssured());
        if (p.getMaxSumAssured() != null && sa.compareTo(p.getMaxSumAssured()) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sum assured exceeds maximum: ₹" + p.getMaxSumAssured());
    }

    private void validateTermYears(InsuranceProduct p, int term) {
        if (p.getMinTermYears() != null && term < p.getMinTermYears())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Term below minimum: " + p.getMinTermYears() + " years");
        if (p.getMaxTermYears() != null && term > p.getMaxTermYears())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Term exceeds maximum: " + p.getMaxTermYears() + " years");
    }
}
