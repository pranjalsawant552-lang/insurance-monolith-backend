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
public class PaymentEventConsumer {

    // FIXED IN PHASE 7 — same contact-field gap Phase 5 found in ClaimEventConsumer
    // and Phase 7 also fixed in PolicyEventConsumer: PaymentEventPublisher never sets
    // customerEmail/customerMobile/customerName either, only customerId.

    private final NotificationEmailService emailService;
    private final NotificationSmsService smsService;
    private final CustomerServiceClient customerServiceClient;

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'payment.events'")
    public void handle(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String type = (String) event.get("eventType");
        if (type == null) return;

        log.info("Payment event received: {}", type);

        String email    = getString(event, "customerEmail");
        String mobile   = getString(event, "customerMobile");
        String name     = getString(event, "customerName");
        if (email == null || mobile == null) {
            UUID customerId = getUuid(event, "customerId");
            var contact = customerServiceClient.resolveContact(customerId);
            if (contact.isPresent()) {
                if (email == null) email = contact.get().email();
                if (mobile == null) mobile = contact.get().mobile();
                if (name == null) name = contact.get().name();
            }
        }
        String policyNo = getString(event, "policyNumber");
        String amount   = getString(event, "amount");

        switch (type) {
            case "PAYMENT_SUCCESS" -> {
                if (email != null) emailService.sendPaymentSuccess(email, name,
                        getString(event, "transactionNumber"),
                        policyNo, amount,
                        getString(event, "paymentMethod") != null
                                ? getString(event, "paymentMethod") : "Online");
                if (mobile != null) smsService.sendPaymentSuccess(mobile,
                        getString(event, "transactionNumber"), amount);
            }
            case "PAYMENT_FAILED" -> {
                if (email != null) emailService.sendPaymentFailed(email, name,
                        policyNo, amount,
                        getString(event, "reason") != null
                                ? getString(event, "reason") : "Payment declined");
                if (mobile != null) smsService.sendPaymentFailed(mobile, policyNo, amount);
            }
            case "PREMIUM_OVERDUE" -> {
                if (email != null) emailService.sendPremiumOverdue(email, name,
                        policyNo, amount, getString(event, "dueDate"));
                if (mobile != null) smsService.sendPremiumOverdue(mobile, policyNo, amount);
            }
            default -> log.debug("Unhandled payment event type: {}", type);
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
