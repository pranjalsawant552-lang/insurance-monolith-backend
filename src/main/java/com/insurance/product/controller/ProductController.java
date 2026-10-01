package com.insurance.product.controller;

import com.insurance.product.dto.request.CreateProductRequest;
import com.insurance.product.dto.request.RatingFactorRequest;
import com.insurance.product.dto.response.*;
import com.insurance.product.entity.InsuranceType;
import com.insurance.product.entity.ProductVersion;
import com.insurance.product.entity.RatingFactor;
import com.insurance.product.service.ProductService;
import com.insurance.product.service.ProductVersionService;
import com.insurance.product.service.RatingFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Insurance product and plan configuration")
public class ProductController {

    private final ProductService productService;
    private final ProductVersionService productVersionService;
    private final RatingFactorService ratingFactorService;

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Create a new insurance product/plan")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody CreateProductRequest req,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                productService.create(req, extractCompanyId(request),
                        extractBranchId(request), extractUserId(request)),
                "Product created successfully."));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getById(id)));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "Get product by product code")
    public ResponseEntity<ApiResponse<ProductResponse>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(productService.getByCode(code)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "List all products (paginated, filterable by type and active status)")
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) InsuranceType type,
            @RequestParam(required = false) Boolean active,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.list(extractCompanyId(request), type, active, page, size)));
    }

    @GetMapping("/featured")
    @Operation(summary = "Get featured products for customer portal homepage")
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> featured(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(productService.getFeatured(page, size)));
    }

    @GetMapping("/premium-preview")
    @Operation(summary = "Calculate premium for a product with given parameters (inc. GST)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> premiumPreview(
            @RequestParam UUID productId,
            @RequestParam BigDecimal sumAssured,
            @RequestParam(defaultValue = "YEARLY") String frequency,
            @RequestParam(defaultValue = "10") int termYears,
            @RequestParam(required = false) List<UUID> riderIds,
            @RequestParam(required = false) Integer age,
            @RequestParam(required = false) BigDecimal riskScore,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String vehicleType,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.calculatePremiumPreview(productId, sumAssured,
                        frequency, termYears, riderIds, age, riskScore, location, vehicleType,
                        extractUserIdOrNull(request)),
                "Premium calculated inclusive of 18% GST. calculationId can be quoted back at policy issuance."));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Update product details")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateProductRequest req,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.update(id, req, extractUserId(request)),
                "Product updated."));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Activate a product")
    public ResponseEntity<ApiResponse<ProductResponse>> activate(
            @PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.toggleActive(id, true, extractUserId(request)),
                "Product activated."));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Deactivate a product (stops new policies from being created)")
    public ResponseEntity<ApiResponse<ProductResponse>> deactivate(
            @PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.toggleActive(id, false, extractUserId(request)),
                "Product deactivated."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Soft-delete a product")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id, HttpServletRequest request) {
        productService.delete(id, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Product deleted."));
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Missing user context");
        return UUID.fromString(id.toString());
    }

    /** Like extractUserId, but tolerant of anonymous callers — used only by the
     *  deliberately-public premium-preview endpoint (see gateway route
     *  product-service-public). Returns null rather than throwing when no
     *  X-User-Id header is present. */
    private UUID extractUserIdOrNull(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        return id != null ? UUID.fromString(id.toString()) : null;
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

    // ── Product versioning ──────────────────────────────────────────────────

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "List every version of a product, newest first — the history of what changed and when")
    public ResponseEntity<ApiResponse<List<ProductVersionResponse>>> listVersions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ProductVersion> versions = productVersionService.listVersions(id, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                versions.map(ProductVersionResponse::from).getContent()));
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "Get the exact config snapshot for one version — what a policy issued under it was actually sold on")
    public ResponseEntity<ApiResponse<ProductVersionResponse>> getVersion(
            @PathVariable UUID id, @PathVariable int versionNumber) {
        return ResponseEntity.ok(ApiResponse.success(
                ProductVersionResponse.from(productVersionService.getVersion(id, versionNumber))));
    }

    // ── Rating factors (the pluggable rules engine's admin surface) ─────────

    @GetMapping("/{id}/versions/{versionNumber}/rating-factors")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "List the rating factors configured for one product version")
    public ResponseEntity<ApiResponse<List<RatingFactorResponse>>> listRatingFactors(
            @PathVariable UUID id, @PathVariable int versionNumber) {
        List<RatingFactorResponse> factors = ratingFactorService.listForVersion(id, versionNumber)
                .stream().map(RatingFactorResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(factors));
    }

    @PostMapping("/{id}/versions/{versionNumber}/rating-factors")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Add a rating factor to a product version — no deploy needed to change pricing rules")
    public ResponseEntity<ApiResponse<RatingFactorResponse>> createRatingFactor(
            @PathVariable UUID id, @PathVariable int versionNumber,
            @Valid @RequestBody RatingFactorRequest req,
            HttpServletRequest request) {
        RatingFactor saved = ratingFactorService.create(id, versionNumber, req, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(RatingFactorResponse.from(saved), "Rating factor added."));
    }

    @PutMapping("/rating-factors/{factorId}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Update a rating factor")
    public ResponseEntity<ApiResponse<RatingFactorResponse>> updateRatingFactor(
            @PathVariable UUID factorId,
            @Valid @RequestBody RatingFactorRequest req,
            HttpServletRequest request) {
        RatingFactor saved = ratingFactorService.update(factorId, req, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(RatingFactorResponse.from(saved), "Rating factor updated."));
    }

    @DeleteMapping("/rating-factors/{factorId}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Remove a rating factor")
    public ResponseEntity<ApiResponse<Void>> deleteRatingFactor(@PathVariable UUID factorId) {
        ratingFactorService.delete(factorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Rating factor deleted."));
    }
}
