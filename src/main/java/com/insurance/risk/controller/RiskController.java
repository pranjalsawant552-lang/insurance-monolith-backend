package com.insurance.risk.controller;

import com.insurance.risk.dto.request.RiskAssessmentRequest;
import com.insurance.risk.dto.request.RiskFactorConfigRequest;
import com.insurance.risk.dto.response.ApiResponse;
import com.insurance.risk.dto.response.RiskAssessmentResponse;
import com.insurance.risk.dto.response.RiskFactorConfigResponse;
import com.insurance.risk.entity.RiskAssessment;
import com.insurance.risk.entity.RiskFactorConfig;
import com.insurance.risk.service.RiskAssessmentService;
import com.insurance.risk.service.RiskFactorConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/risk")
@RequiredArgsConstructor
@Tag(name = "Risk Assessment", description = "Underwriting risk scoring")
@SecurityRequirement(name = "bearerAuth")
public class RiskController {

    private final RiskAssessmentService riskAssessmentService;
    private final RiskFactorConfigService riskFactorConfigService;

    @PostMapping("/assess")
    @PreAuthorize("hasAuthority('POLICY_UNDERWRITE') or hasAuthority('POLICY_WRITE')")
    @Operation(summary = "Compute an underwriting risk score — called by policy-service when a policy enters underwriting")
    public ResponseEntity<ApiResponse<RiskAssessmentResponse>> assess(@Valid @RequestBody RiskAssessmentRequest req) {
        RiskAssessment assessment = riskAssessmentService.assess(req);
        return ResponseEntity.ok(ApiResponse.success(RiskAssessmentResponse.from(assessment),
                "Risk assessed: " + assessment.getDecision()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('POLICY_UNDERWRITE') or hasAuthority('POLICY_READ')")
    @Operation(summary = "Get one risk assessment by id")
    public ResponseEntity<ApiResponse<RiskAssessmentResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(RiskAssessmentResponse.from(riskAssessmentService.getOrThrow(id))));
    }

    @GetMapping("/policy/{policyId}/latest")
    @PreAuthorize("hasAuthority('POLICY_UNDERWRITE') or hasAuthority('POLICY_READ')")
    @Operation(summary = "Get the most recent risk assessment for a policy")
    public ResponseEntity<ApiResponse<RiskAssessmentResponse>> getLatestForPolicy(@PathVariable UUID policyId) {
        return riskAssessmentService.getLatestForPolicy(policyId)
                .map(a -> ResponseEntity.ok(ApiResponse.success(RiskAssessmentResponse.from(a))))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success(null, "No risk assessment on file for this policy yet.")));
    }

    // ── Admin: risk factor configuration (the pluggable rules engine's config surface) ──

    @GetMapping("/factors")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "List configured risk factors for an insurance line")
    public ResponseEntity<ApiResponse<List<RiskFactorConfigResponse>>> listFactors(@RequestParam String insuranceType) {
        List<RiskFactorConfigResponse> factors = riskFactorConfigService.listForInsuranceType(insuranceType)
                .stream().map(RiskFactorConfigResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(factors));
    }

    @PostMapping("/factors")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Add a risk factor — no deploy needed to change underwriting rules")
    public ResponseEntity<ApiResponse<RiskFactorConfigResponse>> createFactor(@Valid @RequestBody RiskFactorConfigRequest req) {
        RiskFactorConfig saved = riskFactorConfigService.create(req);
        return ResponseEntity.ok(ApiResponse.success(RiskFactorConfigResponse.from(saved), "Risk factor added."));
    }

    @PutMapping("/factors/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Update a risk factor")
    public ResponseEntity<ApiResponse<RiskFactorConfigResponse>> updateFactor(
            @PathVariable UUID id, @Valid @RequestBody RiskFactorConfigRequest req) {
        RiskFactorConfig saved = riskFactorConfigService.update(id, req);
        return ResponseEntity.ok(ApiResponse.success(RiskFactorConfigResponse.from(saved), "Risk factor updated."));
    }

    @DeleteMapping("/factors/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Remove a risk factor")
    public ResponseEntity<ApiResponse<Void>> deleteFactor(@PathVariable UUID id) {
        riskFactorConfigService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Risk factor deleted."));
    }
}
