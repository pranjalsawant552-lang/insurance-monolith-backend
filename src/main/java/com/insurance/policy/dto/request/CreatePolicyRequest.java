package com.insurance.policy.dto.request;

import com.insurance.policy.entity.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreatePolicyRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    private UUID agentId;

    @NotNull(message = "Sum assured is required")
    @DecimalMin(value = "1000", message = "Sum assured must be at least 1000")
    private BigDecimal sumAssured;

    @NotNull(message = "Premium frequency is required")
    private PremiumFrequency premiumFrequency;

    @NotNull(message = "Policy term (years) is required")
    @Min(value = 1, message = "Minimum policy term is 1 year")
    private Integer policyTermYears;

    @NotNull(message = "Commencement date is required")
    @FutureOrPresent(message = "Commencement date must be today or in the future")
    private LocalDate commencementDate;

    private List<UUID> riderIds;

    // ── Rating inputs (Phase 3) — passed through to product-service's rating
    // engine. All optional: a rating factor only applies if a matching
    // AGE_BAND/RISK_SCORE_BAND/VEHICLE_TYPE/LOCATION row exists for the
    // product version, so omitting these simply means those factors don't fire.
    private Integer age;
    private BigDecimal riskScore;
    private String location;
    private String vehicleType;

    // ── Underwriting inputs (Phase 4) — passed through to risk-fraud-service's
    // risk engine to decide auto-approve vs manual review. Distinct from the
    // pricing inputs above: "riskScore" there is a pre-known pricing input
    // (e.g. an agent's estimate); the fields below feed the underwriting
    // *decision* itself, computed fresh by risk-fraud-service every time.
    private BigDecimal bmi;
    private Boolean smoker;
    private Boolean preExistingCondition;
    private String occupationClass;
    private BigDecimal annualIncome;
    private Integer priorClaimsCount;
    private Integer vehicleAge;
    private String locationRiskZone;

    // Life-specific
    private LifeDetailsRequest lifeDetails;

    // Health-specific
    private HealthDetailsRequest healthDetails;

    // Motor-specific
    private MotorDetailsRequest motorDetails;

    // Nominees (required for life)
    private List<NomineeRequest> nominees;

    @Data
    public static class LifeDetailsRequest {
        private String planType;
    }

    @Data
    public static class HealthDetailsRequest {
        private String planType;
        private BigDecimal roomRentLimit;
        private boolean maternityCovered;
        private List<HealthMemberRequest> insuredMembers;
    }

    @Data
    public static class HealthMemberRequest {
        @NotBlank private String fullName;
        @NotBlank private String relationship;
        @NotNull private LocalDate dateOfBirth;
        private String gender;
    }

    @Data
    public static class MotorDetailsRequest {
        @NotBlank private String vehicleNumber;
        @NotBlank private String vehicleMake;
        @NotBlank private String vehicleModel;
        @NotNull @Min(1900) private Integer vehicleYear;
        @NotBlank private String vehicleType;
        private String engineNumber;
        private String chassisNumber;
        private Integer cubicCapacity;
        private Integer seatingCapacity;
        @NotNull private BigDecimal idvValue;
        @NotBlank private String policyType;
        private BigDecimal ncbPercent;
    }

    @Data
    public static class NomineeRequest {
        @NotBlank private String fullName;
        @NotBlank private String relationship;
        private LocalDate dateOfBirth;
        @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal sharePercent;
        private boolean primary;
        private String mobile;
        private String email;
    }
}
