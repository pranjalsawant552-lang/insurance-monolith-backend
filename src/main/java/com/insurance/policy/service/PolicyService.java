package com.insurance.policy.service;

import com.insurance.policy.dto.request.CreatePolicyRequest;
import com.insurance.policy.dto.response.*;
import com.insurance.policy.entity.*;
import com.insurance.policy.event.PolicyEventPublisher;
import com.insurance.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final com.insurance.policy.repository.PolicyRenewalReminderRepository renewalReminderRepository;
    private final com.insurance.policy.client.ProductServiceClient productServiceClient;
    private final com.insurance.policy.client.RiskFraudServiceClient riskFraudServiceClient;
    private final com.insurance.policy.event.AuditEventPublisher auditEventPublisher;
    private final PolicyEventPublisher eventPublisher;

    /**
     * Configurable staged reminder points (audit report §4.7 asked for
     * 60/45/30/15/7/1-day staged reminders instead of one flat 30-day
     * trigger — this is that, driven by application.yml so it doesn't
     * need a redeploy to change).
     */
    @Value("${policy.renewal-reminder.milestone-days:60,45,30,15,7,1}")
    private int[] renewalReminderMilestoneDays;

    @Value("${policy.number-prefix.LIFE:LIF}")
    private String lifePrefx;
    @Value("${policy.number-prefix.HEALTH:HLT}")
    private String healthPrefix;
    @Value("${policy.number-prefix.MOTOR:MOT}")
    private String motorPrefix;

    // ── Create Policy ─────────────────────────────────────────────────────────

    @Transactional
    public PolicyResponse createPolicy(CreatePolicyRequest req, UUID createdBy,
                                       UUID companyId, UUID branchId) {

        // Delegate pricing (and product/sum-assured/term validation) entirely to
        // product-service — the single source of truth for the product catalog.
        // Policy-service no longer keeps its own (previously never-synced) copy
        // of product data or its own hardcoded pricing formula — see
        // ProductServiceClient and Phase 3's changelog.
        com.insurance.policy.client.ProductServiceClient.PremiumQuote quote = productServiceClient.calculatePremium(
                req.getProductId(), req.getSumAssured(), req.getPremiumFrequency().name(),
                req.getPolicyTermYears(), req.getRiderIds(),
                req.getAge(), req.getRiskScore(), req.getLocation(), req.getVehicleType(),
                createdBy);

        InsuranceType insuranceType = InsuranceType.valueOf(quote.insuranceType());

        // Generate unique policy number
        String policyNumber = generatePolicyNumber(insuranceType);

        LocalDate maturityDate = req.getCommencementDate().plusYears(req.getPolicyTermYears());
        LocalDate nextDueDate = calculateNextDueDate(req.getCommencementDate(), req.getPremiumFrequency());

        // Save first with a provisional UNDERWRITING status so risk-fraud-service
        // has a real policyId to link the assessment against — then update the
        // status below once we know the outcome. Safer default than PENDING if
        // something goes wrong between here and the status update: an
        // unreviewed policy sitting in UNDERWRITING can't be issued by mistake.
        Policy policy = Policy.builder()
                .policyNumber(policyNumber)
                .productId(req.getProductId())
                .productCode(quote.productCode())
                .productName(quote.productName())
                .productVersion(quote.productVersion())
                .premiumCalculationId(quote.calculationId())
                .customerId(req.getCustomerId())
                .agentId(req.getAgentId())
                .companyId(companyId)
                .branchId(branchId)
                .insuranceType(insuranceType)
                .sumAssured(req.getSumAssured())
                .premiumAmount(quote.totalPremium())
                .premiumFrequency(req.getPremiumFrequency())
                .policyTermYears(req.getPolicyTermYears())
                .commencementDate(req.getCommencementDate())
                .maturityDate(maturityDate)
                .nextDueDate(nextDueDate)
                .status(PolicyStatus.UNDERWRITING)
                .createdBy(createdBy)
                .build();

        policy = policyRepository.save(policy);

        // Underwriting risk assessment (Phase 4) — decides whether this policy
        // can move straight to PENDING (ready for /issue) or must stay in
        // UNDERWRITING for manual review. Replaces the "no risk assessment
        // exists anywhere" gap flagged in the original audit (§4.11).
        com.insurance.policy.client.RiskFraudServiceClient.RiskResult risk = riskFraudServiceClient.assess(
                policy.getId(), req.getCustomerId(), insuranceType.name(), req.getProductId(),
                req.getAge(), req.getBmi(), req.getSmoker(), req.getPreExistingCondition(),
                req.getOccupationClass(), req.getSumAssured(), req.getAnnualIncome(),
                req.getPriorClaimsCount(), req.getVehicleAge(), req.getVehicleType(),
                req.getLocationRiskZone(), createdBy);

        policy.setRiskAssessmentId(risk.assessmentId());
        if ("AUTO_APPROVE".equals(risk.decision())) {
            policy.setStatus(PolicyStatus.PENDING);
        } else if ("AUTO_DECLINE".equals(risk.decision())) {
            policy.setStatus(PolicyStatus.CANCELLED);
            policy.setRejectionReason("Auto-declined by underwriting risk assessment (score " + risk.riskScore() + ")");
        }
        // else MANUAL_REVIEW (or risk-fraud-service unreachable) → stays UNDERWRITING
        policy = policyRepository.save(policy);

        // Publish event for other services (notification, agent commission, etc.)
        eventPublisher.publishPolicyCreated(policy);
        auditEventPublisher.publish("POLICY", policy.getId().toString(), "CREATE",
                null,
                java.util.Map.of(
                        "policyNumber", policy.getPolicyNumber(),
                        "status", policy.getStatus().name(),
                        "sumAssured", policy.getSumAssured().toString(),
                        "premiumAmount", policy.getPremiumAmount().toString(),
                        "customerId", policy.getCustomerId().toString()
                ),
                "Policy " + policy.getPolicyNumber() + " created (PENDING)");

        log.info("Policy created: {} for customer {}", policyNumber, req.getCustomerId());
        return PolicyResponse.from(policy);
    }

    // ── Issue Policy ──────────────────────────────────────────────────────────

    @Transactional
    public PolicyResponse issuePolicy(UUID policyId, UUID companyId, UUID issuedBy) {
        Policy policy = getOrThrow(policyId, companyId);

        if (policy.getStatus() != PolicyStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PENDING policies can be issued. Current status: " + policy.getStatus());
        }

        policy.setStatus(PolicyStatus.ACTIVE);
        policy.setIssuedAt(ZonedDateTime.now());
        policy.setIssuedBy(issuedBy);
        policy = policyRepository.save(policy);

        eventPublisher.publishPolicyIssued(policy);
        auditEventPublisher.publish("POLICY", policy.getId().toString(), "STATUS_CHANGE",
                java.util.Map.of("status", "PENDING"),
                java.util.Map.of("status", "ACTIVE", "issuedAt", policy.getIssuedAt().toString()),
                "Policy " + policy.getPolicyNumber() + " issued (PENDING → ACTIVE)");

        log.info("Policy issued: {}", policy.getPolicyNumber());
        return PolicyResponse.from(policy);
    }

    // ── Get Policy ────────────────────────────────────────────────────────────

    public PolicyResponse getByPolicyNumber(String policyNumber, UUID companyId) {
        Policy policy = policyRepository.findByPolicyNumberAndDeletedFalse(policyNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Policy not found: " + policyNumber));
        if (!policy.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found: " + policyNumber);
        }
        return PolicyResponse.from(policy);
    }

    /**
     * INTERNAL USE ONLY — no tenant check. Same rationale as
     * CustomerService.getByIdInternal: for in-process callers reacting to
     * our own workflow (e.g. risk-fraud's PolicyServiceClient looking up a
     * policy's commencement date mid-fraud-evaluation), never from a
     * controller reachable by external HTTP request.
     */
    public PolicyResponse getByIdInternal(UUID id) {
        return PolicyResponse.from(policyRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found")));
    }

    /**
     * SECURITY FIX: previously getById(UUID id) took no companyId and
     * returned any policy in the database regardless of the caller's
     * company — a cross-tenant IDOR (audit report §7.1). Now scoped, 404
     * on mismatch (not 403) so ID-probing can't distinguish "doesn't
     * exist" from "exists, not yours."
     */
    public PolicyResponse getById(UUID id, UUID companyId) {
        return PolicyResponse.from(getOrThrow(id, companyId));
    }

    // ── List Policies ─────────────────────────────────────────────────────────

    public PagedResponse<PolicyResponse> getPoliciesByCustomer(UUID customerId, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        return PagedResponse.of(policyRepository.findByCustomerIdAndDeletedFalse(customerId, pageable)
                .map(PolicyResponse::from));
    }

    public PagedResponse<PolicyResponse> getPoliciesByCompany(UUID companyId, PolicyStatus status,
                                                               InsuranceType type, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);

        Page<Policy> policies;
        if (status != null) {
            policies = policyRepository.findByCompanyIdAndStatusAndDeletedFalse(companyId, status, pageable);
        } else if (type != null) {
            policies = policyRepository.findByCompanyIdAndInsuranceTypeAndDeletedFalse(companyId, type, pageable);
        } else {
            policies = policyRepository.findByCompanyIdAndDeletedFalse(companyId, pageable);
        }

        return PagedResponse.of(policies.map(PolicyResponse::from));
    }

    // ── Underwriting decision (Phase 4) ─────────────────────────────────────

    @Transactional
    public PolicyResponse underwritingDecision(UUID policyId, UUID companyId, boolean approved, String comments, UUID decidedBy) {
        Policy policy = getOrThrow(policyId, companyId);

        if (policy.getStatus() != PolicyStatus.UNDERWRITING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only policies awaiting underwriting review can receive a decision. Current status: " + policy.getStatus());
        }

        String previousStatus = policy.getStatus().name();
        if (approved) {
            policy.setStatus(PolicyStatus.PENDING);
        } else {
            policy.setStatus(PolicyStatus.CANCELLED);
            policy.setRejectionReason(comments != null ? comments : "Declined by underwriter");
        }
        policy.setUpdatedBy(decidedBy);
        policy = policyRepository.save(policy);

        auditEventPublisher.publish("POLICY", policy.getId().toString(), "UNDERWRITING_DECISION",
                java.util.Map.of("status", previousStatus),
                java.util.Map.of("status", policy.getStatus().name(), "comments", comments != null ? comments : ""),
                "Policy " + policy.getPolicyNumber() + " underwriting decision: "
                        + (approved ? "APPROVED → PENDING" : "DECLINED → CANCELLED") + " by " + decidedBy);

        log.info("Underwriting decision on {}: {}", policy.getPolicyNumber(), approved ? "APPROVED" : "DECLINED");
        return PolicyResponse.from(policy);
    }

    public org.springframework.data.domain.Page<PolicyResponse> listUnderwritingQueue(
            UUID companyId, int page, int size) {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100));
        return policyRepository.findByCompanyIdAndStatusAndDeletedFalse(companyId, PolicyStatus.UNDERWRITING, pageable)
                .map(PolicyResponse::from);
    }

    // ── Cancel Policy ─────────────────────────────────────────────────────────

    @Transactional
    public PolicyResponse cancelPolicy(UUID policyId, UUID companyId, String reason, UUID cancelledBy) {
        Policy policy = getOrThrow(policyId, companyId);

        if (policy.getStatus() == PolicyStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Policy is already cancelled.");
        }

        String previousStatus = policy.getStatus().name();
        policy.setStatus(PolicyStatus.CANCELLED);
        policy.setRejectionReason(reason);
        policy.setUpdatedBy(cancelledBy);
        policy = policyRepository.save(policy);

        eventPublisher.publishPolicyCancelled(policy);
        auditEventPublisher.publish("POLICY", policy.getId().toString(), "STATUS_CHANGE",
                java.util.Map.of("status", previousStatus),
                java.util.Map.of("status", "CANCELLED", "reason", reason != null ? reason : ""),
                "Policy " + policy.getPolicyNumber() + " cancelled: " + (reason != null ? reason : "no reason given"));

        return PolicyResponse.from(policy);
    }

    // ── Lapse Check (scheduled) ───────────────────────────────────────────────

    @Transactional
    public void processLapsedPolicies() {
        LocalDate graceCutoff = LocalDate.now().minusDays(30);
        var lapsed = policyRepository.findPoliciesPastGrace(graceCutoff);

        for (Policy p : lapsed) {
            p.setStatus(PolicyStatus.LAPSED);
            policyRepository.save(p);
            eventPublisher.publishPolicyLapsed(p);
            log.info("Policy lapsed: {}", p.getPolicyNumber());
        }
        log.info("Lapse check complete. Lapsed {} policies.", lapsed.size());
    }

    // ── Renewal reminder (scheduled) ─────────────────────────────────────────

    /**
     * FIX (audit report §4.7): this used to re-query a flat 30-day window
     * every day and re-publish a RENEWAL_DUE event for every policy still
     * in it, with no tracking of what was already sent — a policy due in
     * 25 days got a reminder queued once a day for 25 straight days. Now:
     * for each configured milestone (default 60/45/30/15/7/1 days out),
     * find policies due exactly that many days from today, and for each
     * one, only send if PolicyRenewalReminder doesn't already have a row
     * for (policy, milestone) — so each milestone fires exactly once per
     * policy, ever, regardless of how many times this method runs.
     */
    @Transactional
    public void sendRenewalReminders() {
        int sentCount = 0;
        for (int milestoneDays : renewalReminderMilestoneDays) {
            LocalDate targetDueDate = LocalDate.now().plusDays(milestoneDays);
            List<Policy> due = policyRepository.findPoliciesDueOn(targetDueDate);
            for (Policy p : due) {
                if (renewalReminderRepository.existsByPolicyIdAndMilestoneDays(p.getId(), milestoneDays)) {
                    continue; // already sent for this policy at this milestone — skip
                }
                eventPublisher.publishRenewalDue(p);
                renewalReminderRepository.save(PolicyRenewalReminder.builder()
                        .policyId(p.getId())
                        .milestoneDays(milestoneDays)
                        .sentAt(ZonedDateTime.now())
                        .build());
                sentCount++;
            }
        }
        log.info("Renewal reminders sent for {} policy/milestone combinations.", sentCount);
    }

    // ── Premium calculation preview ───────────────────────────────────────────
    // Removed: this used to call the local hardcoded PremiumCalculationService.
    // Premium preview is now handled directly by PolicyController delegating to
    // ProductServiceClient — see PolicyController.premiumPreview(). Kept this
    // section header as a breadcrumb for anyone grepping the old method name.

    // ── Private helpers ───────────────────────────────────────────────────────

    private Policy getOrThrow(UUID id, UUID companyId) {
        Policy policy = policyRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));
        // SECURITY: 404, not 403 — see audit report §7.1 / CustomerService.findOrThrow for the pattern.
        if (!policy.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found");
        }
        return policy;
    }

    // (findActiveProduct / validateAgainstProductRules removed — product-service's
    //  premium-preview endpoint now performs this validation itself, and
    //  ProductServiceClient surfaces any failure as a 400/503 automatically)

    private String generatePolicyNumber(InsuranceType type) {
        String prefix = switch (type) {
            case LIFE -> lifePrefx;
            case HEALTH -> healthPrefix;
            case MOTOR -> motorPrefix;
        };
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(3); // 10 digits
        String policyNumber = prefix + "-" + timestamp;
        // Ensure uniqueness
        while (policyRepository.existsByPolicyNumber(policyNumber)) {
            policyNumber = prefix + "-" + System.currentTimeMillis();
        }
        return policyNumber;
    }

    private LocalDate calculateNextDueDate(LocalDate commencement, PremiumFrequency frequency) {
        return switch (frequency) {
            case MONTHLY -> commencement.plusMonths(1);
            case QUARTERLY -> commencement.plusMonths(3);
            case HALF_YEARLY -> commencement.plusMonths(6);
            case YEARLY, SINGLE -> commencement.plusYears(1);
        };
    }

    private Pageable buildPageable(int page, int size, String sort) {
        size = Math.min(size, 100);
        Sort sortObj = sort != null && sort.endsWith(",asc")
                ? Sort.by(sort.split(",")[0]).ascending()
                : Sort.by(sort != null ? sort.split(",")[0] : "createdAt").descending();
        return PageRequest.of(page, size, sortObj);
    }
}
