package com.insurance.customer.controller;

import com.insurance.customer.dto.request.CreateCustomerRequest;
import com.insurance.customer.dto.response.*;
import com.insurance.customer.entity.KycStatus;
import com.insurance.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management and KYC workflow")
public class CustomerController {

    private final CustomerService customerService;

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    @Operation(summary = "Create a new customer profile")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            @Valid @RequestBody CreateCustomerRequest req,
            HttpServletRequest request) {
        CustomerResponse resp = customerService.create(req,
                extractUserId(request), extractCompanyId(request), extractBranchId(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(resp, "Customer created successfully."));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getById(id, extractCompanyId(request))));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Get customer by customer code")
    public ResponseEntity<ApiResponse<CustomerResponse>> getByCode(@PathVariable String code, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getByCode(code, extractCompanyId(request))));
    }

    // ── List / Search ─────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "List all customers for company (paginated, filterable by KYC status)")
    public ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) KycStatus kycStatus,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                customerService.list(extractCompanyId(request), kycStatus, page, size, sort)));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Full-text search customers by name, email, mobile, or code")
    public ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                customerService.search(extractCompanyId(request), q, page, size)));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    @Operation(summary = "Update customer profile")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCustomerRequest req,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                customerService.update(id, extractCompanyId(request), req, extractUserId(request)),
                "Customer updated successfully."));
    }

    // ── KYC Workflow ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/kyc/submit")
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    @Operation(summary = "Submit customer KYC documents for review")
    public ResponseEntity<ApiResponse<CustomerResponse>> submitKyc(
            @PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                customerService.submitForKycReview(id, extractCompanyId(request), extractUserId(request)),
                "KYC submitted for review."));
    }

    @PostMapping("/{id}/kyc/approve")
    @PreAuthorize("hasAuthority('CUSTOMER_KYC_APPROVE')")
    @Operation(summary = "Approve KYC (underwriter/admin action)")
    public ResponseEntity<ApiResponse<CustomerResponse>> approveKyc(
            @PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                customerService.approveKyc(id, extractCompanyId(request), extractUserId(request)),
                "KYC approved successfully."));
    }

    @PostMapping("/{id}/kyc/reject")
    @PreAuthorize("hasAuthority('CUSTOMER_KYC_APPROVE')")
    @Operation(summary = "Reject KYC with a reason (underwriter/admin action)")
    public ResponseEntity<ApiResponse<CustomerResponse>> rejectKyc(
            @PathVariable UUID id,
            @RequestParam String reason,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                customerService.rejectKyc(id, extractCompanyId(request), reason, extractUserId(request)),
                "KYC rejected."));
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Get customer statistics for dashboard")
    public ResponseEntity<ApiResponse<CustomerStatsResponse>> stats(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                customerService.getStats(extractCompanyId(request))));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    @Operation(summary = "Soft-delete a customer record")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id, HttpServletRequest request) {
        customerService.delete(id, extractCompanyId(request), extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Customer deleted."));
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
