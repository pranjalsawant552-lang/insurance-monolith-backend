package com.insurance.customer.event;

import com.insurance.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.insurance.monolith.events.DomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("customerAuthEventConsumer")
@RequiredArgsConstructor
public class AuthEventConsumer {

    private final CustomerService customerService;

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'auth.user.events'")
    public void onAuthEvent(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String eventType = (String) event.get("eventType");
        if (!"USER_REGISTERED".equals(eventType)) return;

        try {
            UUID userId    = UUID.fromString((String) event.get("userId"));
            String email   = (String) event.get("email");
            String firstName = (String) event.get("firstName");
            String lastName  = (String) event.get("lastName");
            String mobile  = (String) event.get("mobileNumber");

            customerService.handleUserRegistered(userId, email, firstName, lastName, mobile);
        } catch (Exception e) {
            log.error("Failed to handle USER_REGISTERED event: {}", e.getMessage(), e);
        }
    }
}
