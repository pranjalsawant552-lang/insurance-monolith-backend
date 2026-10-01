package com.insurance.document.dto.request;

import com.insurance.document.entity.ExtractionTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ExtractionTemplateRequest {

    @NotBlank private String documentType;
    @NotBlank private String issuerCode;
    private String issuerName;

    @NotEmpty private List<ExtractionTemplate.FieldRule> fieldRules;

    private boolean active = true;
}
