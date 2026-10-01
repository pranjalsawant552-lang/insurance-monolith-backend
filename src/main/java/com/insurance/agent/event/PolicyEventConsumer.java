package com.insurance.agent.event;

import com.insurance.agent.service.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.insurance.monolith.events.DomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("agentPolicyEventConsumer")
@RequiredArgsConstructor
public class PolicyEventConsumer {

    private final CommissionService commissionService;

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'policy.events'")
    public void handle(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String type = (String) event.get("eventType");
        if (type == null) return;

        try {
            switch (type) {
                case "POLICY_ISSUED" -> {
                    String agentIdStr = (String) event.get("agentId");
                    if (agentIdStr == null || agentIdStr.isBlank()) return;

                    commissionService.calculateFirstYearCommission(
                            UUID.fromString(agentIdStr),
                            UUID.fromString((String) event.get("policyId")),
                            (String) event.get("policyNumber"),
                            UUID.fromString((String) event.get("customerId")),
                            UUID.fromString((String) event.get("companyId")),
                            (String) event.get("insuranceType"),
                            new BigDecimal((String) event.get("premiumAmount"))
                    );
                }
                case "POLICY_RENEWAL_DUE" -> {
                    // Renewal commission is lower rate — triggered when customer pays
                    log.debug("Renewal commission will be calculated on payment confirmation");
                }
                default -> {}
            }
        } catch (Exception e) {
            log.error("Failed to process policy event {} for commission: {}", type, e.getMessage(), e);
        }
    }
}
