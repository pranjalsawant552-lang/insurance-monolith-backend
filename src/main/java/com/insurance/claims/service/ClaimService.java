package com.insurance.claims.service;

import com.insurance.claims.dto.request.CreateClaimRequest;
import com.insurance.claims.dto.response.*;
import com.insurance.claims.entity.*;
import com.insurance.claims.event.ClaimEventPublisher;
import com.insurance.claims.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimEventPublisher eventPublisher;
    private final com.insurance.claims.event.AuditEventPublisher auditEventPublisher;
    private final com.insurance.claims.client.RiskFraudServiceClient riskFraudServiceClient;
    private final SlaService slaService;

    // ── Submit Claim (FNOL) ───────────────────────────────────────────────────

    @Transactional
    public ClaimResponse submit(CreateClaimRequest req, UUID customerId,
                                UUID companyId, UUID branchId, UUID createdBy) {

        String claimNumber = generateClaimNumber(req.getInsuranceType());

        Claim claim = Claim.builder()
                .claimNumber(claimNumber)
                .policyId(req.getPolicyId())
                .policyNumber(req.getPolicyNumber())
                .customerId(customerId)
                .companyId(companyId)
                .branchId(branchId)
                .insuranceType(req.getInsuranceType())
                .claimType(req.getClaimType())
                .incidentDate(req.getIncidentDate())
                .description(req.getDescription())
                .claimedAmount(req.getClaimedAmount())
                .status(ClaimStatus.SUBMITTED)
                .priority(determinePriority(req.getClaimedAmount()))
                .createdBy(createdBy)
                .build();

        // Add initial workflow history entry
        addHistory(claim, null, ClaimStatus.SUBMITTED, "FNOL submitted", null, createdBy);
        slaService.applySlaForNewStatus(claim);

        claim = claimRepository.save(claim);
        eventPublisher.publishClaimSubmitted(claim);
        auditEventPublisher.publish("CLAIM", claim.getId().toString(), "CREATE",
                null,
                Map.of("claimNumber", claim.getClaimNumber(), "status", "SUBMITTED",
                        "claimedAmount", claim.getClaimedAmount().toString(), "policyNumber", claim.getPolicyNumber()),
                "Claim " + claim.getClaimNumber() + " submitted (FNOL)");

        // Automated fraud rule check (Phase 4) — replaces "fraud detection is
        // manual-only" gap flagged in the original audit (§4.10). A claims
        // officer can still confirm/override via the existing flag-fraud
        // endpoint; this just means a claim never sits unscored by default.
        com.insurance.claims.client.RiskFraudServiceClient.FraudResult fraud = riskFraudServiceClient.evaluate(
                claim.getId(), claim.getPolicyId(), claim.getPolicyNumber(), customerId,
                claim.getIncidentDate(), createdBy);
        if (fraud.recommendFlag()) {
            claim.setFraudFlag(true);
            claim.setFraudScore(fraud.fraudScore());
            claim = claimRepository.save(claim);
            eventPublisher.publishFraudFlagged(claim);
            auditEventPublisher.publish("CLAIM", claim.getId().toString(), "AUTO_FRAUD_FLAG",
                    Map.of("fraudFlag", "false"),
                    Map.of("fraudFlag", "true", "fraudScore", fraud.fraudScore().toString(),
                            "triggeredRules", String.valueOf(fraud.triggeredRules().size())),
                    "Claim " + claim.getClaimNumber() + " auto-flagged for fraud review by risk-fraud-service (score "
                            + fraud.fraudScore() + ", " + fraud.triggeredRules().size() + " rule(s) triggered)");
        }

        log.info("Claim submitted: {} for policy {}", claimNumber, req.getPolicyNumber());
        return ClaimResponse.from(claim);
    }

    // ── Assign to Claims Officer ──────────────────────────────────────────────

    @Transactional
    public ClaimResponse assignToOfficer(UUID claimId, UUID companyId, UUID officerId, UUID assignedBy) {
        Claim claim = findOrThrow(claimId, companyId);
        validateTransition(claim, ClaimStatus.UNDER_REVIEW);

        claim.setAssignedTo(officerId);
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        claim.setUpdatedBy(assignedBy);
        addHistory(claim, ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW,
                "Assigned to claims officer", null, assignedBy);
        slaService.applySlaForNewStatus(claim);

        return ClaimResponse.from(claimRepository.save(claim));
    }

    // ── Request Additional Documents ──────────────────────────────────────────

    @Transactional
    public ClaimResponse requestDocuments(UUID claimId, UUID companyId, String documentList, UUID requestedBy) {
        Claim claim = findOrThrow(claimId, companyId);
        claim.setStatus(ClaimStatus.DOCS_REQUIRED);
        claim.setUpdatedBy(requestedBy);
        addHistory(claim, claim.getStatus(), ClaimStatus.DOCS_REQUIRED,
                "Additional documents required", documentList, requestedBy);
        slaService.applySlaForNewStatus(claim);

        // Kafka event triggers notification service to email/SMS customer
        eventPublisher.publishClaimSubmitted(claim); // reuse — notification listens for DOCS_REQUIRED in status field
        return ClaimResponse.from(claimRepository.save(claim));
    }

    // ── Assign Surveyor ───────────────────────────────────────────────────────

    @Transactional
    public ClaimResponse assignSurveyor(UUID claimId, UUID companyId, UUID surveyorId, UUID assignedBy) {
        Claim claim = findOrThrow(claimId, companyId);
        claim.setSurveyorId(surveyorId);
        claim.setStatus(ClaimStatus.SURVEYOR_ASSIGNED);
        claim.setUpdatedBy(assignedBy);
        addHistory(claim, claim.getStatus(), ClaimStatus.SURVEYOR_ASSIGNED,
                "Surveyor assigned", null, assignedBy);
        slaService.applySlaForNewStatus(claim);
        return ClaimResponse.from(claimRepository.save(claim));
    }

    // ── Record Surveyor Report ────────────────────────────────────────────────

    @Transactional
    public ClaimResponse recordSurveyorReport(UUID claimId, UUID companyId, BigDecimal assessedAmount,
                                              String recommendation, UUID recordedBy) {
        Claim claim = findOrThrow(claimId, companyId);
        claim.setStatus(ClaimStatus.SURVEYOR_REPORT_RECEIVED);
        claim.setUpdatedBy(recordedBy);
        addHistory(claim, claim.getStatus(), ClaimStatus.SURVEYOR_REPORT_RECEIVED,
                "Surveyor report received. Assessed: " + assessedAmount + ". Recommendation: " + recommendation,
                null, recordedBy);
        slaService.applySlaForNewStatus(claim);
        return ClaimResponse.from(claimRepository.save(claim));
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @Transactional
    public ClaimResponse approve(UUID claimId, UUID companyId, BigDecimal approvedAmount, String comments, UUID approvedBy) {
        Claim claim = findOrThrow(claimId, companyId);

        if (approvedAmount.compareTo(claim.getClaimedAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Approved amount cannot exceed claimed amount.");
        }

        String previousStatus = claim.getStatus().name();
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setApprovedAmount(approvedAmount);
        claim.setUpdatedBy(approvedBy);
        addHistory(claim, claim.getStatus(), ClaimStatus.APPROVED,
                "Claim approved for " + approvedAmount, comments, approvedBy);
        slaService.applySlaForNewStatus(claim);

        Claim saved = claimRepository.save(claim);
        eventPublisher.publishClaimApproved(saved);
        auditEventPublisher.publish("CLAIM", saved.getId().toString(), "APPROVE",
                Map.of("status", previousStatus),
                Map.of("status", "APPROVED", "approvedAmount", approvedAmount.toString(), "comments", comments != null ? comments : ""),
                "Claim " + saved.getClaimNumber() + " approved for " + approvedAmount);
        log.info("Claim approved: {} amount={}", claim.getClaimNumber(), approvedAmount);
        return ClaimResponse.from(saved);
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @Transactional
    public ClaimResponse reject(UUID claimId, UUID companyId, String reason, UUID rejectedBy) {
        Claim claim = findOrThrow(claimId, companyId);

        String previousStatus = claim.getStatus().name();
        claim.setStatus(ClaimStatus.REJECTED);
        claim.setRejectionReason(reason);
        claim.setUpdatedBy(rejectedBy);
        addHistory(claim, claim.getStatus(), ClaimStatus.REJECTED, "Claim rejected", reason, rejectedBy);
        slaService.applySlaForNewStatus(claim);

        Claim saved = claimRepository.save(claim);
        eventPublisher.publishClaimRejected(saved);
        auditEventPublisher.publish("CLAIM", saved.getId().toString(), "REJECT",
                Map.of("status", previousStatus),
                Map.of("status", "REJECTED", "reason", reason != null ? reason : ""),
                "Claim " + saved.getClaimNumber() + " rejected: " + (reason != null ? reason : "no reason given"));
        return ClaimResponse.from(saved);
    }

    // ── Settle ────────────────────────────────────────────────────────────────

    @Transactional
    public ClaimResponse settle(UUID claimId, UUID companyId, BigDecimal settledAmount,
                                String settlementRef, UUID settledBy) {
        Claim claim = findOrThrow(claimId, companyId);

        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only APPROVED claims can be settled.");
        }

        claim.setStatus(ClaimStatus.SETTLED);
        claim.setSettledAmount(settledAmount);
        claim.setSettlementDate(LocalDate.now());
        claim.setSettlementReference(settlementRef);
        claim.setUpdatedBy(settledBy);
        addHistory(claim, ClaimStatus.APPROVED, ClaimStatus.SETTLED,
                "Payment settled: " + settledAmount + " ref: " + settlementRef,
                null, settledBy);
        slaService.applySlaForNewStatus(claim);

        Claim saved = claimRepository.save(claim);
        eventPublisher.publishClaimSettled(saved);
        auditEventPublisher.publish("CLAIM", saved.getId().toString(), "SETTLE",
                Map.of("status", "APPROVED"),
                Map.of("status", "SETTLED", "settledAmount", settledAmount.toString(), "settlementReference", settlementRef != null ? settlementRef : ""),
                "Claim " + saved.getClaimNumber() + " settled for " + settledAmount + " (ref: " + settlementRef + ")");
        log.info("Claim settled: {} amount={}", claim.getClaimNumber(), settledAmount);
        return ClaimResponse.from(saved);
    }

    // ── Flag Fraud ────────────────────────────────────────────────────────────

    @Transactional
    public ClaimResponse flagFraud(UUID claimId, UUID companyId, BigDecimal fraudScore, UUID flaggedBy) {
        Claim claim = findOrThrow(claimId, companyId);
        claim.setFraudFlag(true);
        claim.setFraudScore(fraudScore);
        claim.setPriority(ClaimPriority.CRITICAL);
        claim.setUpdatedBy(flaggedBy);
        addHistory(claim, claim.getStatus(), claim.getStatus(),
                "Fraud flag raised. Score: " + fraudScore, null, flaggedBy);
        Claim saved = claimRepository.save(claim);
        eventPublisher.publishFraudFlagged(saved);
        auditEventPublisher.publish("CLAIM", saved.getId().toString(), "FLAG_FRAUD",
                Map.of("fraudFlag", "false"),
                Map.of("fraudFlag", "true", "fraudScore", fraudScore.toString()),
                "Claim " + saved.getClaimNumber() + " flagged for fraud review (score: " + fraudScore + ")");
        return ClaimResponse.from(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * SECURITY FIX: previously took no companyId — cross-tenant IDOR
     * (audit report §7.1). Now scoped; 404 (not 403) on mismatch.
     */
    public ClaimResponse getById(UUID id, UUID companyId) {
        return ClaimResponse.from(findOrThrow(id, companyId));
    }

    public ClaimResponse getByNumber(String claimNumber, UUID companyId) {
        Claim claim = claimRepository.findByClaimNumberAndDeletedFalse(claimNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Claim not found: " + claimNumber));
        if (!claim.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found: " + claimNumber);
        }
        return ClaimResponse.from(claim);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    public PagedResponse<ClaimResponse> list(UUID companyId, ClaimStatus status, InsuranceType type,
                                             UUID branchId, UUID assignedTo, ClaimPriority priority,
                                             Boolean slaBreached,
                                             LocalDate incidentDateFrom, LocalDate incidentDateTo,
                                             int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);

        org.springframework.data.jpa.domain.Specification<Claim> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            predicates.add(cb.isFalse(root.get("deleted")));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (type != null) predicates.add(cb.equal(root.get("insuranceType"), type));
            if (branchId != null) predicates.add(cb.equal(root.get("branchId"), branchId));
            if (assignedTo != null) predicates.add(cb.equal(root.get("assignedTo"), assignedTo));
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (slaBreached != null) predicates.add(cb.equal(root.get("slaBreached"), slaBreached));
            if (incidentDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("incidentDate"), incidentDateFrom));
            if (incidentDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("incidentDate"), incidentDateTo));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Claim> result = claimRepository.findAll(spec, pageable);
        return PagedResponse.of(result.map(ClaimResponse::from));
    }

    public PagedResponse<ClaimResponse> listByCustomer(UUID customerId, int page, int size) {
        return PagedResponse.of(claimRepository.findByCustomerIdAndDeletedFalse(
                customerId, buildPageable(page, size, "createdAt,desc")).map(ClaimResponse::from));
    }

    public PagedResponse<ClaimResponse> listFraudFlagged(UUID companyId, int page, int size) {
        return PagedResponse.of(claimRepository.findFlaggedClaims(
                companyId, buildPageable(page, size, "createdAt,desc")).map(ClaimResponse::from));
    }

    public Map<String, Long> getStats(UUID companyId) {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (ClaimStatus s : ClaimStatus.values()) {
            stats.put(s.name(), claimRepository.countByCompanyIdAndStatusAndDeletedFalse(companyId, s));
        }
        return stats;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Claim findOrThrow(UUID id, UUID companyId) {
        Claim claim = claimRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found: " + id));
        // SECURITY: 404, not 403 — cross-tenant IDOR fix, see audit report §7.1.
        if (!claim.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found: " + id);
        }
        return claim;
    }

    private void addHistory(Claim claim, ClaimStatus oldStatus, ClaimStatus newStatus,
                            String action, String comments, UUID performedBy) {
        ClaimWorkflowHistory h = ClaimWorkflowHistory.builder()
                .claim(claim)
                .oldStatus(oldStatus != null ? oldStatus.name() : null)
                .newStatus(newStatus.name())
                .action(action)
                .comments(comments)
                .performedBy(performedBy)
                .build();
        claim.getWorkflowHistory().add(h);
    }

    private void validateTransition(Claim claim, ClaimStatus targetStatus) {
        Set<ClaimStatus> validFrom = Map.of(
                ClaimStatus.UNDER_REVIEW,              Set.of(ClaimStatus.SUBMITTED),
                ClaimStatus.DOCS_REQUIRED,             Set.of(ClaimStatus.UNDER_REVIEW),
                ClaimStatus.SURVEYOR_ASSIGNED,         Set.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.DOCS_REQUIRED),
                ClaimStatus.SURVEYOR_REPORT_RECEIVED,  Set.of(ClaimStatus.SURVEYOR_ASSIGNED),
                ClaimStatus.APPROVED,                  Set.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.SURVEYOR_REPORT_RECEIVED),
                ClaimStatus.REJECTED,                  Set.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.SURVEYOR_REPORT_RECEIVED),
                ClaimStatus.SETTLED,                   Set.of(ClaimStatus.APPROVED)
        ).getOrDefault(targetStatus, Set.of());

        if (!validFrom.contains(claim.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot transition from " + claim.getStatus() + " to " + targetStatus);
        }
    }

    private ClaimPriority determinePriority(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("5000000")) >= 0) return ClaimPriority.CRITICAL;
        if (amount.compareTo(new BigDecimal("1000000")) >= 0) return ClaimPriority.HIGH;
        if (amount.compareTo(new BigDecimal("100000"))  >= 0) return ClaimPriority.NORMAL;
        return ClaimPriority.LOW;
    }

    private String generateClaimNumber(InsuranceType type) {
        String prefix = switch (type) {
            case LIFE   -> "CLM-LIF";
            case HEALTH -> "CLM-HLT";
            case MOTOR  -> "CLM-MOT";
        };
        String number = prefix + "-" + String.valueOf(System.currentTimeMillis()).substring(3);
        while (claimRepository.existsByClaimNumber(number)) {
            number = prefix + "-" + System.currentTimeMillis();
        }
        return number;
    }

    private Pageable buildPageable(int page, int size, String sort) {
        String[] parts = (sort != null ? sort : "createdAt,desc").split(",");
        Sort s = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.by(parts[0]).ascending() : Sort.by(parts[0]).descending();
        return PageRequest.of(page, Math.min(size, 100), s);
    }
}
