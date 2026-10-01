package com.insurance.document.controller;

import com.insurance.document.dto.response.ApiResponse;
import com.insurance.document.dto.response.ExtractionResultResponse;
import com.insurance.document.dto.response.PagedResponse;
import com.insurance.document.entity.ExtractionResult;
import com.insurance.document.service.ExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * The verification queue an ops/agent reviewer works from — every
 * automatically-extracted document sits here until a human confirms or
 * corrects it. This is the safety net described to the client: automatic
 * reading is fast, but nothing becomes the official record without a
 * person looking at it first.
 */
@RestController
@RequestMapping("/extractions")
@RequiredArgsConstructor
@Tag(name = "Document Extraction", description = "PDF/OCR extraction results and the human verification queue")
@SecurityRequirement(name = "bearerAuth")
public class ExtractionController {

    private final ExtractionService extractionService;

    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "List extraction results — the verification queue, filterable by status and document type")
    public ResponseEntity<ApiResponse<PagedResponse<ExtractionResultResponse>>> queue(
            @RequestParam(required = false) ExtractionResult.ExtractionStatus status,
            @RequestParam(required = false) String documentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = extractionService.queue(status, documentType, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(result.map(ExtractionResultResponse::from))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "Get one extraction result — the raw text and extracted fields a reviewer checks against the document")
    public ResponseEntity<ApiResponse<ExtractionResultResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(ExtractionResultResponse.from(extractionService.getOrThrow(id))));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('DOCUMENT_VERIFY')")
    @Operation(summary = "Confirm an extraction — submit the reviewer's final field values (corrected or as-is)")
    public ResponseEntity<ApiResponse<ExtractionResultResponse>> verify(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> correctedFields,
            @RequestParam(required = false) String notes,
            HttpServletRequest request) {
        ExtractionResult result = extractionService.verify(id, correctedFields, notes, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(ExtractionResultResponse.from(result), "Extraction verified."));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('DOCUMENT_VERIFY')")
    @Operation(summary = "Reject an extraction — the document was wrong, unreadable, or not what it claimed to be")
    public ResponseEntity<ApiResponse<ExtractionResultResponse>> reject(
            @PathVariable UUID id,
            @RequestParam String reason,
            HttpServletRequest request) {
        ExtractionResult result = extractionService.reject(id, reason, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(ExtractionResultResponse.from(result), "Extraction rejected."));
    }

    @PostMapping("/{id}/request-better-scan")
    @PreAuthorize("hasAuthority('DOCUMENT_VERIFY')")
    @Operation(summary = "Ask the uploader for a clearer copy — the document was too poor quality to extract meaningfully")
    public ResponseEntity<ApiResponse<ExtractionResultResponse>> requestBetterScan(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {
        ExtractionResult result = extractionService.requestBetterScan(id, reason, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(ExtractionResultResponse.from(result), "Marked as needing a better scan."));
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing user context");
        return UUID.fromString(id.toString());
    }
}
