package com.insurance.agent.controller;

import com.insurance.agent.dto.request.CreateAgentRequest;
import com.insurance.agent.dto.response.*;
import com.insurance.agent.entity.AgentStatus;
import com.insurance.agent.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
@Tag(name = "Agents", description = "Agent and broker management, commissions")
public class AgentController {

    private final AgentService agentService;
    private final CommissionService commissionService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('AGENT_WRITE')")
    @Operation(summary = "Register a new agent or broker")
    public ResponseEntity<ApiResponse<AgentResponse>> create(
            @Valid @RequestBody CreateAgentRequest req,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                agentService.create(req, extractCompanyId(request),
                        extractBranchId(request), extractUserId(request)),
                "Agent registered successfully."));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AGENT_READ')")
    @Operation(summary = "Get agent by ID")
    public ResponseEntity<ApiResponse<AgentResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(agentService.getById(id)));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('AGENT_READ')")
    @Operation(summary = "Get agent by agent code")
    public ResponseEntity<ApiResponse<AgentResponse>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(agentService.getByCode(code)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AGENT_READ')")
    @Operation(summary = "List all agents (paginated, filterable by status)")
    public ResponseEntity<ApiResponse<PagedResponse<AgentResponse>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) AgentStatus status,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                agentService.list(extractCompanyId(request), status, page, size, sort)));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('AGENT_READ')")
    @Operation(summary = "Search agents by name, email, mobile, or code")
    public ResponseEntity<ApiResponse<PagedResponse<AgentResponse>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                agentService.search(extractCompanyId(request), q, page, size)));
    }

    @GetMapping("/{id}/team")
    @PreAuthorize("hasAuthority('AGENT_READ')")
    @Operation(summary = "List agents under a manager")
    public ResponseEntity<ApiResponse<PagedResponse<AgentResponse>>> listTeam(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(agentService.listTeam(id, page, size)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('AGENT_WRITE')")
    @Operation(summary = "Update agent profile")
    public ResponseEntity<ApiResponse<AgentResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAgentRequest req,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                agentService.update(id, req, extractUserId(request)),
                "Agent updated successfully."));
    }

    @PostMapping("/{id}/assign-manager")
    @PreAuthorize("hasAuthority('AGENT_WRITE')")
    @Operation(summary = "Assign a manager to an agent")
    public ResponseEntity<ApiResponse<AgentResponse>> assignManager(
            @PathVariable UUID id,
            @RequestParam UUID managerId,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                agentService.assignManager(id, managerId, extractUserId(request)),
                "Manager assigned."));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('AGENT_WRITE')")
    @Operation(summary = "Change agent status (ACTIVE, INACTIVE, SUSPENDED, TERMINATED)")
    public ResponseEntity<ApiResponse<AgentResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestParam AgentStatus status,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                agentService.updateStatus(id, status, extractUserId(request)),
                "Agent status updated."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('AGENT_WRITE')")
    @Operation(summary = "Soft-delete an agent record")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id, HttpServletRequest request) {
        agentService.delete(id, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Agent deleted."));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('AGENT_READ')")
    @Operation(summary = "Agent statistics for dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                agentService.getStats(extractCompanyId(request))));
    }

    // ── Commissions ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/commissions")
    @PreAuthorize("hasAuthority('AGENT_READ')")
    @Operation(summary = "List commissions for an agent")
    public ResponseEntity<ApiResponse<PagedResponse<CommissionResponse>>> listCommissions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                commissionService.listByAgent(id, page, size)));
    }

    @GetMapping("/{id}/commissions/summary")
    @PreAuthorize("hasAuthority('AGENT_READ')")
    @Operation(summary = "Earnings summary for an agent")
    public ResponseEntity<ApiResponse<Map<String, Object>>> commissionSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                commissionService.getEarningsSummary(id)));
    }

    @PostMapping("/commissions/{commissionId}/approve")
    @PreAuthorize("hasAuthority('AGENT_COMMISSION_APPROVE')")
    @Operation(summary = "Approve a pending commission")
    public ResponseEntity<ApiResponse<CommissionResponse>> approveCommission(
            @PathVariable UUID commissionId,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                commissionService.approve(commissionId, extractUserId(request)),
                "Commission approved."));
    }

    @PostMapping("/commissions/{commissionId}/mark-paid")
    @PreAuthorize("hasAuthority('AGENT_COMMISSION_APPROVE')")
    @Operation(summary = "Mark commission as paid with payment reference")
    public ResponseEntity<ApiResponse<CommissionResponse>> markPaid(
            @PathVariable UUID commissionId,
            @RequestParam String paymentReference,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                commissionService.markPaid(commissionId, paymentReference, extractUserId(request)),
                "Commission marked as paid."));
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
