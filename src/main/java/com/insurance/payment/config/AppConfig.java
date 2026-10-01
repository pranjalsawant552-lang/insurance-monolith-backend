package com.insurance.payment.config;

import com.insurance.payment.service.IdempotencyService;
import com.insurance.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// NOTE: the security bean that used to live here (a permissive "anyRequest().permitAll()"
// filter chain) was removed as part of Phase 1 RBAC enforcement — it duplicated the class
// name in com.insurance.payment.config.SecurityConfig, which now provides the real,
// role/permission-checking filter chain (see config/SecurityConfig.java + security/HeaderAuthFilter.java).
// The webhook-must-be-public exception now lives there too (PUBLIC_ENDPOINTS includes
// "/payments/webhook/**").


@Slf4j
@Component
@RequiredArgsConstructor
class PaymentScheduler {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;


    // Daily at 09:00 - mark overdue billing entries and fire events
    @Scheduled(cron = "0 0 9 * * *")
    public void overdueCheck() {

        log.info("Running daily overdue billing check...");

        paymentService.processOverdueBilling();
    }


    // Daily at 03:00 - purge expired idempotency records
    // Records are kept for 48 hours.
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredIdempotencyRecords() {

        long deleted = idempotencyService.purgeExpired();

        if (deleted > 0) {
            log.info(
                    "Purged {} expired idempotency record(s).",
                    deleted
            );
        }
    }
}