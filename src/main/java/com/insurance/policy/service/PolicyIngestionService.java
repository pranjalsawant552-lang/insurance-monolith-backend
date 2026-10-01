package com.insurance.policy.service;

import com.insurance.policy.dto.request.IngestPolicyRequest;
import com.insurance.policy.dto.response.PolicyResponse;
import com.insurance.policy.entity.*;
import com.insurance.policy.event.AuditEventPublisher;
import com.insurance.policy.event.PolicyEventPublisher;
import com.insurance.policy.repository.PolicyIngestionFailureRepository;
import com.insurance.policy.repository.PolicyNomineeRepository;
import com.insurance.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * The post-pivot replacement for premium-calculation-driven policy creation:
 * the insurer already issued the policy, so this service's job is to turn
 * either (a) a verified document-service extraction, delivered as a Kafka
 * event, or (b) a manually-typed record, into a Policy row — not to price
 * or approve anything.
 *
 * Two entry points converge on the same {@link #ingest} core:
 *   - {@code PolicyDocumentVerifiedConsumer} for the automatic path (a
 *     reviewer just verified a POLICY_SCHEDULE extraction in document-service)
 *   - {@code PolicyIngestionController#ingest} for a direct, fully-manual entry
 *     or to resolve a row that landed in the ingestion-failure queue.
 *
 * Anything that can't be safely mapped into a Policy — a required field
 * missing from the verified fields, an unparseable date/amount, an
 * insurance type we can't infer — is written to
 * {@code policy_ingestion_failures} instead of thrown away or (for the
 * Kafka path) allowed to crash the consumer. See PolicyIngestionFailure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyIngestionService {

    private final PolicyRepository policyRepository;
    private final PolicyNomineeRepository nomineeRepository;
    private final PolicyIngestionFailureRepository failureRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final PolicyEventPublisher eventPublisher;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MMM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    // Renewal auto-link window: a fresh ingestion within this many days either side of a
    // prior term's maturity date, for the same customer + insurer + product, is treated
    // as a renewal rather than an unrelated new policy.
    private static final int RENEWAL_MATCH_WINDOW_DAYS = 60;

    // ── Entry point 1: automatic, from document-service's verification Kafka event ──

    @Transactional
    public void ingestFromVerifiedExtraction(Map<String, Object> event) {
        UUID extractionId = uuidOf(event.get("extractionId"));
        UUID documentId = uuidOf(event.get("documentId"));
        UUID companyId = uuidOf(event.get("companyId"));
        String ownerType = (String) event.get("ownerType");
        UUID ownerId = uuidOf(event.get("ownerId"));
        String issuerCode = (String) event.get("issuerCode");
        UUID reviewedBy = uuidOf(event.get("reviewedBy"));
        String insuranceTypeHint = (String) event.get("insuranceType");
        String productNameHint = (String) event.get("productName");
        UUID uploadedBy = uuidOf(event.get("uploadedBy"));

        @SuppressWarnings("unchecked")
        Map<String, Object> verifiedFields = event.get("verifiedFields") instanceof Map
                ? (Map<String, Object>) event.get("verifiedFields") : Map.of();

        if (extractionId != null && policyRepository.existsBySourceExtractionId(extractionId)) {
            log.info("Extraction {} already ingested as a policy — skipping (idempotent replay).", extractionId);
            return;
        }

        if (companyId == null) {
            recordFailure(extractionId, documentId, companyId, ownerId, verifiedFields,
                    "Verified-extraction event had no companyId — cannot attribute this policy to a company.");
            return;
        }

        if (!"CUSTOMER".equalsIgnoreCase(ownerType)) {
            recordFailure(extractionId, documentId, companyId, ownerId, verifiedFields,
                    "Extraction owner type was '" + ownerType + "', not CUSTOMER — this pipeline only "
                            + "auto-creates policies for documents uploaded against a customer profile. "
                            + "Resolve manually via POST /policies/ingestion-failures/{id}/resolve.");
            return;
        }

        try {
            IngestPolicyRequest req = mapVerifiedFieldsToRequest(verifiedFields, issuerCode, ownerId);
            req.setSourceExtractionId(extractionId);
            req.setSourceDocumentId(documentId);

            // Phase 10: prefer what the uploader actually told us over guessing from OCR text.
            if (insuranceTypeHint != null && !insuranceTypeHint.isBlank()) {
                try {
                    req.setInsuranceType(InsuranceType.valueOf(insuranceTypeHint.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // fall through to whatever mapVerifiedFieldsToRequest already inferred, if anything
                }
            }
            if (productNameHint != null && !productNameHint.isBlank()) {
                req.setProductName(productNameHint);
            }
            // The uploader is almost always the agent who sold/serviced the policy — set it as a
            // sensible default for commission attribution; a reviewer resolving a failure by hand
            // can always override it.
            if (uploadedBy != null) {
                req.setAgentId(uploadedBy);
            }

            validate(req);
            ingest(req, companyId, reviewedBy, IngestionSource.PDF_INGESTION);
            log.info("Auto-ingested policy {} from extraction {}", req.getPolicyNumber(), extractionId);
        } catch (Exception e) {
            log.warn("Automatic ingestion failed for extraction {}: {}", extractionId, e.getMessage());
            recordFailure(extractionId, documentId, companyId, ownerId, verifiedFields, e.getMessage());
        }
    }

    private void recordFailure(UUID extractionId, UUID documentId, UUID companyId, UUID customerId,
                                Map<String, Object> rawFields, String errorMessage) {
        PolicyIngestionFailure failure = PolicyIngestionFailure.builder()
                .sourceExtractionId(extractionId)
                .sourceDocumentId(documentId)
                .companyId(companyId != null ? companyId : new UUID(0, 0))
                .customerId(customerId)
                .rawFields(rawFields)
                .errorMessage(errorMessage != null ? errorMessage : "Unknown ingestion error")
                .status(PolicyIngestionFailure.FailureStatus.PENDING)
                .build();
        failureRepository.save(failure);
    }

    // ── Entry point 2: manual (direct call or resolving a failure) ──────────

    @Transactional
    public PolicyResponse ingestManual(IngestPolicyRequest req, UUID companyId, UUID actorId) {
        validate(req);
        Policy policy = ingest(req, companyId, actorId,
                req.getSourceExtractionId() != null ? IngestionSource.PDF_INGESTION : IngestionSource.MANUAL);
        return PolicyResponse.from(policy);
    }

    @Transactional
    public PolicyResponse resolveFailure(UUID failureId, IngestPolicyRequest req, UUID actorId) {
        PolicyIngestionFailure failure = failureRepository.findById(failureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion failure not found: " + failureId));
        if (failure.getStatus() != PolicyIngestionFailure.FailureStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only a PENDING ingestion failure can be resolved. Current status: " + failure.getStatus());
        }

        if (req.getCustomerId() == null) req.setCustomerId(failure.getCustomerId());
        if (req.getSourceExtractionId() == null) req.setSourceExtractionId(failure.getSourceExtractionId());
        if (req.getSourceDocumentId() == null) req.setSourceDocumentId(failure.getSourceDocumentId());

        validate(req);
        Policy policy = ingest(req, failure.getCompanyId(), actorId, IngestionSource.PDF_INGESTION);

        failure.setStatus(PolicyIngestionFailure.FailureStatus.RESOLVED);
        failure.setResolvedPolicyId(policy.getId());
        failure.setResolvedBy(actorId);
        failure.setResolvedAt(ZonedDateTime.now());
        failureRepository.save(failure);

        return PolicyResponse.from(policy);
    }

    // ── Shared core ───────────────────────────────────────────────────────

    private Policy ingest(IngestPolicyRequest req, UUID companyId, UUID actorId, IngestionSource source) {

        if (policyRepository.existsByPolicyNumber(req.getPolicyNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A policy with number " + req.getPolicyNumber() + " already exists.");
        }

        PremiumFrequency frequency = req.getPremiumFrequency() != null ? req.getPremiumFrequency() : PremiumFrequency.YEARLY;

        LocalDate maturityDate = req.getMaturityDate();
        Integer termYears = req.getPolicyTermYears();
        if (maturityDate == null && termYears == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Either maturityDate or policyTermYears is required.");
        }
        if (maturityDate == null) {
            maturityDate = req.getCommencementDate().plusYears(termYears);
        }
        if (termYears == null) {
            termYears = Math.max(1, Period.between(req.getCommencementDate(), maturityDate).getYears());
        }

        LocalDate nextDueDate = calculateNextDueDate(req.getCommencementDate(), frequency);

        Policy policy = Policy.builder()
                .policyNumber(req.getPolicyNumber())
                .insurerName(req.getInsurerName())
                .productName(req.getProductName())
                .customerId(req.getCustomerId())
                .agentId(req.getAgentId())
                .companyId(companyId)
                .insuranceType(req.getInsuranceType())
                .sumAssured(req.getSumAssured())
                .premiumAmount(req.getPremiumAmount())
                .premiumFrequency(frequency)
                .policyTermYears(termYears)
                .commencementDate(req.getCommencementDate())
                .maturityDate(maturityDate)
                .nextDueDate(nextDueDate)
                .status(PolicyStatus.ACTIVE) // the insurer already issued/activated it
                .ingestionSource(source)
                .sourceExtractionId(req.getSourceExtractionId())
                .sourceDocumentId(req.getSourceDocumentId())
                .issuedAt(ZonedDateTime.now())
                .issuedBy(actorId)
                .createdBy(actorId)
                .build();

        linkRenewalIfApplicable(policy);

        policy = policyRepository.save(policy);

        if (req.getNominees() != null) {
            for (IngestPolicyRequest.NomineeRequest n : req.getNominees()) {
                nomineeRepository.save(PolicyNominee.builder()
                        .policy(policy)
                        .fullName(n.getFullName())
                        .relationship(n.getRelationship() != null ? n.getRelationship() : "UNSPECIFIED")
                        .dateOfBirth(n.getDateOfBirth())
                        .sharePercent(n.getSharePercent() != null ? n.getSharePercent() : BigDecimal.valueOf(100))
                        .primary(n.isPrimary())
                        .build());
            }
        }

        eventPublisher.publishPolicyCreated(policy);
        eventPublisher.publishPolicyIssued(policy);
        auditEventPublisher.publish("POLICY", policy.getId().toString(), "CREATE",
                null,
                Map.of(
                        "policyNumber", policy.getPolicyNumber(),
                        "status", policy.getStatus().name(),
                        "ingestionSource", source.name(),
                        "insurerName", policy.getInsurerName() != null ? policy.getInsurerName() : "",
                        "sumAssured", policy.getSumAssured().toString(),
                        "premiumAmount", policy.getPremiumAmount().toString(),
                        "customerId", policy.getCustomerId().toString()
                ),
                "Policy " + policy.getPolicyNumber() + " recorded from " + source.name()
                        + (policy.getInsurerName() != null ? " (insurer: " + policy.getInsurerName() + ")" : ""));

        log.info("Policy ingested: {} (source={}, customer={})", policy.getPolicyNumber(), source, policy.getCustomerId());
        return policy;
    }

    private void linkRenewalIfApplicable(Policy policy) {
        if (policy.getInsurerName() == null || policy.getProductName() == null) return;
        List<Policy> priorTerms = policyRepository.findPriorTermsForRenewalMatch(
                policy.getCustomerId(), policy.getInsurerName(), policy.getProductName());
        for (Policy prior : priorTerms) {
            if (prior.getMaturityDate() == null) continue;
            long daysBetween = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                    prior.getMaturityDate(), policy.getCommencementDate()));
            if (daysBetween <= RENEWAL_MATCH_WINDOW_DAYS) {
                policy.setRenewedFromPolicyId(prior.getId());
                log.info("Linked {} as a renewal of {}", policy.getPolicyNumber(), prior.getPolicyNumber());
                return;
            }
        }
    }

    // ── Mapping verified extraction fields → a request ───────────────────

    /**
     * The generic POLICY_SCHEDULE extraction template (document-service Phase 8) only
     * captures policyNumber, sumAssured, premiumAmount, commencementDate, expiryDate and
     * nomineeName today — no insuranceType and no productName. Both are required here,
     * so a document that can't yield them lands in the failure queue for a human to
     * complete via the resolve endpoint, rather than guessing wrong and silently
     * mis-filing a motor policy as life insurance.
     */
    private IngestPolicyRequest mapVerifiedFieldsToRequest(Map<String, Object> fields, String issuerCode, UUID customerId) {
        IngestPolicyRequest req = new IngestPolicyRequest();
        req.setCustomerId(customerId);
        req.setInsurerName(issuerCode);
        req.setPolicyNumber(stringField(fields, "policyNumber"));
        req.setSumAssured(amountField(fields, "sumAssured"));
        req.setPremiumAmount(amountField(fields, "premiumAmount"));
        req.setCommencementDate(dateField(fields, "commencementDate"));
        req.setMaturityDate(dateField(fields, "expiryDate"));
        req.setInsuranceType(inferInsuranceType(fields));

        String nomineeName = stringField(fields, "nomineeName");
        if (nomineeName != null && !nomineeName.isBlank()) {
            IngestPolicyRequest.NomineeRequest nominee = new IngestPolicyRequest.NomineeRequest();
            nominee.setFullName(nomineeName);
            nominee.setRelationship("UNSPECIFIED — confirm with customer");
            nominee.setPrimary(true);
            nominee.setSharePercent(BigDecimal.valueOf(100));
            req.setNominees(List.of(nominee));
        }
        return req;
    }

    private InsuranceType inferInsuranceType(Map<String, Object> fields) {
        String haystack = fields.values().stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .reduce("", (a, b) -> a + " " + b)
                .toLowerCase();
        if (haystack.contains("vehicle") || haystack.contains("motor") || haystack.contains("idv")) return InsuranceType.MOTOR;
        if (haystack.contains("health") || haystack.contains("mediclaim") || haystack.contains("hospital")) return InsuranceType.HEALTH;
        if (haystack.contains("life") || haystack.contains("term plan") || haystack.contains("nominee")) return InsuranceType.LIFE;
        return null; // forces a validation failure → ingestion-failure queue, not a wrong guess
    }

    private void validate(IngestPolicyRequest req) {
        List<String> problems = new ArrayList<>();
        if (isBlank(req.getPolicyNumber())) problems.add("policyNumber could not be read from the document");
        if (isBlank(req.getInsurerName())) problems.add("insurerName is required (specify the issuer, e.g. via issuerCode at upload)");
        if (req.getInsuranceType() == null) problems.add("insuranceType could not be determined — specify LIFE, HEALTH, or MOTOR");
        if (req.getCustomerId() == null) problems.add("customerId is required");
        if (req.getSumAssured() == null) problems.add("sumAssured could not be read from the document");
        if (req.getPremiumAmount() == null) problems.add("premiumAmount could not be read from the document");
        if (req.getCommencementDate() == null) problems.add("commencementDate could not be read from the document");
        if (req.getMaturityDate() == null && req.getPolicyTermYears() == null)
            problems.add("expiryDate could not be read from the document, and no policyTermYears was supplied");
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
        }
    }

    // ── Small parsing helpers ─────────────────────────────────────────────

    private String stringField(Map<String, Object> fields, String key) {
        Object v = fields.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private BigDecimal amountField(Map<String, Object> fields, String key) {
        String raw = stringField(fields, key);
        if (raw == null) return null;
        String cleaned = raw.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate dateField(Map<String, Object> fields, String key) {
        String raw = stringField(fields, key);
        if (raw == null) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, fmt);
            } catch (DateTimeParseException ignored) { /* try next format */ }
        }
        return null;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private UUID uuidOf(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); } catch (IllegalArgumentException e) { return null; }
    }

    private LocalDate calculateNextDueDate(LocalDate commencement, PremiumFrequency frequency) {
        return switch (frequency) {
            case MONTHLY -> commencement.plusMonths(1);
            case QUARTERLY -> commencement.plusMonths(3);
            case HALF_YEARLY -> commencement.plusMonths(6);
            case YEARLY, SINGLE -> commencement.plusYears(1);
        };
    }
}
