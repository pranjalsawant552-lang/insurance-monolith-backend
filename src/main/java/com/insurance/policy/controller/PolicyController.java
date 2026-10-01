package com.insurance.policy.controller;

import com.insurance.policy.dto.request.CreatePolicyRequest;
import com.insurance.policy.dto.response.*;
import com.insurance.policy.entity.*;
import com.insurance.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AUDIT FINDING (§8, "Workflow / Core Business Model Audit"): this
 * controller contains two independent policy-creation paths — the correct
 * one (PolicyIngestionController's /ingest, matching the stated business
 * model where the insurer issues the policy and this app only ingests the
 * resulting document), and a legacy self-issuance/underwriting path (this
 * controller's POST /policies → risk assessment → UNDERWRITING status →
 * /underwriting-decision → ACTIVE) where the application decides, itself,
 * whether to issue a policy — contradicting the stated business model.
 *
 * Per the audit's recommendation, this was ISOLATED rather than removed,
 * pending your explicit KEEP/ISOLATE/DEPRECATE/REMOVE decision: the four
 * endpoints below that belong to this legacy flow (create, issue,
 * underwriting-queue, underwriting-decision) are now gated behind
 * `policy.legacy-issuance.enabled`, defaulting to false. With the default,
 * all four return 403 with an explanation instead of silently working —
 * the code and data model are preserved untouched (nothing was deleted),
 * but the contradiction with your stated business model is no longer live
 * by default. Flip the flag in application.yml if you decide to KEEP this
 * flow (e.g. for a white-label/MGA arrangement); remove these four
 * endpoints and the corresponding PolicyService methods if you decide to
 * REMOVE it.
 */

@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
@Tag(name = "Policies", description = "Policy lifecycle management")
public class PolicyController {

    private final com.insurance.policy.client.ProductServiceClient productServiceClient;

    private final PolicyService policyService;

    @Value("${policy.legacy-issuance.enabled:false}")
    private boolean legacyIssuanceEnabled;

    /** See the class-level javadoc — gates the legacy self-issuance/underwriting endpoints. */
    private void requireLegacyIssuanceEnabled() {
        if (!legacyIssuanceEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This endpoint belongs to the legacy self-issuance/underwriting flow, which is "
                    + "disabled by default because it contradicts the platform's stated business model "
                    + "(the insurer issues the policy; this app ingests the resulting document). "
                    + "See MONOLITH-CONVERSION-NOTES.md / the audit report §8. "
                    + "Set policy.legacy-issuance.enabled=true if this is intentional.");
        }
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('POLICY_WRITE')")
    @Operation(summary = "Create a new policy application")
    public ResponseEntity<ApiResponse<PolicyResponse>> create(
            @Valid @RequestBody CreatePolicyRequest req,
            HttpServletRequest request) {

        requireLegacyIssuanceEnabled();
        UUID userId    = extractUserId(request);
        UUID companyId = extractCompanyId(request);
        UUID branchId  = extractBranchId(request);

        PolicyResponse policy = policyService.createPolicy(req, userId, companyId, branchId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(policy, "Policy application created successfully."));
    }

    // ── Issue ─────────────────────────────────────────────────────────────────

    @PostMapping("/{policyId}/issue")
    @PreAuthorize("hasAuthority('POLICY_ISSUE')")
    @Operation(summary = "Issue (activate) a pending policy — underwriter action")
    public ResponseEntity<ApiResponse<PolicyResponse>> issue(
            @PathVariable UUID policyId,
            HttpServletRequest request) {

        requireLegacyIssuanceEnabled();
        PolicyResponse policy = policyService.issuePolicy(policyId, extractCompanyId(request), extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(policy, "Policy issued successfully."));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{policyId}")
    @PreAuthorize("hasAuthority('POLICY_READ')")
    @Operation(summary = "Get policy by ID")
    public ResponseEntity<ApiResponse<PolicyResponse>> getById(@PathVariable UUID policyId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(policyService.getById(policyId, extractCompanyId(request))));
    }

