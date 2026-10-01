package com.insurance.policy.dto.request;

import com.insurance.policy.entity.InsuranceType;
import com.insurance.policy.entity.PremiumFrequency;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Creates a Policy record from a policy the insurer already issued outside
 * this platform — the platform's role is to record it, not price or issue
 * it. Used two ways:
 *   1. Built automatically by PolicyIngestionService from a document-service
 *      "policy.document.verified" Kafka event (the normal path).
 *   2. Submitted directly to POST /policies/ingest by an agent/ops user —
 *      either because there was no PDF pipeline run at all, or to resolve a
 *      row that landed in the ingestion-failure queue.
 */
@Data
public class IngestPolicyRequest {

    @NotBlank(message = "Policy number (as printed on the insurer's document) is required")
    private String policyNumber;

    @NotBlank(message = "Insurer name is required")
    private String insurerName;

    private String productName;

    @NotNull(message = "Insurance type is required")
    private InsuranceType insuranceType;

    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    private UUID agentId;

    @NotNull(message = "Sum assured is required")
    @DecimalMin(value = "0", inclusive = false, message = "Sum assured must be positive")
    private BigDecimal sumAssured;

    @NotNull(message = "Premium amount is required")
    @DecimalMin(value = "0", inclusive = false, message = "Premium amount must be positive")
    private BigDecimal premiumAmount;

    /** Defaults to YEARLY if the document didn't make the installment cadence clear. */
    private PremiumFrequency premiumFrequency;

    @NotNull(message = "Commencement date is required")
    private LocalDate commencementDate;

    /** Either maturityDate or policyTermYears must be supplied — whichever the
     *  document actually states; the other is derived. */
    private LocalDate maturityDate;
    private Integer policyTermYears;

    private List<NomineeRequest> nominees;

    /** Set automatically by the Kafka-driven path; left null for a fully manual entry
     *  with no underlying PDF at all. */
    private UUID sourceExtractionId;
    private UUID sourceDocumentId;

    @Data
    public static class NomineeRequest {
        @NotBlank private String fullName;
        private String relationship;
        private LocalDate dateOfBirth;
        private BigDecimal sharePercent;
        private boolean primary;
    }
}
