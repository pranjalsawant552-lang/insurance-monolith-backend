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
@Component
@RequiredArgsConstructor
public class ClaimEventConsumer {

    // FIXED IN PHASE 7 (found in Phase 5, left unfixed then — see the Phase 5
    // changelog for the original discovery). ClaimEventPublisher still only
    // publishes claim-internal fields, not contact details — the fix lives here
    // instead, resolving contact details from customerId via CustomerServiceClient,
    // same approach as PolicyEventConsumer and PaymentEventConsumer.

    private final NotificationEmailService emailService;
    private final NotificationSmsService smsService;
    private final CustomerServiceClient customerServiceClient;

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'claim.events'")
    public void handle(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String type = (String) event.get("eventType");
        if (type == null) return;

        log.info("Claim event received: {}", type);

        String email     = getString(event, "customerEmail");
        String mobile    = getString(event, "customerMobile");
        String name      = getString(event, "customerName");
        if (email == null || mobile == null) {
            UUID customerId = getUuid(event, "customerId");
            var contact = customerServiceClient.resolveContact(customerId);
            if (contact.isPresent()) {
                if (email == null) email = contact.get().email();
                if (mobile == null) mobile = contact.get().mobile();
                if (name == null) name = contact.get().name();
            }
        }
        String claimNo   = getString(event, "claimNumber");
        String policyNo  = getString(event, "policyNumber");

        switch (type) {
            case "CLAIM_SUBMITTED" -> {
                if (email != null) emailService.sendClaimSubmitted(email, name, claimNo,
                        policyNo,
                        getString(event, "insuranceType"),
                        getString(event, "claimedAmount"));
                if (mobile != null) smsService.sendClaimSubmitted(mobile, claimNo);
            }
            case "CLAIM_APPROVED" -> {
                if (email != null) emailService.sendClaimApproved(email, name, claimNo,
                        getString(event, "approvedAmount"));
                if (mobile != null) smsService.sendClaimApproved(mobile, claimNo,
                        getString(event, "approvedAmount"));
            }
            case "CLAIM_REJECTED" -> {
                if (email != null) emailService.sendClaimRejected(email, name, claimNo,
                        getString(event, "reason"));
                if (mobile != null) smsService.sendClaimRejected(mobile, claimNo);
            }
            case "CLAIM_SETTLED" -> {
                if (email != null) emailService.sendClaimSettled(email, name, claimNo,
                        getString(event, "settledAmount"));
                if (mobile != null) smsService.sendClaimSettled(mobile, claimNo,
                        getString(event, "settledAmount"));
            }
            case "CLAIM_SLA_BREACHED" -> {
                // No staff-directory lookup exists yet to resolve "assignedTo" (a UUID)
                // into an email/Slack handle — that needs an auth-service call this
                // service doesn't make today. Logging at WARN is a real, if minimal,
                // signal an ops dashboard or log-based alert (e.g. a Grafana/Loki rule
                // on this log line) can act on immediately; a proper email/Slack alert
                // to the assigned officer is flagged as follow-up work, not faked here.
                log.warn("SLA BREACH: claim {} in status {} assigned to {} was due {}",
                        claimNo, getString(event, "status"), getString(event, "assignedTo"), getString(event, "slaDueAt"));
            }
            default -> log.debug("Unhandled claim event type: {}", type);
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
