package com.insurance.product.dto.response;

import com.insurance.product.entity.ProductVersion;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ProductVersionResponse {
    private UUID id;
    private UUID productId;
    private int versionNumber;
    private String status;
    private Map<String, Object> configSnapshot;
    private ZonedDateTime effectiveFrom;
    private ZonedDateTime effectiveTo;
    private UUID createdBy;
    private ZonedDateTime createdAt;

    public static ProductVersionResponse from(ProductVersion v) {
        return ProductVersionResponse.builder()
                .id(v.getId())
                .productId(v.getProductId())
                .versionNumber(v.getVersionNumber())
                .status(v.getStatus().name())
                .configSnapshot(v.getConfigSnapshot())
                .effectiveFrom(v.getEffectiveFrom())
                .effectiveTo(v.getEffectiveTo())
                .createdBy(v.getCreatedBy())
                .createdAt(v.getCreatedAt())
                .build();
    }
}
