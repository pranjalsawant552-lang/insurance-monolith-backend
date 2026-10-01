package com.insurance.document.controller;

import com.insurance.document.dto.response.*;
import com.insurance.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document upload, storage, OCR, and verification")
public class DocumentController {

    private final DocumentService documentService;

    // ── Upload ────────────────────────────────────────────────────────────────

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENT_WRITE')")
    @Operation(summary = "Upload a document (max 10 MB — JPEG, PNG, PDF, TIFF, WEBP)",
               description = "For documentType=POLICY_SCHEDULE (or POLICY_DOCUMENT/POLICY_CERTIFICATE), pass "
                       + "insuranceType (LIFE/HEALTH/MOTOR) if known — it's used directly when the policy is "
                       + "created from this document's verified extraction, instead of being guessed from OCR "
                       + "text. productName is optional but enables automatic renewal-linking.")
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestParam("file")         MultipartFile file,
            @RequestParam("ownerType")    String ownerType,
            @RequestParam("ownerId")      UUID ownerId,
            @RequestParam("documentType") String documentType,
            @RequestParam(value = "issuerCode", required = false)    String issuerCode,
            @RequestParam(value = "insuranceType", required = false) String insuranceType,
            @RequestParam(value = "productName", required = false)   String productName,
            HttpServletRequest request) {

        DocumentResponse resp = documentService.upload(
                file, ownerType, ownerId, documentType, issuerCode, insuranceType, productName,
                extractCompanyId(request), extractUserId(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(resp, "Document uploaded successfully."));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "Get document metadata by ID")
    public ResponseEntity<ApiResponse<DocumentResponse>> getById(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean withUrl,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.getById(id, extractCompanyId(request), withUrl)));
    }

    @GetMapping("/number/{docNumber}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "Get document metadata by document number")
    public ResponseEntity<ApiResponse<DocumentResponse>> getByNumber(
            @PathVariable String docNumber,
            @RequestParam(defaultValue = "false") boolean withUrl,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.getByNumber(docNumber, extractCompanyId(request), withUrl)));
    }

    @GetMapping("/{id}/download-url")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "Generate a pre-signed download URL (max 60 min expiry)")
    public ResponseEntity<ApiResponse<String>> getDownloadUrl(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "15") int expiryMinutes,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.getDownloadUrl(id, extractCompanyId(request), expiryMinutes),
                "Pre-signed URL valid for " + Math.min(expiryMinutes, 60) + " minutes."));
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping("/owner/{ownerType}/{ownerId}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "List all documents for an owner (policy, claim, customer, KYC)")
    public ResponseEntity<ApiResponse<PagedResponse<DocumentResponse>>> listByOwner(
            @PathVariable String ownerType,
            @PathVariable UUID ownerId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.listByOwner(ownerId, ownerType, page, size)));
    }

    @GetMapping("/owner/{ownerType}/{ownerId}/count")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "Count documents for an owner")
    public ResponseEntity<ApiResponse<Long>> countByOwner(
            @PathVariable String ownerType,
            @PathVariable UUID ownerId) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.countByOwner(ownerId, ownerType)));
    }

    // ── Verification ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('DOCUMENT_VERIFY')")
    @Operation(summary = "Mark document as verified (admin/underwriter)")
    public ResponseEntity<ApiResponse<DocumentResponse>> verify(
            @PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.verify(id, extractCompanyId(request), extractUserId(request)),
                "Document verified."));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('DOCUMENT_VERIFY')")
    @Operation(summary = "Reject document with a reason")
    public ResponseEntity<ApiResponse<DocumentResponse>> reject(
            @PathVariable UUID id,
            @RequestParam String reason,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.reject(id, extractCompanyId(request), reason, extractUserId(request)),
                "Document rejected."));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_DELETE')")
    @Operation(summary = "Soft-delete a document (file retained in storage for audit)")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id, HttpServletRequest request) {
        documentService.delete(id, extractCompanyId(request), extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Document deleted."));
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
}
