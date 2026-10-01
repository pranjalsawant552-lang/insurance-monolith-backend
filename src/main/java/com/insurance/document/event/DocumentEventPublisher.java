package com.insurance.document.event;

import com.insurance.document.entity.Document;
import com.insurance.document.entity.ExtractionResult;
import com.insurance.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.insurance.monolith.events.DomainEventBus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes domain events other services react to — separate from
 * AuditEventPublisher's generic "this record changed" envelope, same
 * separation of concerns as policy-service's PolicyEventPublisher.
 *
 * This is the missing other half of the Phase 8 extraction pipeline: that
 * phase's changelog explicitly flagged "policy-service does not yet consume
 * verified extractions" as the next required step. A verified
 * POLICY_SCHEDULE extraction now fires here, and policy-service's
 * PolicyDocumentVerifiedConsumer picks it up to create the Policy record —
 * matching the client's "once confirmed, the policy shows up in the
 * dashboard" description of how the platform should behave.
 *
 * Deliberately only fires for document types a downstream service is known
 * to consume (currently just POLICY_SCHEDULE) — publishing an event nobody
 * listens to isn't harmful, but keeping this list explicit makes it obvious
 * at a glance what wiring exists versus what's still just sitting in the
 * verification queue for a person to look at (KYC documents, claim
 * evidence, etc. are correctly NOT published here yet).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentEventPublisher {

    private final DomainEventBus domainEventBus;
    private final DocumentRepository documentRepository;

    private static final String POLICY_DOCUMENT_VERIFIED_TOPIC = "policy.document.verified";

    /** Document types whose verified extraction should flow into policy-service.
     *  Kept as a small explicit set rather than "everything" — a KYC document type
     *  verifying, for instance, should not try to create a Policy row. */
    private static final java.util.Set<String> POLICY_CREATING_DOCUMENT_TYPES =
            java.util.Set.of("POLICY_SCHEDULE", "POLICY_DOCUMENT", "POLICY_CERTIFICATE");

    @Async
    public void publishIfPolicyCreating(ExtractionResult result) {
        if (result.getDocumentType() == null
                || !POLICY_CREATING_DOCUMENT_TYPES.contains(result.getDocumentType().toUpperCase())) {
            return;
        }

        Map<String, Object> event = new HashMap<>();
        event.put("extractionId", result.getId().toString());
        event.put("documentId", result.getDocumentId().toString());
        event.put("ownerType", result.getOwnerType());
        event.put("ownerId", result.getOwnerId().toString());
        event.put("companyId", result.getCompanyId().toString());
        event.put("documentType", result.getDocumentType());
        if (result.getIssuerCode() != null) event.put("issuerCode", result.getIssuerCode());
        if (result.getReviewedBy() != null) event.put("reviewedBy", result.getReviewedBy().toString());
        event.put("verifiedFields", result.getVerifiedFields());
        event.put("timestamp", ZonedDateTime.now().toString());

        // Phase 10: pull insuranceType/productName/uploadedBy straight off the source
        // Document — set by the uploader, far more reliable than inferring from OCR
        // text — plus the original uploader (almost always the agent) for commission
        // attribution on the policy-service side.
        documentRepository.findById(result.getDocumentId()).ifPresent(doc -> {
            if (doc.getInsuranceType() != null) event.put("insuranceType", doc.getInsuranceType());
            if (doc.getProductName() != null) event.put("productName", doc.getProductName());
            if (doc.getUploadedBy() != null) event.put("uploadedBy", doc.getUploadedBy().toString());
        });

        domainEventBus.publish(POLICY_DOCUMENT_VERIFIED_TOPIC, result.getId().toString(), event);
        log.info("Published policy.document.verified for extraction {} (document type {})",
                result.getId(), result.getDocumentType());
    }
}
