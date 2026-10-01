package com.insurance.document.service;

import com.insurance.document.entity.ExtractionResult;
import com.insurance.document.repository.ExtractionResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The human-verification workflow — every extraction sits here as
 * PENDING_REVIEW until a person confirms or corrects it. Nothing downstream
 * (policy-service, etc.) should ever be wired to read extractedFields
 * directly; verifiedFields (populated only once status = VERIFIED) is the
 * only trustworthy output of this whole pipeline.
 */
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private final ExtractionResultRepository repository;
    private final com.insurance.document.event.AuditEventPublisher auditEventPublisher;
    private final com.insurance.document.event.DocumentEventPublisher documentEventPublisher;

    public Page<ExtractionResult> queue(ExtractionResult.ExtractionStatus status, String documentType,
                                         int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.ASC, "createdAt"));
        return repository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (documentType != null && !documentType.isBlank()) predicates.add(cb.equal(root.get("documentType"), documentType.toUpperCase()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable);
    }

    public ExtractionResult getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Extraction result not found: " + id));
    }

    /**
     * A reviewer confirms the extraction — either as-is, or with corrections.
     * @param correctedFields the reviewer's final field values (start from extractedFields
     *                        on the client, edit what's wrong, submit the whole set back —
     *                        this way verifiedFields is always complete, not a sparse diff).
     */
    @Transactional
    public ExtractionResult verify(UUID id, Map<String, Object> correctedFields, String notes, UUID reviewedBy) {
        ExtractionResult result = getOrThrow(id);
        if (result.getStatus() != ExtractionResult.ExtractionStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only a PENDING_REVIEW extraction can be verified. Current status: " + result.getStatus());
        }

        result.setVerifiedFields(correctedFields);
        result.setStatus(ExtractionResult.ExtractionStatus.VERIFIED);
        result.setReviewedBy(reviewedBy);
        result.setReviewedAt(ZonedDateTime.now());
        result.setReviewNotes(notes);
        ExtractionResult saved = repository.save(result);

        auditEventPublisher.publish("EXTRACTION_RESULT", saved.getId().toString(), "VERIFY",
                java.util.Map.of("status", "PENDING_REVIEW"),
                java.util.Map.of("status", "VERIFIED", "documentType", saved.getDocumentType()),
                "Extraction " + saved.getId() + " verified for document " + saved.getDocumentId()
                        + " (" + saved.getDocumentType() + ")");

        // The other half of the pipeline: if this is a policy-creating document type,
        // policy-service picks this up and turns it into a Policy record. Fire-and-forget
        // (async) — a Kafka publish failure here must never block the reviewer's verify action.
        documentEventPublisher.publishIfPolicyCreating(saved);

        return saved;
    }

    @Transactional
    public ExtractionResult reject(UUID id, String reason, UUID reviewedBy) {
        ExtractionResult result = getOrThrow(id);
        if (result.getStatus() != ExtractionResult.ExtractionStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only a PENDING_REVIEW extraction can be rejected. Current status: " + result.getStatus());
        }

        result.setStatus(ExtractionResult.ExtractionStatus.REJECTED);
        result.setReviewedBy(reviewedBy);
        result.setReviewedAt(ZonedDateTime.now());
        result.setReviewNotes(reason);
        ExtractionResult saved = repository.save(result);

        auditEventPublisher.publish("EXTRACTION_RESULT", saved.getId().toString(), "REJECT",
                java.util.Map.of("status", "PENDING_REVIEW"),
                java.util.Map.of("status", "REJECTED", "reason", reason != null ? reason : ""),
                "Extraction " + saved.getId() + " rejected: " + (reason != null ? reason : "no reason given"));

        return saved;
    }

    /** For a scanned document too poor to extract meaningfully — asks for a better copy rather
     *  than being force-fit into VERIFIED or REJECTED, which both imply a decision was made
     *  about the document's actual content. */
    @Transactional
    public ExtractionResult requestBetterScan(UUID id, String reason, UUID reviewedBy) {
        ExtractionResult result = getOrThrow(id);
        result.setStatus(ExtractionResult.ExtractionStatus.NEEDS_BETTER_SCAN);
        result.setReviewedBy(reviewedBy);
        result.setReviewedAt(ZonedDateTime.now());
        result.setReviewNotes(reason);
        return repository.save(result);
    }
}
