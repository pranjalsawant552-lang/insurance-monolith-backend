package com.insurance.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name = "kyc_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KycDocument {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id") private Customer customer;
    @Column(name = "document_type", nullable = false) private String documentType;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "storage_key", nullable = false) private String storageKey;
    @Column(name = "file_size_bytes") private Long fileSizeBytes;
    @Column(name = "mime_type") private String mimeType;
    @Column(name = "ocr_verified") @Builder.Default private boolean ocrVerified = false;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocr_data", columnDefinition = "jsonb") private Map<String, Object> ocrData;
    @Column(nullable = false) @Builder.Default private String status = "PENDING";
    @Column(name = "rejection_reason") private String rejectionReason;
    @Column(name = "uploaded_at", updatable = false) @Builder.Default private ZonedDateTime uploadedAt = ZonedDateTime.now();
    @Column(name = "verified_at") private ZonedDateTime verifiedAt;
    @Column(name = "verified_by") private UUID verifiedBy;
    @Column(name = "is_deleted") @Builder.Default private boolean deleted = false;
}
