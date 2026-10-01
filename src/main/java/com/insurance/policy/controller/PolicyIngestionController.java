package com.insurance.policy.controller;

import com.insurance.policy.dto.request.IngestPolicyRequest;
import com.insurance.policy.dto.response.ApiResponse;
import com.insurance.policy.dto.response.PagedResponse;
import com.insurance.policy.dto.response.PolicyIngestionFailureResponse;
import com.insurance.policy.dto.response.PolicyResponse;
import com.insurance.policy.entity.PolicyIngestionFailure;
import com.insurance.policy.repository.PolicyIngestionFailureRepository;
import com.insurance.policy.service.PolicyIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * The post-pivot front door for creating a Policy record: the insurer
 * already issued the policy, so this is "record what happened", not
 * "apply, underwrite, issue". The normal path is fully automatic (see
 * PolicyDocumentVerifiedConsumer) — these endpoints exist for the two
 * cases automation doesn't cover: a PDF pipeline run that couldn't be
 * fully mapped (the ingestion-failure queue), and entry with no
 * underlying PDF at all.
 */
@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
@Tag(name = "Policy Ingestion", description = "Recording insurer-issued policies — manual entry and the automatic-ingestion failure queue")
public class PolicyIngestionController {

    private final PolicyIngestionService ingestionService;
    private final PolicyIngestionFailureRepository failureRepository;

    @PostMapping("/ingest")
    @PreAuthorize("hasAuthority('POLICY_WRITE')")
    @Operation(summary = "Record a policy the insurer already issued — manual entry (no PDF pipeline run, or one not available)")
    public ResponseEntity<ApiResponse<PolicyResponse>> ingest(
            @Valid @RequestBody IngestPolicyRequest req,
            HttpServletRequest request) {
        PolicyResponse policy = ingestionService.ingestManual(req, extractCompanyId(request), extractUserId(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(policy, "Policy recorded."));
    }

    @GetMapping("/ingestion-failures")
    @PreAuthorize("hasAuthority('POLICY_READ')")
    @Operation(summary = "The ops queue: extractions that were verified but couldn't be auto-turned into a Policy record")
    public ResponseEntity<ApiResponse<PagedResponse<PolicyIngestionFailureResponse>>> ingestionFailures(
            @RequestParam(required = false) PolicyIngestionFailure.FailureStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        UUID companyId = extractCompanyId(request);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        var results = status != null
                ? failureRepository.findByCompanyIdAndStatus(companyId, status, pageable)
                : failureRepository.findByCompanyId(companyId, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(results.map(PolicyIngestionFailureResponse::from))));
    }

    @GetMapping("/ingestion-failures/{id}")
    @PreAuthorize("hasAuthority('POLICY_READ')")
    @Operation(summary = "Get one ingestion failure — the verified fields as document-service sent them, plus why it couldn't be filed automatically")
    public ResponseEntity<ApiResponse<PolicyIngestionFailureResponse>> getIngestionFailure(@PathVariable UUID id) {
        PolicyIngestionFailure failure = failureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion failure not found: " + id));
        return ResponseEntity.ok(ApiResponse.success(PolicyIngestionFailureResponse.from(failure)));
    }

    @PostMapping("/ingestion-failures/{id}/resolve")
    @PreAuthorize("hasAuthority('POLICY_WRITE')")
    @Operation(summary = "Fix and complete a failed ingestion by hand — submit the corrected/completed field values to create the Policy")
    public ResponseEntity<ApiResponse<PolicyResponse>> resolveIngestionFailure(
            @PathVariable UUID id,
            @Valid @RequestBody IngestPolicyRequest req,
            HttpServletRequest request) {
        PolicyResponse policy = ingestionService.resolveFailure(id, req, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(policy, "Ingestion failure resolved — policy recorded."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private UUID extractUserId(HttpServletRequest request) {
        Object id = request.getAttribute("userId");
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user context");
        return UUID.fromString(id.toString());
    }

    private UUID extractCompanyId(HttpServletRequest request) {
        Object id = request.getAttribute("companyId");
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing company context");
        return UUID.fromString(id.toString());
    }
}
