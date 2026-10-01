package com.insurance.notification.consumer;

import com.insurance.notification.service.NotificationEmailService;
import com.insurance.notification.service.NotificationSmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.insurance.monolith.events.DomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerEventConsumer {

    private final NotificationEmailService emailService;
    private final NotificationSmsService smsService;

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'customer.events'")
    public void handle(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String type = (String) event.get("eventType");
        if (type == null) return;

        log.info("Customer event received: {}", type);

        String email  = getString(event, "email");
        String mobile = getString(event, "mobileNumber");
        String name   = getString(event, "firstName");

        switch (type) {
            case "KYC_VERIFIED" -> {
                if (email != null)  emailService.sendKycVerified(email, name);
                if (mobile != null) smsService.sendKycVerified(mobile);
            }
            case "KYC_REJECTED" -> {
                if (email != null)  emailService.sendKycRejected(email, name,
                        getString(event, "reason") != null ? getString(event, "reason") : "Documents could not be verified");
                if (mobile != null) smsService.sendKycRejected(mobile);
            }
            default -> log.debug("Unhandled customer event type: {}", type);
        }
    }

    private String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }
}
