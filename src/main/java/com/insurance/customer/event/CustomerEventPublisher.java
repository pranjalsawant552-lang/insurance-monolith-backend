package com.insurance.customer.event;

import com.insurance.customer.entity.Customer;
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
public class CustomerEventPublisher {

    private final DomainEventBus domainEventBus;
    private static final String TOPIC = "customer.events";

    @Async public void publishCustomerCreated(Customer c)  { publish("CUSTOMER_CREATED", c, Map.of()); }
    @Async public void publishKycSubmitted(Customer c)     { publish("KYC_SUBMITTED", c, Map.of()); }
    @Async public void publishKycVerified(Customer c)      { publish("KYC_VERIFIED", c, Map.of()); }
    @Async public void publishKycRejected(Customer c, String reason) {
        publish("KYC_REJECTED", c, Map.of("reason", reason));
    }

    private void publish(String type, Customer c, Map<String, String> extra) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", type);
        event.put("customerId", c.getId().toString());
        event.put("customerCode", c.getCustomerCode());
        event.put("companyId", c.getCompanyId().toString());
        event.put("email", c.getEmail());
        event.put("mobileNumber", c.getMobileNumber());
        event.put("kycStatus", c.getKycStatus().name());
        event.put("timestamp", ZonedDateTime.now().toString());
        event.putAll(extra);
        domainEventBus.publish(TOPIC, c.getId().toString(), event);
    }
}
