package com.insurance.document.dto.response;

import com.insurance.document.entity.ExtractionTemplate;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ExtractionTemplateResponse {
    private UUID id;
    private String documentType;
    private String issuerCode;
    private String issuerName;
    private List<ExtractionTemplate.FieldRule> fieldRules;
    private boolean active;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static ExtractionTemplateResponse from(ExtractionTemplate t) {
        return ExtractionTemplateResponse.builder()
                .id(t.getId())
                .documentType(t.getDocumentType())
                .issuerCode(t.getIssuerCode())
                .issuerName(t.getIssuerName())
                .fieldRules(t.getFieldRules())
                .active(t.isActive())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
