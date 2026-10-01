package com.insurance.document.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.document.entity.*;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentResponse {
    private UUID id;
    private String documentNumber;
    private String ownerType;
    private UUID ownerId;
    private String documentType;
    private String issuerCode;
    private String originalName;
    private Long fileSizeBytes;
    private String mimeType;
    private OcrStatus ocrStatus;
    private Map<String, Object> ocrData;
    private BigDecimal ocrConfidence;
    private boolean verified;
    private ZonedDateTime verifiedAt;
    private String rejectionNote;
    private ZonedDateTime createdAt;
    private String downloadUrl; // pre-signed, set when explicitly requested

    public static DocumentResponse from(Document d) {
        return DocumentResponse.builder()
                .id(d.getId())
                .documentNumber(d.getDocumentNumber())
                .ownerType(d.getOwnerType())
                .ownerId(d.getOwnerId())
                .documentType(d.getDocumentType())
                .issuerCode(d.getIssuerCode())
                .originalName(d.getOriginalName())
                .fileSizeBytes(d.getFileSizeBytes())
                .mimeType(d.getMimeType())
                .ocrStatus(d.getOcrStatus())
                .ocrData(d.getOcrData())
                .ocrConfidence(d.getOcrConfidence())
                .verified(d.isVerified())
                .verifiedAt(d.getVerifiedAt())
                .rejectionNote(d.getRejectionNote())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
