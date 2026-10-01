package com.insurance.risk.controller;

import com.insurance.risk.dto.request.FraudEvaluationRequest;
import com.insurance.risk.dto.request.FraudRuleConfigRequest;
import com.insurance.risk.dto.response.ApiResponse;
import com.insurance.risk.dto.response.FraudEvaluationResponse;
import com.insurance.risk.dto.response.FraudRuleConfigResponse;
import com.insurance.risk.entity.FraudEvaluation;
import com.insurance.risk.entity.FraudRuleConfig;
import com.insurance.risk.service.FraudRuleConfigService;
import com.insurance.risk.service.FraudRuleEngineService;
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
@RequestMapping("/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud Detection", description = "Automated claims fraud rule engine")
@SecurityRequirement(name = "bearerAuth")
public class FraudController {

    private final FraudRuleEngineService fraudRuleEngineService;
    private final FraudRuleConfigService fraudRuleConfigService;

    @PostMapping("/evaluate")
    @PreAuthorize("hasAuthority('CLAIM_WRITE') or hasAuthority('CLAIM_FLAG_FRAUD')")
    @Operation(summary = "Run automated fraud rules against a claim — called by claims-service on submit")
    public ResponseEntity<ApiResponse<FraudEvaluationResponse>> evaluate(@Valid @RequestBody FraudEvaluationRequest req) {
        FraudEvaluation evaluation = fraudRuleEngineService.evaluate(req);
        return ResponseEntity.ok(ApiResponse.success(FraudEvaluationResponse.from(evaluation),
                evaluation.isRecommendFlag() ? "Fraud indicators found — recommend review." : "No fraud indicators triggered."));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLAIM_READ') or hasAuthority('CLAIM_FLAG_FRAUD')")
    @Operation(summary = "Get one fraud evaluation by id")
    public ResponseEntity<ApiResponse<FraudEvaluationResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(FraudEvaluationResponse.from(fraudRuleEngineService.getOrThrow(id))));
    }

    // ── Admin: fraud rule configuration ─────────────────────────────────────

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('CLAIM_FLAG_FRAUD')")
    @Operation(summary = "List all configured fraud rules and their thresholds")
    public ResponseEntity<ApiResponse<List<FraudRuleConfigResponse>>> listRules() {
        List<FraudRuleConfigResponse> rules = fraudRuleConfigService.listAll()
                .stream().map(FraudRuleConfigResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(rules));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('CLAIM_FLAG_FRAUD')")
    @Operation(summary = "Update a fraud rule's threshold, weight, or enabled state")
    public ResponseEntity<ApiResponse<FraudRuleConfigResponse>> updateRule(
            @PathVariable UUID id, @Valid @RequestBody FraudRuleConfigRequest req) {
        FraudRuleConfig saved = fraudRuleConfigService.update(id, req);
        return ResponseEntity.ok(ApiResponse.success(FraudRuleConfigResponse.from(saved), "Fraud rule updated."));
    }
}
