package com.insurance.document.controller;

import com.insurance.document.dto.request.ExtractionTemplateRequest;
import com.insurance.document.dto.response.ApiResponse;
import com.insurance.document.dto.response.ExtractionTemplateResponse;
import com.insurance.document.entity.ExtractionTemplate;
import com.insurance.document.service.ExtractionTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Lets an ops team add/edit extraction rules for a new insurer's document
 * layout without a code deploy — the whole point of building extraction as
 * data-driven templates instead of hardcoded per-insurer parsers.
 */
@RestController
@RequestMapping("/extraction-templates")
@RequiredArgsConstructor
@Tag(name = "Extraction Templates", description = "Admin-configurable field-extraction rules per document type and issuer")
@SecurityRequirement(name = "bearerAuth")
public class ExtractionTemplateController {

    private final ExtractionTemplateService service;

    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "List extraction templates for a document type")
    public ResponseEntity<ApiResponse<List<ExtractionTemplateResponse>>> list(@RequestParam String documentType) {
        List<ExtractionTemplateResponse> templates = service.listForDocumentType(documentType)
                .stream().map(ExtractionTemplateResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DOCUMENT_WRITE')")
    @Operation(summary = "Create an extraction template for a (documentType, issuerCode) combination")
    public ResponseEntity<ApiResponse<ExtractionTemplateResponse>> create(
            @Valid @RequestBody ExtractionTemplateRequest req, HttpServletRequest request) {
        ExtractionTemplate saved = service.create(req, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(ExtractionTemplateResponse.from(saved), "Extraction template created."));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_WRITE')")
    @Operation(summary = "Update an extraction template's field rules or active state")
    public ResponseEntity<ApiResponse<ExtractionTemplateResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody ExtractionTemplateRequest req, HttpServletRequest request) {
        ExtractionTemplate saved = service.update(id, req, extractUserId(request));
        return ResponseEntity.ok(ApiResponse.success(ExtractionTemplateResponse.from(saved), "Extraction template updated."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_WRITE')")
    @Operation(summary = "Delete an extraction template")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Extraction template deleted."));
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing user context");
        return UUID.fromString(id.toString());
    }
}
