package com.insurance.claims.dto.request;

import com.insurance.claims.entity.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateClaimRequest {

    @NotNull(message = "Policy ID is required")
    private UUID policyId;

    @NotBlank(message = "Policy number is required")
    private String policyNumber;

    @NotNull(message = "Insurance type is required")
    private InsuranceType insuranceType;

    @NotBlank(message = "Claim type is required")
    private String claimType;

    @NotNull(message = "Incident date is required")
    @PastOrPresent(message = "Incident date cannot be in the future")
    private LocalDate incidentDate;

    @NotBlank(message = "Description is required")
    @Size(min = 20, message = "Please provide a detailed description (min 20 characters)")
    private String description;

    @NotNull(message = "Claimed amount is required")
    @DecimalMin(value = "1", message = "Claimed amount must be greater than 0")
    private BigDecimal claimedAmount;

    // Type-specific detail objects (one required based on insuranceType)
    private LifeClaimDetailRequest lifeDetails;
    private HealthClaimDetailRequest healthDetails;
    private MotorClaimDetailRequest motorDetails;

    @Data
    public static class LifeClaimDetailRequest {
        @NotBlank private String claimantName;
        @NotBlank private String claimantRelation;
        private String claimantMobile;
        private String causeOfDeath;
        private LocalDate deathDate;
        private String deathPlace;
    }

    @Data
    public static class HealthClaimDetailRequest {
        private String hospitalName;
        private UUID hospitalId;
        private LocalDate admissionDate;
        private LocalDate dischargeDate;
        private String diagnosis;
        private String treatment;
        private boolean cashless;
        private String tpaReference;
    }

    @Data
    public static class MotorClaimDetailRequest {
        @NotBlank private String vehicleNumber;
        private String accidentLocation;
        private String accidentDescription;
        private String policeFirNumber;
        private String garageName;
        private UUID garageId;
        private BigDecimal repairEstimate;
        private boolean totalLoss;
    }
}
