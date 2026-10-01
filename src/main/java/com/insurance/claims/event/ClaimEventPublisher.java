package com.insurance.claims.event;

import com.insurance.claims.entity.Claim;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.insurance.monolith.events.DomainEventBus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimEventPublisher {

    private final DomainEventBus domainEventBus;
    private static final String TOPIC = "claim.events";

    @Async public void publishClaimSubmitted(Claim c)  { publish("CLAIM_SUBMITTED", c, Map.of()); }
    @Async public void publishClaimApproved(Claim c)   {
        publish("CLAIM_APPROVED", c, Map.of("approvedAmount", c.getApprovedAmount().toString()));
    }
    @Async public void publishClaimRejected(Claim c)   {
        publish("CLAIM_REJECTED", c, Map.of("reason", c.getRejectionReason() != null ? c.getRejectionReason() : ""));
    }
    @Async public void publishClaimSettled(Claim c)    {
        publish("CLAIM_SETTLED", c, Map.of("settledAmount", c.getSettledAmount().toString()));
    }
    @Async public void publishFraudFlagged(Claim c)    {
        publish("CLAIM_FRAUD_FLAGGED", c, Map.of("fraudScore", c.getFraudScore() != null ? c.getFraudScore().toString() : ""));
    }
    @Async public void publishSlaBreached(Claim c) {
        publish("CLAIM_SLA_BREACHED", c, Map.of(
                "status", c.getStatus().name(),
                "assignedTo", c.getAssignedTo() != null ? c.getAssignedTo().toString() : "",
                "slaDueAt", c.getSlaDueAt() != null ? c.getSlaDueAt().toString() : ""));
    }

    private void publish(String type, Claim c, Map<String, String> extra) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", type);
        event.put("claimId", c.getId().toString());
        event.put("claimNumber", c.getClaimNumber());
        event.put("policyId", c.getPolicyId().toString());
        event.put("customerId", c.getCustomerId().toString());
        event.put("companyId", c.getCompanyId().toString());
        event.put("insuranceType", c.getInsuranceType().name());
        event.put("claimedAmount", c.getClaimedAmount().toString());
        event.put("timestamp", ZonedDateTime.now().toString());
        event.putAll(extra);
        domainEventBus.publish(TOPIC, c.getId().toString(), event);
    }
}
