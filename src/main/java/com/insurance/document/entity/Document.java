package com.insurance.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Document {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "document_number", nullable = false, unique = true)
    private String documentNumber;

    @Column(name = "owner_type", nullable = false) private String ownerType;
    @Column(name = "owner_id",   nullable = false) private UUID ownerId;
    @Column(name = "company_id", nullable = false) private UUID companyId;

    @Column(name = "document_type",   nullable = false) private String documentType;

    /** Which insurer/authority issued this document, when known (e.g. STAR_HEALTH, HDFC_ERGO,
     *  GOVT_IN) — lets extraction pick the right ExtractionTemplate directly instead of
     *  guessing from document content. Optional: an agent may not always know/specify it,
     *  in which case extraction falls back to content-based template matching. */
    @Column(name = "issuer_code") private String issuerCode;

    // Phase 10: captured at upload time instead of inferred from OCR text — see
    // V3__capture_insurance_type_at_upload.sql for why. Both nullable: only meaningful
    // for POLICY_SCHEDULE-type uploads, and productName is optional even then.
    @Column(name = "insurance_type") private String insuranceType;
    @Column(name = "product_name")   private String productName;

    @Column(name = "original_name",   nullable = false) private String originalName;
    @Column(name = "storage_key",     nullable = false) private String storageKey;
    @Builder.Default
    @Column(name = "storage_bucket",  nullable = false) private String storageBucket = "insurance-documents";
    @Column(name = "file_size_bytes", nullable = false) private Long fileSizeBytes;
    @Column(name = "mime_type",       nullable = false) private String mimeType;
    @Column(name = "checksum_md5") private String checksumMd5;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "ocr_status", nullable = false) private OcrStatus ocrStatus = OcrStatus.PENDING;

    @Column(name = "ocr_text", columnDefinition = "TEXT") private String ocrText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocr_data", columnDefinition = "jsonb") private Map<String, Object> ocrData;

    @Column(name = "ocr_confidence") private java.math.BigDecimal ocrConfidence;

    @Column(name = "is_verified") @Builder.Default private boolean verified = false;
    @Column(name = "verified_by")  private UUID verifiedBy;
    @Column(name = "verified_at")  private ZonedDateTime verifiedAt;
    @Column(name = "rejection_note") private String rejectionNote;

    @Column(name = "is_public") @Builder.Default private boolean publicAccess = false;
    @Column(name = "uploaded_by", nullable = false) private UUID uploadedBy;

    @Column(name = "is_deleted") @Builder.Default private boolean deleted = false;
    @Column(name = "deleted_at") private ZonedDateTime deletedAt;
    @Column(name = "deleted_by") private UUID deletedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
    @Column(name = "updated_at", nullable = false)
    @Builder.Default private ZonedDateTime updatedAt = ZonedDateTime.now();

    @PreUpdate public void preUpdate() { this.updatedAt = ZonedDateTime.now(); }
}
