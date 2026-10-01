package com.insurance.analytics.service;

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
public class AnalyticsEventConsumer {

    private final AnalyticsService analyticsService;

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'policy.events'")
    public void onPolicyEvent(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String type      = str(event, "eventType");
        String companyId = str(event, "companyId");
        if (type == null || companyId == null) return;
        String insType   = str(event, "insuranceType");

        switch (type) {
            case "POLICY_CREATED"  -> analyticsService.increment(companyId, "policies:total");
            case "POLICY_ISSUED"   -> {
                analyticsService.increment(companyId, "policies:active");
                analyticsService.increment(companyId, "policies:status:ACTIVE");
                if (insType != null) analyticsService.increment(companyId, "policies:type:" + insType);
            }
            case "POLICY_LAPSED"   -> {
                analyticsService.increment(companyId, "policies:lapsed");
                analyticsService.increment(companyId, "policies:status:LAPSED");
            }
            case "POLICY_CANCELLED"  -> analyticsService.increment(companyId, "policies:status:CANCELLED");
            case "POLICY_SURRENDERED"-> analyticsService.increment(companyId, "policies:status:SURRENDERED");
            case "POLICY_MATURED"    -> analyticsService.increment(companyId, "policies:status:MATURED");
        }
    }

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'claim.events'")
    public void onClaimEvent(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String type      = str(event, "eventType");
        String companyId = str(event, "companyId");
        if (type == null || companyId == null) return;
        String insType   = str(event, "insuranceType");

        switch (type) {
            case "CLAIM_SUBMITTED" -> {
                analyticsService.increment(companyId, "claims:total");
                analyticsService.increment(companyId, "claims:open");
                analyticsService.increment(companyId, "claims:status:SUBMITTED");
                if (insType != null) analyticsService.increment(companyId, "claims:type:" + insType);
            }
            case "CLAIM_APPROVED"  -> analyticsService.increment(companyId, "claims:status:APPROVED");
            case "CLAIM_REJECTED"  -> analyticsService.increment(companyId, "claims:status:REJECTED");
            case "CLAIM_SETTLED"   -> {
                analyticsService.increment(companyId, "claims:settled");
                analyticsService.increment(companyId, "claims:status:SETTLED");
                String settled = str(event, "settledAmount");
                if (settled != null) analyticsService.incrementAmount(companyId, "payments:claims_settled", settled);
            }
            case "CLAIM_FRAUD_FLAGGED" -> analyticsService.increment(companyId, "claims:fraud_flagged");
        }
    }

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'payment.events'")
    public void onPaymentEvent(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String type      = str(event, "eventType");
        String companyId = str(event, "companyId");
        if (type == null || companyId == null) return;
        String amount    = str(event, "amount");
        String txnType   = str(event, "transactionType");

        switch (type) {
            case "PAYMENT_SUCCESS" -> {
                analyticsService.increment(companyId, "payments:count");
                if ("PREMIUM".equals(txnType) && amount != null) {
                    analyticsService.incrementAmount(companyId, "payments:premium_total", amount);
                }
            }
            case "PAYMENT_FAILED"  -> analyticsService.increment(companyId, "payments:failed_count");
            case "PREMIUM_OVERDUE" -> analyticsService.increment(companyId, "payments:overdue_count");
        }
    }

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'customer.events'")
    public void onCustomerEvent(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String type      = str(event, "eventType");
        String companyId = str(event, "companyId");
        if (type == null || companyId == null) return;

        switch (type) {
            case "CUSTOMER_CREATED" -> analyticsService.increment(companyId, "customers:total");
            case "KYC_VERIFIED"     -> analyticsService.increment(companyId, "customers:kyc_verified");
        }
    }

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'auth.user.events'")
    public void onAuthEvent(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        // Auth events not directly tracked in analytics
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }
}
