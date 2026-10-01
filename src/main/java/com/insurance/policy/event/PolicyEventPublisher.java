package com.insurance.policy.event;

import com.insurance.monolith.events.DomainEventBus;
import com.insurance.policy.entity.Policy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyEventPublisher {

    private final DomainEventBus domainEventBus;

    private static final String TOPIC = "policy.events";

    @Async
    public void publishPolicyCreated(Policy policy) {
        publish("POLICY_CREATED", policy, Map.of(
                "premiumAmount", policy.getPremiumAmount().toString(),
                "sumAssured", policy.getSumAssured().toString(),
                "insuranceType", policy.getInsuranceType().name()
        ));
    }

    @Async
    public void publishPolicyIssued(Policy policy) {
        publish("POLICY_ISSUED", policy, Map.of(
                "issuedAt", policy.getIssuedAt().toString()
        ));
    }

    @Async
    public void publishPolicyCancelled(Policy policy) {
        publish("POLICY_CANCELLED", policy, Map.of(
                "reason", policy.getRejectionReason() != null ? policy.getRejectionReason() : ""
        ));
    }

    @Async
    public void publishPolicyLapsed(Policy policy) {
        publish("POLICY_LAPSED", policy, Map.of(
                "lastPaidDate", policy.getLastPaidDate() != null ? policy.getLastPaidDate().toString() : ""
        ));
    }

    @Async
    public void publishRenewalDue(Policy policy) {
        publish("POLICY_RENEWAL_DUE", policy, Map.of(
                "nextDueDate", policy.getNextDueDate().toString(),
                "premiumAmount", policy.getPremiumAmount().toString()
        ));
    }

    @Async
    public void publishPolicyMatured(Policy policy) {
        publish("POLICY_MATURED", policy, Map.of(
                "maturityDate", policy.getMaturityDate().toString()
        ));
    }

    private void publish(String eventType, Policy policy, Map<String, String> extra) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("policyId", policy.getId().toString());
        event.put("policyNumber", policy.getPolicyNumber());
        event.put("customerId", policy.getCustomerId().toString());
        event.put("companyId", policy.getCompanyId().toString());
        if (policy.getAgentId() != null) event.put("agentId", policy.getAgentId().toString());
        event.put("timestamp", ZonedDateTime.now().toString());
        event.putAll(extra);

        domainEventBus.publish(TOPIC, policy.getId().toString(), event);
        log.debug("Published {} for policy {}", eventType, policy.getPolicyNumber());
    }
}
