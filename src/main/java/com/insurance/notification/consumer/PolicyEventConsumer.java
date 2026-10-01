package com.insurance.notification.consumer;

import com.insurance.notification.client.CustomerServiceClient;
import com.insurance.notification.service.NotificationEmailService;
import com.insurance.notification.service.NotificationSmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.insurance.monolith.events.DomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("notificationPolicyEventConsumer")
@RequiredArgsConstructor
public class PolicyEventConsumer {

    private final NotificationEmailService emailService;
    private final NotificationSmsService smsService;
    private final CustomerServiceClient customerServiceClient;

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'policy.events'")
    public void handle(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String type = (String) event.get("eventType");
        if (type == null) return;

        log.info("Policy event received: {}", type);

        // FIX (Phase 7): the event itself never carries customerEmail/customerMobile/
        // customerName (see PolicyEventPublisher — only customerId is published). Every
        // notification send here was previously a silent no-op because of it. Resolve
        // contact details from customerId instead; still fall back to the event's own
        // fields first in case a future publisher change starts including them directly.
        String email  = getString(event, "customerEmail");
        String mobile = getString(event, "customerMobile");
        String name   = getString(event, "customerName");
        if (email == null || mobile == null) {
            UUID customerId = getUuid(event, "customerId");
            var contact = customerServiceClient.resolveContact(customerId);
            if (contact.isPresent()) {
                if (email == null) email = contact.get().email();
                if (mobile == null) mobile = contact.get().mobile();
                if (name == null) name = contact.get().name();
            }
        }
        String policyNo   = getString(event, "policyNumber");

        switch (type) {
            case "POLICY_ISSUED" -> {
                if (email != null) emailService.sendPolicyIssued(email, name, policyNo,
                        getString(event, "insuranceType"),
                        getString(event, "sumAssured"),
                        getString(event, "premiumAmount"),
                        getString(event, "commencementDate"));
                if (mobile != null) smsService.sendPolicyIssued(mobile, policyNo);
            }
            case "POLICY_RENEWAL_DUE" -> {
                if (email != null) emailService.sendPolicyRenewalDue(email, name, policyNo,
                        getString(event, "nextDueDate"),
                        getString(event, "premiumAmount"));
                if (mobile != null) smsService.sendPremiumDue(mobile, policyNo,
                        getString(event, "premiumAmount"),
                        getString(event, "nextDueDate"));
            }
            case "POLICY_LAPSED" -> {
                if (email != null) emailService.sendPolicyLapsed(email, name, policyNo);
                if (mobile != null) smsService.sendPolicyLapsed(mobile, policyNo);
            }
            case "POLICY_CANCELLED" -> {
                if (email != null) emailService.sendPolicyCancelled(email, name, policyNo,
                        getString(event, "reason"));
            }
            default -> log.debug("Unhandled policy event type: {}", type);
        }
    }

    private String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private UUID getUuid(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
