package com.insurance.product.service;

import com.insurance.product.dto.request.RatingFactorRequest;
import com.insurance.product.entity.ProductVersion;
import com.insurance.product.entity.RatingFactor;
import com.insurance.product.repository.RatingFactorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Admin-facing CRUD over {@link RatingFactor} rows — this is what makes the
 * premium engine "pluggable, not hardcoded if/else" per the project brief:
 * an admin adds/edits/removes a row here and PremiumEngineService picks it
 * up on the very next calculation, no deploy involved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingFactorService {

    private final RatingFactorRepository ratingFactorRepository;
    private final ProductVersionService productVersionService;
    private final com.insurance.product.event.AuditEventPublisher auditEventPublisher;

    public List<RatingFactor> listForVersion(UUID productId, int versionNumber) {
        ProductVersion version = productVersionService.getVersion(productId, versionNumber);
        return ratingFactorRepository.findByProductVersionId(version.getId());
    }

    @Transactional
    public RatingFactor create(UUID productId, int versionNumber, RatingFactorRequest req, UUID createdBy) {
        ProductVersion version = productVersionService.getVersion(productId, versionNumber);
        if (version.getStatus() != ProductVersion.ProductVersionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot add a rating factor to a superseded version — edit the current active version instead.");
        }

        RatingFactor factor = RatingFactor.builder()
                .productId(productId)
                .productVersionId(version.getId())
                .factorType(req.getFactorType())
                .minValue(req.getMinValue())
                .maxValue(req.getMaxValue())
                .conditionValue(req.getConditionValue())
                .label(req.getLabel())
                .description(req.getDescription())
                .adjustmentType(req.getAdjustmentType())
                .multiplier(req.getMultiplier())
                .active(req.isActive())
                .build();
        RatingFactor saved = ratingFactorRepository.save(factor);

        auditEventPublisher.publish("RATING_FACTOR", saved.getId().toString(), "CREATE",
                null,
                java.util.Map.of("productId", productId.toString(), "version", String.valueOf(versionNumber),
                        "factorType", saved.getFactorType().name(), "adjustmentType", saved.getAdjustmentType().name(),
                        "multiplier", saved.getMultiplier().toString()),
                "Rating factor added to product " + productId + " v" + versionNumber + ": " + saved.getFactorType());

        return saved;
    }

    @Transactional
    public RatingFactor update(UUID factorId, RatingFactorRequest req, UUID updatedBy) {
        RatingFactor f = ratingFactorRepository.findById(factorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rating factor not found: " + factorId));

        f.setFactorType(req.getFactorType());
        f.setMinValue(req.getMinValue());
        f.setMaxValue(req.getMaxValue());
        f.setConditionValue(req.getConditionValue());
        f.setLabel(req.getLabel());
        f.setDescription(req.getDescription());
        f.setAdjustmentType(req.getAdjustmentType());
        f.setMultiplier(req.getMultiplier());
        f.setActive(req.isActive());
        RatingFactor saved = ratingFactorRepository.save(f);

        auditEventPublisher.publish("RATING_FACTOR", saved.getId().toString(), "UPDATE",
                null,
                java.util.Map.of("factorType", saved.getFactorType().name(),
                        "adjustmentType", saved.getAdjustmentType().name(), "multiplier", saved.getMultiplier().toString(),
                        "active", String.valueOf(saved.isActive())),
                "Rating factor " + saved.getId() + " updated");

        return saved;
    }

    @Transactional
    public void delete(UUID factorId) {
        RatingFactor f = ratingFactorRepository.findById(factorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rating factor not found: " + factorId));
        ratingFactorRepository.delete(f);
        auditEventPublisher.publish("RATING_FACTOR", factorId.toString(), "DELETE",
                java.util.Map.of("factorType", f.getFactorType().name()), null,
                "Rating factor " + factorId + " deleted");
    }
}
