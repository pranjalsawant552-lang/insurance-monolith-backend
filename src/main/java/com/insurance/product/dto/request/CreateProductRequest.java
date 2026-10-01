package com.insurance.product.dto.request;

import com.insurance.product.entity.InsuranceType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateProductRequest {

    @NotBlank @Size(max = 50) private String code;
    @NotBlank @Size(max = 255) private String name;
    @NotNull private InsuranceType insuranceType;

    private String category;

    @NotBlank private String description;
    private String keyFeatures;           // JSON array string e.g. ["No medical test","Online issuance"]

    @NotNull @DecimalMin("1") private BigDecimal basePremium;
    private BigDecimal minSumAssured;
    private BigDecimal maxSumAssured;

    @Min(0) @Max(100) private Integer minAge;
    @Min(0) @Max(100) private Integer maxAge;
    @Min(1) private Integer minTermYears;
    @Min(1) private Integer maxTermYears;

    private Integer gracePeriodDays;
    private Integer freeLookDays;
    private boolean featured;

    private List<RiderRequest> riders;

    @Data
    public static class RiderRequest {
        @NotBlank private String name;
        @NotBlank private String code;
        private String description;
        @NotNull @DecimalMin("0") private BigDecimal additionalPremium;
    }
}
