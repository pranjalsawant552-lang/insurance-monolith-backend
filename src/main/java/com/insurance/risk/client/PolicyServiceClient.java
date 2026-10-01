package com.insurance.risk.client;

import com.insurance.policy.dto.response.PolicyResponse;
import com.insurance.policy.service.PolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * MONOLITH CONVERSION NOTE: originally an HTTP call to policy-service.
 * Now a direct, in-process call to PolicyService.getById.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyServiceClient {

    private final PolicyService policyService;

    public Optional<LocalDate> getPolicyCommencementDate(UUID policyId) {
        try {
            PolicyResponse policy = policyService.getByIdInternal(policyId);
            return Optional.ofNullable(policy.getCommencementDate());
        } catch (RuntimeException e) {
            log.warn("Could not fetch policy {} for fraud evaluation: {}", policyId, e.getMessage());
            return Optional.empty();
        }
    }
}
