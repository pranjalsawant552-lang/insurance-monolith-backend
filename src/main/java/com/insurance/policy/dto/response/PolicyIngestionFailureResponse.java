package com.insurance.policy.dto.response;

import com.insurance.policy.entity.PolicyIngestionFailure;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class PolicyIngestionFailureResponse {
    private UUID id;
    private UUID sourceExtractionId;
    private UUID sourceDocumentId;
    private UUID customerId;
    private Map<String, Object> rawFields;
    private String errorMessage;
    private String status;
    private UUID resolvedPolicyId;
    private ZonedDateTime resolvedAt;
    private ZonedDateTime createdAt;

    public static PolicyIngestionFailureResponse from(PolicyIngestionFailure f) {
        return PolicyIngestionFailureResponse.builder()
                .id(f.getId())
                .sourceExtractionId(f.getSourceExtractionId())
                .sourceDocumentId(f.getSourceDocumentId())
                .customerId(f.getCustomerId())
                .rawFields(f.getRawFields())
                .errorMessage(f.getErrorMessage())
                .status(f.getStatus().name())
                .resolvedPolicyId(f.getResolvedPolicyId())
                .resolvedAt(f.getResolvedAt())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
