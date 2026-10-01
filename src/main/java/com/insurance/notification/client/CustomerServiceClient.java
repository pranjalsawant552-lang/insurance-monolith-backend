package com.insurance.notification.client;

import com.insurance.customer.dto.response.CustomerResponse;
import com.insurance.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * MONOLITH CONVERSION NOTE: originally an HTTP call to customer-service.
 * Now a direct, in-process call to CustomerService. Fails soft in the same
 * way as before: if the customer can't be found (or any runtime exception
 * is thrown), returns empty rather than propagating — the calling consumer
 * already has an `if (email != null)` guard.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceClient {

    private final CustomerService customerService;

    public record ContactInfo(String email, String mobile, String name) {}

    public Optional<ContactInfo> resolveContact(UUID customerId) {
        if (customerId == null) return Optional.empty();
        try {
            CustomerResponse c = customerService.getByIdInternal(customerId);
            String name = ((c.getFirstName() != null ? c.getFirstName() : "") + " "
                    + (c.getLastName() != null ? c.getLastName() : "")).trim();
            return Optional.of(new ContactInfo(c.getEmail(), c.getMobileNumber(), name.isBlank() ? "Customer" : name));
        } catch (RuntimeException e) {
            log.warn("Could not resolve contact details for customer {}: {}", customerId, e.getMessage());
            return Optional.empty();
        }
    }
}
