package com.insurance.claims.service;

import com.insurance.claims.entity.Claim;
import com.insurance.claims.event.ClaimEventPublisher;
import com.insurance.claims.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Runs every 15 minutes, finds claims whose current-stage SLA has passed
 * without being resolved, marks them breached, and publishes a
 * CLAIM_SLA_BREACHED event so notification-service (or a future ops
 * dashboard) can alert someone. Replaces the "no SLA-deadline field on
 * claims" gap flagged in the original audit — claims previously had no
 * concept of being overdue at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaMonitorScheduler {

    private final ClaimRepository claimRepository;
    private final ClaimEventPublisher eventPublisher;
    private final com.insurance.claims.event.AuditEventPublisher auditEventPublisher;
    private final IdempotencyService idempotencyService;

    @Scheduled(fixedRate = 15 * 60 * 1000) // every 15 minutes
    @Transactional
    public void flagBreachedClaims() {
        List<Claim> overdue = claimRepository.findNewlyBreachedClaims(ZonedDateTime.now());
        if (overdue.isEmpty()) return;

        log.info("SLA monitor: {} claim(s) newly breached", overdue.size());

        for (Claim claim : overdue) {
            claim.setSlaBreached(true);
            claimRepository.save(claim);

            eventPublisher.publishSlaBreached(claim);
            auditEventPublisher.publish("CLAIM", claim.getId().toString(), "SLA_BREACHED",
                    java.util.Map.of("slaBreached", "false"),
                    java.util.Map.of("slaBreached", "true", "status", claim.getStatus().name(),
                            "slaDueAt", String.valueOf(claim.getSlaDueAt())),
                    "Claim " + claim.getClaimNumber() + " breached its SLA in status " + claim.getStatus());
        }
    }

    // Daily at 03:00 - purge expired idempotency records (kept 48h, see IdempotencyRecord)
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredIdempotencyRecords() {
        long deleted = idempotencyService.purgeExpired();
        if (deleted > 0) log.info("Purged {} expired idempotency record(s).", deleted);
    }
}
