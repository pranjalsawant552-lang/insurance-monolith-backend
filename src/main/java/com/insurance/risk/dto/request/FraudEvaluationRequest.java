package com.insurance.risk.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record FraudEvaluationRequest(
        @NotNull UUID claimId,
        @NotNull UUID policyId,
        String policyNumber,
        UUID customerId,
        LocalDate incidentDate
) {}
