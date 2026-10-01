package com.insurance.document.service;

import com.insurance.document.entity.*;
import com.insurance.document.repository.DocumentRepository;
import com.insurance.document.repository.ExtractionResultRepository;
import com.insurance.document.repository.ExtractionTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Replaces what used to be a complete fake: the previous version of this
 * class never opened the uploaded file and returned hardcoded placeholder
 * strings ("Extracted from document", a fixed 94.50% confidence) regardless
 * of what was actually uploaded. Found while starting Phase 8 — see the
 * changelog for the full detail.
 *
 * Real flow now:
 *  1. Download the file from storage.
 *  2. If it's a PDF: try direct text extraction (PdfTextExtractionService).
 *     If that yields too little text (likely scanned), fall back to OCR.
 *  3. If it's a plain image: OCR directly.
 *  4. Find the best-matching ExtractionTemplate for this document's type
 *     (and issuer, if known) and apply its field rules to the raw text.
 *  5. Save the result as a PENDING_REVIEW ExtractionResult — never write
 *     extracted-but-unverified data directly onto the Document as if it
 *     were trustworthy. A human confirms it via ExtractionController
 *     before anything downstream (e.g. policy-service) should consume it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final OcrEngineService ocrEngineService;
    private final FieldExtractionService fieldExtractionService;
    private final ExtractionTemplateRepository templateRepository;
    private final ExtractionResultRepository extractionResultRepository;

    private static final List<String> PDF_MIME_TYPES = List.of("application/pdf");

    @Async
    @Transactional
    public void processDocument(Document document) {
        log.info("Starting extraction for document {} type={}", document.getId(), document.getDocumentType());

        document.setOcrStatus(OcrStatus.PROCESSING);
        documentRepository.save(document);

        String rawText;
        ExtractionResult.ExtractionMethod method;

        try (InputStream stream = storageService.download(document.getStorageKey())) {
            boolean isPdf = document.getMimeType() != null && PDF_MIME_TYPES.contains(document.getMimeType().toLowerCase());

            if (isPdf) {
                PdfTextExtractionService.ExtractionOutcome textOutcome = pdfTextExtractionService.extractText(stream);
                if (!textOutcome.likelyScanned()) {
                    rawText = textOutcome.text();
                    method = ExtractionResult.ExtractionMethod.TEXT;
                } else {
                    log.info("Document {} looks like a scanned PDF (too little embedded text) — falling back to OCR", document.getId());
                    // Re-download: the text-extraction stream is already consumed.
                    try (InputStream ocrStream = storageService.download(document.getStorageKey())) {
                        rawText = ocrEngineService.ocrPdf(ocrStream);
                        method = ExtractionResult.ExtractionMethod.OCR;
                    }
                }
            } else {
                rawText = ocrEngineService.ocrImage(stream);
                method = ExtractionResult.ExtractionMethod.OCR;
            }
        } catch (Exception e) {
            log.error("Extraction failed for document {}: {}", document.getId(), e.getMessage(), e);
            document.setOcrStatus(OcrStatus.FAILED);
            documentRepository.save(document);
            return;
        }

        ExtractionTemplate template = resolveTemplate(document, rawText);

        ExtractionResult.ExtractionResultBuilder resultBuilder = ExtractionResult.builder()
                .documentId(document.getId())
                .ownerType(document.getOwnerType())
                .ownerId(document.getOwnerId())
                .companyId(document.getCompanyId())
                .documentType(document.getDocumentType())
                .extractionMethod(method)
                .rawTextSnapshot(truncate(rawText, 20_000))
                .status(ExtractionResult.ExtractionStatus.PENDING_REVIEW);

        if (template != null) {
            FieldExtractionService.ExtractionOutcome fieldOutcome = fieldExtractionService.apply(rawText, template);
            resultBuilder
                    .templateId(template.getId())
                    .issuerCode(template.getIssuerCode())
                    .extractedFields(fieldOutcome.fields())
                    .fieldConfidence(fieldOutcome.confidence())
                    .overallConfidence(fieldOutcome.overallConfidence());
        } else {
            log.info("No extraction template found for documentType={} issuerCode={} — saving raw text only, no structured fields",
                    document.getDocumentType(), document.getIssuerCode());
            resultBuilder.overallConfidence(0);
        }

        ExtractionResult result = extractionResultRepository.save(resultBuilder.build());

        // Document.ocrStatus/ocrData/ocrConfidence stay as a quick-glance summary for
        // screens that only look at the Document record — the ExtractionResult row above
        // is the actual source of truth a reviewer acts on.
        document.setOcrStatus(OcrStatus.COMPLETED);
        document.setOcrText(truncate(rawText, 10_000));
        document.setOcrData(result.getExtractedFields());
        document.setOcrConfidence(result.getOverallConfidence() != null
                ? BigDecimal.valueOf(result.getOverallConfidence()) : BigDecimal.ZERO);
        documentRepository.save(document);

        log.info("Extraction completed for document {} (result {}), confidence={}",
                document.getId(), result.getId(), result.getOverallConfidence());
    }

    // ── Scheduled: process any pending OCR documents ──────────────────────────

    @Transactional
    public void processPendingOcr() {
        documentRepository.findByOcrStatusAndDeletedFalse(OcrStatus.PENDING)
                .forEach(this::processDocument);
    }

    // ── Template resolution ─────────────────────────────────────────────────

    private ExtractionTemplate resolveTemplate(Document document, String rawText) {
        // 1) If the uploader specified an issuer, use it directly.
        if (document.getIssuerCode() != null) {
            Optional<ExtractionTemplate> exact = templateRepository.findByDocumentTypeAndIssuerCodeAndActiveTrue(
                    document.getDocumentType(), document.getIssuerCode());
            if (exact.isPresent()) return exact.get();
        }

        // 2) Otherwise, try to detect the issuer by looking for its name in the extracted text.
        List<ExtractionTemplate> candidates = templateRepository.findByDocumentTypeOrderByIssuerCodeAsc(document.getDocumentType());
        String haystack = rawText == null ? "" : rawText.toUpperCase();
        for (ExtractionTemplate t : candidates) {
            if (t.isActive() && !"GENERIC".equals(t.getIssuerCode())
                    && t.getIssuerName() != null && haystack.contains(t.getIssuerName().toUpperCase())) {
                return t;
            }
        }

        // 3) Fall back to a GENERIC template for this document type, if one exists.
        return candidates.stream()
                .filter(t -> t.isActive() && "GENERIC".equals(t.getIssuerCode()))
                .findFirst()
                .orElse(null);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
