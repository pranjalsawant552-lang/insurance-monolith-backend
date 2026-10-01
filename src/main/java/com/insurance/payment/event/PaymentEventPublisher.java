package com.insurance.payment.event;

import com.insurance.payment.entity.PaymentTransaction;
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
public class PaymentEventPublisher {

    private final DomainEventBus domainEventBus;
    private static final String TOPIC = "payment.events";

    @Async
    public void publishPaymentSuccess(PaymentTransaction t) {
        publish("PAYMENT_SUCCESS", t, Map.of(
                "gatewayPaymentId", t.getGatewayPaymentId() != null ? t.getGatewayPaymentId() : "",
                "transactionType", t.getTransactionType().name()
        ));
    }

    @Async
    public void publishPaymentFailed(PaymentTransaction t) {
        publish("PAYMENT_FAILED", t, Map.of(
                "reason", t.getFailureReason() != null ? t.getFailureReason() : ""
        ));
    }

    @Async
    public void publishRefundInitiated(PaymentTransaction t) {
        publish("REFUND_INITIATED", t, Map.of());
    }

    @Async
    public void publishPremiumOverdue(PaymentTransaction t) {
        publish("PREMIUM_OVERDUE", t, Map.of(
                "dueDate", t.getPremiumDueDate() != null ? t.getPremiumDueDate().toString() : ""
        ));
    }

    private void publish(String type, PaymentTransaction t, Map<String, String> extra) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", type);
        event.put("transactionId", t.getId().toString());
        event.put("transactionNumber", t.getTransactionNumber());
        event.put("policyId", t.getPolicyId().toString());
        event.put("policyNumber", t.getPolicyNumber());
        event.put("customerId", t.getCustomerId().toString());
        event.put("companyId", t.getCompanyId().toString());
        event.put("amount", t.getAmount().toString());
        event.put("currency", t.getCurrency());
        event.put("timestamp", ZonedDateTime.now().toString());
        event.putAll(extra);

        domainEventBus.publish(TOPIC, t.getId().toString(), event);
        log.debug("Published {} for txn {}", type, t.getTransactionNumber());
    }
}
