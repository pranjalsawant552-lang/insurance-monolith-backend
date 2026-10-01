package com.insurance.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicyDocument {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "policy_id") private Policy policy;
    @Column(name = "document_type", nullable = false) private String documentType;
    @Column(name = "file_name",     nullable = false) private String fileName;
    @Column(name = "storage_key",   nullable = false) private String storageKey;
    @Column(name = "file_size_bytes") private Long fileSizeBytes;
    @Column(name = "mime_type")       private String mimeType;
    @Column(name = "uploaded_by", nullable = false) private UUID uploadedBy;
    @Column(name = "uploaded_at", updatable = false)
    @Builder.Default private ZonedDateTime uploadedAt = ZonedDateTime.now();
    @Column(name = "is_deleted") @Builder.Default private boolean deleted = false;
}
