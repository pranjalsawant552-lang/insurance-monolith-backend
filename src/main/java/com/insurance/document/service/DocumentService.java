package com.insurance.document.service;

import com.insurance.document.dto.response.*;
import com.insurance.document.entity.*;
import com.insurance.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final OcrService ocrService;
    private final com.insurance.document.event.AuditEventPublisher auditEventPublisher;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp",
            "application/pdf",
            "image/tiff"
    );

    // ── Upload ────────────────────────────────────────────────────────────────

    @Transactional
    public DocumentResponse upload(MultipartFile file, String ownerType, UUID ownerId,
                                   String documentType, String issuerCode, String insuranceType,
                                   String productName, UUID companyId, UUID uploadedBy) {
        validateFile(file);

        String folder = ownerType.toLowerCase() + "/" + ownerId;
        String storageKey = storageService.upload(file, folder);
        String docNumber  = generateDocNumber(documentType);

        String checksum;
        try {
            checksum = DigestUtils.md5Hex(file.getInputStream());
        } catch (Exception e) {
            checksum = null;
        }

        // Determine if OCR is applicable
        boolean shouldOcr = shouldRunOcr(documentType, file.getContentType());

        Document doc = Document.builder()
                .documentNumber(docNumber)
                .ownerType(ownerType.toUpperCase())
                .ownerId(ownerId)
                .companyId(companyId)
                .documentType(documentType.toUpperCase())
                .issuerCode(issuerCode != null && !issuerCode.isBlank() ? issuerCode.toUpperCase() : null)
                .insuranceType(insuranceType != null && !insuranceType.isBlank() ? insuranceType.toUpperCase() : null)
                .productName(productName != null && !productName.isBlank() ? productName.trim() : null)
                .originalName(file.getOriginalFilename())
                .storageKey(storageKey)
                .storageBucket(storageService.getDefaultBucket())
                .fileSizeBytes(file.getSize())
                .mimeType(file.getContentType())
                .checksumMd5(checksum)
                .ocrStatus(shouldOcr ? OcrStatus.PENDING : OcrStatus.SKIPPED)
                .uploadedBy(uploadedBy)
                .build();

        Document saved = documentRepository.save(doc);

        // Trigger OCR asynchronously
        if (shouldOcr) {
            ocrService.processDocument(saved);
        }

        log.info("Document uploaded: {} owner={}/{}", docNumber, ownerType, ownerId);
        return DocumentResponse.from(saved);
    }

    // ── Get with pre-signed URL ───────────────────────────────────────────────

    /**
     * SECURITY FIX: previously took no companyId and returned any document
     * in the database — including generating a real, working presigned
     * download URL for it — regardless of the caller's company. Cross-
     * tenant IDOR on the most sensitive data this app holds (audit report
     * §7.1). Now scoped; 404 (not 403) on mismatch.
     */
    public DocumentResponse getById(UUID id, UUID companyId, boolean withDownloadUrl) {
        Document doc = findOrThrow(id, companyId);
        DocumentResponse resp = DocumentResponse.from(doc);
        if (withDownloadUrl) {
            resp.setDownloadUrl(storageService.generatePresignedUrl(doc.getStorageKey(), 15));
        }
        return resp;
    }

    public DocumentResponse getByNumber(String docNumber, UUID companyId, boolean withDownloadUrl) {
        Document doc = documentRepository.findByDocumentNumberAndDeletedFalse(docNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found: " + docNumber));
        if (!doc.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + docNumber);
        }
        DocumentResponse resp = DocumentResponse.from(doc);
        if (withDownloadUrl) {
            resp.setDownloadUrl(storageService.generatePresignedUrl(doc.getStorageKey(), 15));
        }
        return resp;
    }

    // ── Generate a short-lived download URL ───────────────────────────────────

    public String getDownloadUrl(UUID id, UUID companyId, int expiryMinutes) {
        Document doc = findOrThrow(id, companyId);
        int expiry = Math.min(expiryMinutes, 60); // max 1 hour
        return storageService.generatePresignedUrl(doc.getStorageKey(), expiry);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    public PagedResponse<DocumentResponse> listByOwner(UUID ownerId, String ownerType,
                                                        int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("createdAt").descending());
        return PagedResponse.of(documentRepository
                .findByOwnerIdAndOwnerTypeAndDeletedFalse(ownerId, ownerType.toUpperCase(), pageable)
                .map(DocumentResponse::from));
    }

    // ── Verify document (admin/underwriter action) ────────────────────────────

    @Transactional
    public DocumentResponse verify(UUID id, UUID companyId, UUID verifiedBy) {
        Document doc = findOrThrow(id, companyId);
        doc.setVerified(true);
        doc.setVerifiedBy(verifiedBy);
        doc.setVerifiedAt(ZonedDateTime.now());
        doc.setRejectionNote(null);
        Document saved = documentRepository.save(doc);
        auditEventPublisher.publish("DOCUMENT", saved.getId().toString(), "VERIFY",
                java.util.Map.of("verified", "false"),
                java.util.Map.of("verified", "true"),
                "Document " + saved.getDocumentNumber() + " (" + saved.getDocumentType() + ") verified");
        return DocumentResponse.from(saved);
    }

    // ── Reject document ───────────────────────────────────────────────────────

    @Transactional
    public DocumentResponse reject(UUID id, UUID companyId, String reason, UUID rejectedBy) {
        Document doc = findOrThrow(id, companyId);
        doc.setVerified(false);
        doc.setRejectionNote(reason);
        Document saved = documentRepository.save(doc);
        auditEventPublisher.publish("DOCUMENT", saved.getId().toString(), "REJECT",
                java.util.Map.of("verified", "false"),
                java.util.Map.of("verified", "false", "rejectionNote", reason != null ? reason : ""),
                "Document " + saved.getDocumentNumber() + " rejected: " + (reason != null ? reason : "no reason given"));
        return DocumentResponse.from(saved);
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id, UUID companyId, UUID deletedBy) {
        Document doc = findOrThrow(id, companyId);
        doc.setDeleted(true);
        doc.setDeletedAt(ZonedDateTime.now());
        doc.setDeletedBy(deletedBy);
        documentRepository.save(doc);
        auditEventPublisher.publish("DOCUMENT", doc.getId().toString(), "DELETE",
                java.util.Map.of("deleted", "false"),
                java.util.Map.of("deleted", "true"),
                "Document " + doc.getDocumentNumber() + " soft-deleted");
        // Note: we do NOT delete from storage immediately — retained for audit
        log.info("Document soft-deleted: {}", doc.getDocumentNumber());
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public long countByOwner(UUID ownerId, String ownerType) {
        return documentRepository.countByOwnerIdAndOwnerTypeAndDeletedFalse(
                ownerId, ownerType.toUpperCase());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Document findOrThrow(UUID id, UUID companyId) {
        Document doc = documentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found: " + id));
        // SECURITY: 404, not 403 — see audit report §7.1.
        if (!doc.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + id);
        }
        return doc;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File size exceeds 10 MB limit.");
        }
        if (!ALLOWED_MIME_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File type not allowed. Permitted: JPEG, PNG, WEBP, PDF, TIFF.");
        }
    }

    private String generateDocNumber(String docType) {
        String prefix = "DOC-" + docType.substring(0, Math.min(3, docType.length())).toUpperCase();
        String number = prefix + "-" + String.valueOf(System.currentTimeMillis()).substring(3);
        while (documentRepository.existsByDocumentNumber(number)) {
            number = prefix + "-" + System.currentTimeMillis();
        }
        return number;
    }

    private boolean shouldRunOcr(String docType, String mimeType) {
        if (mimeType == null) return false;
        // Only run OCR on identity documents and medical/financial documents
        List<String> ocrTypes = List.of("AADHAAR", "PAN", "PASSPORT", "DRIVING_LICENSE",
                "RC_BOOK", "VEHICLE_RC", "HOSPITAL_BILL", "MEDICAL_BILL",
                "DEATH_CERTIFICATE", "DISCHARGE_SUMMARY");
        return ocrTypes.stream().anyMatch(t -> docType.toUpperCase().contains(t));
    }
}
