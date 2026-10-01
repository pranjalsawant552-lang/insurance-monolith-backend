package com.insurance.claims.controller;

import com.insurance.claims.dto.request.CreateClaimRequest;
import com.insurance.claims.dto.response.*;
import com.insurance.claims.entity.*;
import com.insurance.claims.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/claims")
@RequiredArgsConstructor
@Tag(name = "Claims", description = "Claims lifecycle — FNOL, review, approval, settlement")
public class ClaimController {

    private final ClaimService claimService;
    private final com.insurance.claims.service.SlaConfigService slaConfigService;
    private final com.insurance.claims.service.IdempotencyService idempotencyService;

    private ResponseEntity<Object> replay(com.insurance.claims.service.IdempotencyService.CachedResponse cached) {
        return ResponseEntity.status(cached.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(cached.body());
    }

    // ── Submit FNOL ───────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('CLAIM_WRITE')")
    @Operation(summary = "Submit a new claim (First Notice of Loss)")
    public ResponseEntity<ApiResponse<ClaimResponse>> submit(
            @Valid @RequestBody CreateClaimRequest req,
            HttpServletRequest request) {

        ClaimResponse resp = claimService.submit(req,
                extractUserId(request), extractCompanyId(request),
                extractBranchId(request), extractUserId(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(resp, "Claim submitted successfully."));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLAIM_READ')")
    @Operation(summary = "Get claim by ID")
    public ResponseEntity<ApiResponse<ClaimResponse>> getById(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(claimService.getById(id, extractCompanyId(request))));
    }

    @GetMapping("/number/{claimNumber}")
    @PreAuthorize("hasAuthority('CLAIM_READ')")
    @Operation(summary = "Get claim by claim number")
    public ResponseEntity<ApiResponse<ClaimResponse>> getByNumber(@PathVariable String claimNumber, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(claimService.getByNumber(claimNumber, extractCompanyId(request))));
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('CLAIM_READ')")
    @Operation(summary = "List all claims (paginated, filterable by status, type, branch, assignee, priority, SLA breach, and incident-date range)")
    public ResponseEntity<ApiResponse<PagedResponse<ClaimResponse>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) InsuranceType type,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) ClaimPriority priority,
            @RequestParam(required = false) Boolean slaBreached,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incidentDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incidentDateTo,
            HttpServletRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                claimService.list(extractCompanyId(request), status, type, branchId, assignedTo,
                        priority, slaBreached, incidentDateFrom, incidentDateTo, page, size, sort)));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAuthority('CLAIM_READ')")
    @Operation(summary = "List claims for a specific customer")
    public ResponseEntity<ApiResponse<PagedResponse<ClaimResponse>>> listByCustomer(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(claimService.listByCustomer(customerId, page, size)));
    }

    @GetMapping("/fraud-flagged")
    @PreAuthorize("hasAuthority('CLAIM_FLAG_FRAUD')")
    @Operation(summary = "List fraud-flagged claims (admin/fraud team only)")
    public ResponseEntity<ApiResponse<PagedResponse<ClaimResponse>>> listFraud(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                claimService.listFraudFlagged(extractCompanyId(request), page, size)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('CLAIM_READ')")
    @Operation(summary = "Get claim counts by status (dashboard widget)")
    public ResponseEntity<ApiResponse<Map<String, Long>>> stats(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(claimService.getStats(extractCompanyId(request))));
    }

    // ── Workflow actions ──────────────────────────────────────────────────────

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('CLAIM_ASSIGN')")
    @Operation(summary = "Assign claim to a claims officer")
    public ResponseEntity<ApiResponse<ClaimResponse>> assign(
            @PathVariable UUID id,
            @RequestParam UUID officerId,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                claimService.assignToOfficer(id, extractCompanyId(request), officerId, extractUserId(request)),
                "Claim assigned."));
    }

    @PostMapping("/{id}/request-documents")
    @PreAuthorize("hasAuthority('CLAIM_WRITE')")
    @Operation(summary = "Request additional documents from customer")
    public ResponseEntity<ApiResponse<ClaimResponse>> requestDocs(
            @PathVariable UUID id,
            @RequestParam String documentList,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                claimService.requestDocuments(id, extractCompanyId(request), documentList, extractUserId(request)),
                "Document request sent."));
    }

    @PostMapping("/{id}/assign-surveyor")
    @PreAuthorize("hasAuthority('CLAIM_ASSIGN')")
    @Operation(summary = "Assign an external surveyor to inspect the claim")
    public ResponseEntity<ApiResponse<ClaimResponse>> assignSurveyor(
            @PathVariable UUID id,
            @RequestParam UUID surveyorId,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                claimService.assignSurveyor(id, extractCompanyId(request), surveyorId, extractUserId(request)),
                "Surveyor assigned."));
    }

    @PostMapping("/{id}/surveyor-report")
    @PreAuthorize("hasAuthority('CLAIM_WRITE')")
    @Operation(summary = "Record surveyor assessment report")
    public ResponseEntity<ApiResponse<ClaimResponse>> surveyorReport(
            @PathVariable UUID id,
            @RequestParam BigDecimal assessedAmount,
            @RequestParam String recommendation,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                claimService.recordSurveyorReport(id, extractCompanyId(request), assessedAmount, recommendation, extractUserId(request)),
                "Surveyor report recorded."));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('CLAIM_APPROVE')")
    @Operation(summary = "Approve a claim with an approved amount")
    public ResponseEntity<Object> approve(
            @PathVariable UUID id,
            @RequestParam BigDecimal approvedAmount,
            @RequestParam(required = false) String comments,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        final String endpoint = "POST /claims/{id}/approve";
        record ApprovePayload(UUID id, BigDecimal approvedAmount, String comments) {}
        var payload = new ApprovePayload(id, approvedAmount, comments);
        var cached = idempotencyService.checkExisting(idempotencyKey, endpoint, payload);
        if (cached.isPresent()) return replay(cached.get());

        ApiResponse<ClaimResponse> body = ApiResponse.success(
                claimService.approve(id, extractCompanyId(request), approvedAmount, comments, extractUserId(request)), "Claim approved.");
        idempotencyService.store(idempotencyKey, endpoint, payload, HttpStatus.OK.value(), body);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('CLAIM_APPROVE')")
    @Operation(summary = "Reject a claim with a reason")
    public ResponseEntity<ApiResponse<ClaimResponse>> reject(
            @PathVariable UUID id,
            @RequestParam String reason,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                claimService.reject(id, extractCompanyId(request), reason, extractUserId(request)),
                "Claim rejected."));
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasAuthority('CLAIM_SETTLE')")
    @Operation(summary = "Mark claim as settled after payment disbursement")
    public ResponseEntity<Object> settle(
            @PathVariable UUID id,
            @RequestParam BigDecimal settledAmount,
            @RequestParam String settlementReference,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        final String endpoint = "POST /claims/{id}/settle";
        record SettlePayload(UUID id, BigDecimal settledAmount, String settlementReference) {}
        var payload = new SettlePayload(id, settledAmount, settlementReference);
        var cached = idempotencyService.checkExisting(idempotencyKey, endpoint, payload);
        if (cached.isPresent()) return replay(cached.get());

        ApiResponse<ClaimResponse> body = ApiResponse.success(
                claimService.settle(id, extractCompanyId(request), settledAmount, settlementReference, extractUserId(request)), "Claim settled.");
        idempotencyService.store(idempotencyKey, endpoint, payload, HttpStatus.OK.value(), body);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/flag-fraud")
    @PreAuthorize("hasAuthority('CLAIM_FLAG_FRAUD')")
    @Operation(summary = "Flag a claim for fraud investigation")
    public ResponseEntity<ApiResponse<ClaimResponse>> flagFraud(
            @PathVariable UUID id,
            @RequestParam BigDecimal fraudScore,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                claimService.flagFraud(id, extractCompanyId(request), fraudScore, extractUserId(request)),
                "Claim flagged for fraud review."));
    }

    // ── SLA configuration (Phase 5) — admin-editable, no redeploy needed ──────

    @GetMapping("/sla-config")
    @PreAuthorize("hasAuthority('CLAIM_ASSIGN')")
    @Operation(summary = "List configured SLA thresholds per status/priority")
    public ResponseEntity<ApiResponse<java.util.List<com.insurance.claims.entity.SlaConfig>>> listSlaConfig() {
        return ResponseEntity.ok(ApiResponse.success(slaConfigService.listAll()));
    }

    @PutMapping("/sla-config")
    @PreAuthorize("hasAuthority('CLAIM_ASSIGN')")
    @Operation(summary = "Set (create or update) the SLA threshold for a status/priority combination")
    public ResponseEntity<ApiResponse<com.insurance.claims.entity.SlaConfig>> upsertSlaConfig(
            @RequestParam ClaimStatus status,
            @RequestParam ClaimPriority priority,
            @RequestParam int maxHours) {
        return ResponseEntity.ok(ApiResponse.success(
                slaConfigService.upsert(status, priority, maxHours), "SLA threshold saved."));
    }

    @DeleteMapping("/sla-config/{id}")
    @PreAuthorize("hasAuthority('CLAIM_ASSIGN')")
    @Operation(summary = "Remove an SLA threshold (that status/priority combination becomes untracked)")
    public ResponseEntity<ApiResponse<Void>> deleteSlaConfig(@PathVariable UUID id) {
        slaConfigService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "SLA threshold removed."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Missing user context");
        return UUID.fromString(id.toString());
    }
    private UUID extractCompanyId(HttpServletRequest req) {
        Object id = req.getAttribute("companyId");
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Missing company context");
        return UUID.fromString(id.toString());
    }
    private UUID extractBranchId(HttpServletRequest req) {
        Object id = req.getAttribute("branchId");
        return id != null ? UUID.fromString(id.toString()) : null;
    }
}
