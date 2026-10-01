package com.insurance.product.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.product.entity.*;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductResponse {
    private UUID id;
    private String code;
    private String name;
    private InsuranceType insuranceType;
    private String category;
    private String description;
    private String keyFeatures;
    private BigDecimal basePremium;
    private BigDecimal minSumAssured;
    private BigDecimal maxSumAssured;
    private Integer minAge;
    private Integer maxAge;
    private Integer minTermYears;
    private Integer maxTermYears;
    private int gracePeriodDays;
    private int freeLookDays;
    private boolean active;
    private boolean featured;
    private UUID companyId;
    private List<RiderResponse> riders;
    private ZonedDateTime createdAt;

    public static ProductResponse from(InsuranceProduct p) {
        return ProductResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .insuranceType(p.getInsuranceType())
                .category(p.getCategory())
                .description(p.getDescription())
                .keyFeatures(p.getKeyFeatures())
                .basePremium(p.getBasePremium())
                .minSumAssured(p.getMinSumAssured())
                .maxSumAssured(p.getMaxSumAssured())
                .minAge(p.getMinAge())
                .maxAge(p.getMaxAge())
                .minTermYears(p.getMinTermYears())
                .maxTermYears(p.getMaxTermYears())
                .gracePeriodDays(p.getGracePeriodDays())
                .freeLookDays(p.getFreeLookDays())
                .active(p.isActive())
                .featured(p.isFeatured())
                .companyId(p.getCompanyId())
                .riders(p.getRiders() != null
                        ? p.getRiders().stream().filter(ProductRider::isActive)
                                .map(RiderResponse::from).collect(Collectors.toList())
                        : List.of())
                .createdAt(p.getCreatedAt())
                .build();
    }

    @Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RiderResponse {
        private UUID id;
        private String name;
        private String code;
        private String description;
        private BigDecimal additionalPremium;

        public static RiderResponse from(ProductRider r) {
            return RiderResponse.builder()
                    .id(r.getId()).name(r.getName()).code(r.getCode())
                    .description(r.getDescription())
                    .additionalPremium(r.getAdditionalPremium())
                    .build();
        }
    }
}