    @GetMapping("/number/{policyNumber}")
    @PreAuthorize("hasAuthority('POLICY_READ')")
    @Operation(summary = "Get policy by policy number")
    public ResponseEntity<ApiResponse<PolicyResponse>> getByNumber(@PathVariable String policyNumber, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(policyService.getByPolicyNumber(policyNumber, extractCompanyId(request))));
    }

    // ── List with pagination, filter, sort ───────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('POLICY_READ')")
    @Operation(summary = "List all policies for current company (paginated, filterable)")
    public ResponseEntity<ApiResponse<PagedResponse<PolicyResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(required = false) InsuranceType type,
            HttpServletRequest request) {

        UUID companyId = extractCompanyId(request);
        return ResponseEntity.ok(ApiResponse.success(
                policyService.getPoliciesByCompany(companyId, status, type, page, size, sort)));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAuthority('POLICY_READ')")
    @Operation(summary = "List policies for a specific customer")
    public ResponseEntity<ApiResponse<PagedResponse<PolicyResponse>>> listByCustomer(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        return ResponseEntity.ok(ApiResponse.success(
                policyService.getPoliciesByCustomer(customerId, page, size, sort)));
    }

    // ── Premium preview ───────────────────────────────────────────────────────

    @GetMapping("/premium-preview")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Calculate premium preview before creating a policy — delegates to product-service's rating engine")
    public ResponseEntity<ApiResponse<Map<String, Object>>> premiumPreview(
            @RequestParam UUID productId,
            @RequestParam BigDecimal sumAssured,
            @RequestParam PremiumFrequency frequency,
            @RequestParam int termYears,
            @RequestParam(required = false) List<UUID> riderIds,
            @RequestParam(required = false) Integer age,
            @RequestParam(required = false) BigDecimal riskScore,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String vehicleType,
            HttpServletRequest request) {

        var quote = productServiceClient.calculatePremium(
                productId, sumAssured, frequency.name(), termYears, riderIds,
                age, riskScore, location, vehicleType, extractUserIdOrNull(request));

        Map<String, Object> body = Map.ofEntries(
                Map.entry("calculationId", quote.calculationId()),
                Map.entry("productCode", quote.productCode()),
                Map.entry("productName", quote.productName()),
                Map.entry("productVersion", quote.productVersion()),
                Map.entry("basePremium", quote.basePremium()),
                Map.entry("factorsApplied", quote.factorsApplied()),
                Map.entry("riderPremium", quote.riderPremium()),
                Map.entry("gstAmount", quote.gstAmount()),
                Map.entry("totalPremium", quote.totalPremium())
        );
        return ResponseEntity.ok(ApiResponse.success(body, "Premium calculated (inclusive of 18% GST)."));
    }

    // ── Underwriting queue (Phase 4) ────────────────────────────────────────

    @GetMapping("/underwriting-queue")
    @PreAuthorize("hasAuthority('POLICY_UNDERWRITE')")
    @Operation(summary = "List policies awaiting manual underwriting review")
    public ResponseEntity<ApiResponse<PagedResponse<PolicyResponse>>> underwritingQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        requireLegacyIssuanceEnabled();
        var result = policyService.listUnderwritingQueue(extractCompanyId(request), page, size);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(result)));
    }

    @PostMapping("/{policyId}/underwriting-decision")
    @PreAuthorize("hasAuthority('POLICY_UNDERWRITE')")
    @Operation(summary = "Approve or decline a policy held in UNDERWRITING — approve moves it to PENDING (ready for /issue), decline cancels it")
    public ResponseEntity<ApiResponse<PolicyResponse>> underwritingDecision(
            @PathVariable UUID policyId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String comments,
            HttpServletRequest request) {
        requireLegacyIssuanceEnabled();
        PolicyResponse policy = policyService.underwritingDecision(policyId, extractCompanyId(request), approved, comments, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(policy,
                approved ? "Underwriting approved — ready to issue." : "Policy declined and cancelled."));
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @PostMapping("/{policyId}/cancel")
    @PreAuthorize("hasAuthority('POLICY_CANCEL')")
    @Operation(summary = "Cancel a policy")
    public ResponseEntity<ApiResponse<PolicyResponse>> cancel(
            @PathVariable UUID policyId,
            @RequestParam String reason,
            HttpServletRequest request) {

        PolicyResponse policy = policyService.cancelPolicy(policyId, extractCompanyId(request), reason, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(policy, "Policy cancelled."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID extractUserId(HttpServletRequest request) {
        Object id = request.getAttribute("userId");
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing user context");
        return UUID.fromString(id.toString());
    }

    private UUID extractUserIdOrNull(HttpServletRequest request) {
        Object id = request.getAttribute("userId");
        return id != null ? UUID.fromString(id.toString()) : null;
    }

    private UUID extractCompanyId(HttpServletRequest request) {
        Object id = request.getAttribute("companyId");
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing company context");
        return UUID.fromString(id.toString());
    }

    private UUID extractBranchId(HttpServletRequest request) {
        Object id = request.getAttribute("branchId");
        return id != null ? UUID.fromString(id.toString()) : null;
    }
}
