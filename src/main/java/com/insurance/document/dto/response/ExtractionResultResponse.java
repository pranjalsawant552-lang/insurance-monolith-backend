package com.insurance.document.dto.response;

import com.insurance.document.entity.ExtractionResult;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ExtractionResultResponse {
    private UUID id;
    private UUID documentId;
    private String ownerType;
    private UUID ownerId;
    private String documentType;
    private String issuerCode;
    private String extractionMethod;
    private Map<String, Object> extractedFields;
    private Map<String, Integer> fieldConfidence;
    private Integer overallConfidence;
    private Map<String, Object> verifiedFields;
    private String status;
    private String rawTextSnapshot;
    private UUID reviewedBy;
    private ZonedDateTime reviewedAt;
    private String reviewNotes;
    private ZonedDateTime createdAt;

    public static ExtractionResultResponse from(ExtractionResult e) {
        return ExtractionResultResponse.builder()
                .id(e.getId())
                .documentId(e.getDocumentId())
                .ownerType(e.getOwnerType())
                .ownerId(e.getOwnerId())
                .documentType(e.getDocumentType())
                .issuerCode(e.getIssuerCode())
                .extractionMethod(e.getExtractionMethod() != null ? e.getExtractionMethod().name() : null)
                .extractedFields(e.getExtractedFields())
                .fieldConfidence(e.getFieldConfidence())
                .overallConfidence(e.getOverallConfidence())
                .verifiedFields(e.getVerifiedFields())
                .status(e.getStatus().name())
                .rawTextSnapshot(e.getRawTextSnapshot())
                .reviewedBy(e.getReviewedBy())
                .reviewedAt(e.getReviewedAt())
                .reviewNotes(e.getReviewNotes())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
