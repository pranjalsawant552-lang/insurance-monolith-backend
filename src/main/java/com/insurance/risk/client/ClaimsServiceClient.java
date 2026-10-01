package com.insurance.risk.client;

import com.insurance.claims.entity.Claim;
import com.insurance.claims.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MONOLITH CONVERSION NOTE:
 *
 * Originally this class made an HTTP call to claims-service.
 * Since all services are now inside the same monolith, this class
 * directly accesses ClaimRepository for read-only claim history.
 *
 * IMPORTANT:
 * This class intentionally does NOT depend on ClaimService.
 * This prevents the following circular dependency:
 *
 * ClaimService
 *      ↓
 * RiskFraudServiceClient
 *      ↓
 * FraudRuleEngineService
 *      ↓
 * ClaimsServiceClient
 *      ↓
 * ClaimService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimsServiceClient {

    private final ClaimRepository claimRepository;

    public record ClaimSummary(
            UUID id,
            String policyNumber,
            String status,
            LocalDate incidentDate
    ) {
    }

    /**
     * Returns recent claims for a customer.
     *
     * This method directly queries the repository instead of going
     * through ClaimService so that the risk module does not create
     * a dependency cycle with the claims module.
     */
    public List<ClaimSummary> getRecentClaimsForCustomer(
            UUID customerId,
            int lookbackDays
    ) {

        try {
            Page<Claim> page = claimRepository.findByCustomerIdAndDeletedFalse(
                    customerId,
                    PageRequest.of(
                            0,
                            50,
                            Sort.by("createdAt").descending()
                    )
            );

            List<Claim> claims = page.getContent();

            if (claims == null || claims.isEmpty()) {
                return List.of();
            }

            LocalDate cutoff = LocalDate.now().minusDays(lookbackDays);

            List<ClaimSummary> summaries = new ArrayList<>();

            for (Claim claim : claims) {

                LocalDate incidentDate = claim.getIncidentDate();

                /*
                 * Keep claims where incidentDate is:
                 *
                 * - null
                 * - equal to cutoff
                 * - after cutoff
                 *
                 * Skip claims older than the requested lookback period.
                 */
                if (incidentDate != null && incidentDate.isBefore(cutoff)) {
                    continue;
                }

                summaries.add(
                        new ClaimSummary(
                                claim.getId(),
                                claim.getPolicyNumber(),
                                claim.getStatus() != null
                                        ? claim.getStatus().name()
                                        : null,
                                incidentDate
                        )
                );
            }

            return summaries;

        } catch (RuntimeException e) {

            /*
             * Fraud detection should not block claim submission
             * when historical claim lookup unexpectedly fails.
             */
            log.warn(
                    "Could not fetch claim history for customer {}: {}",
                    customerId,
                    e.getMessage(),
                    e
            );

            return List.of();
        }
    }
}