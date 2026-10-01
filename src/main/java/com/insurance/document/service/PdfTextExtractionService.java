package com.insurance.document.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Real PDF text extraction using Apache PDFBox — replaces the previous
 * OcrService, which never opened the uploaded file at all and returned a
 * hardcoded placeholder string regardless of content (found while starting
 * this work; see the Phase 8 changelog).
 *
 * PDFBox reads a PDF's embedded text directly — no OCR needed — which
 * covers the large majority of insurer-issued policy documents today,
 * since those are digitally generated, not scanned. A PDF that yields very
 * little text per page is a signal it's a scanned/photographed document
 * instead, and should fall back to {@link OcrEngineService}.
 */
@Slf4j
@Service
public class PdfTextExtractionService {

    /** Below this many characters per page, on average, treat the PDF as likely-scanned rather than trusting a near-empty extraction. */
    private static final int MIN_CHARS_PER_PAGE_FOR_TEXT_PDF = 40;

    public record ExtractionOutcome(String text, int pageCount, boolean likelyScanned) {}

    public ExtractionOutcome extractText(InputStream pdfStream) throws IOException {
        try (PDDocument document = PDDocument.load(pdfStream)) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            int meaningfulChars = text.replaceAll("\\s+", "").length();
            boolean likelyScanned = pageCount > 0 && (meaningfulChars / (double) pageCount) < MIN_CHARS_PER_PAGE_FOR_TEXT_PDF;

            log.debug("PDF text extraction: {} pages, {} chars, likelyScanned={}", pageCount, meaningfulChars, likelyScanned);
            return new ExtractionOutcome(text, pageCount, likelyScanned);
        }
    }
}
