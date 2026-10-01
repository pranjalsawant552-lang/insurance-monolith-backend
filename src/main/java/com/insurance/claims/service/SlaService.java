package com.insurance.claims.service;

import com.insurance.claims.entity.Claim;
import com.insurance.claims.entity.ClaimStatus;
import com.insurance.claims.entity.SlaConfig;
import com.insurance.claims.repository.SlaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Computes a claim's sla_due_at whenever it enters a new status, based on
 * whatever SlaConfig row exists for (status, priority) — replaces the
 * "no SLA-deadline field on claims" gap flagged in the original audit.
 * Terminal statuses (APPROVED/REJECTED/SETTLED/CLOSED... actually APPROVED
 * still needs settlement tracked, so only REJECTED/SETTLED/CLOSED are truly
 * terminal here) clear the due date — nothing to breach once a claim is done.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaService {

    private final SlaConfigRepository slaConfigRepository;

    private static final Set<ClaimStatus> TERMINAL = Set.of(
            ClaimStatus.REJECTED, ClaimStatus.SETTLED, ClaimStatus.CLOSED);

    /** Called on every status transition — sets the new stage's due date, or clears it if terminal. */
    public void applySlaForNewStatus(Claim claim) {
        if (TERMINAL.contains(claim.getStatus())) {
            claim.setSlaDueAt(null);
            claim.setSlaBreached(false);
            return;
        }

        slaConfigRepository.findByStatusAndPriority(claim.getStatus(), claim.getPriority())
                .ifPresentOrElse(
                        config -> {
                            claim.setSlaDueAt(ZonedDateTime.now().plusHours(config.getMaxHours()));
                            claim.setSlaBreached(false); // fresh stage, fresh clock
                        },
                        () -> {
                            // No SLA configured for this status/priority combination —
                            // leave sla_due_at null rather than guessing a default. A
                            // missing config row should surface as "no SLA tracked", not
                            // a silently wrong deadline.
                            log.debug("No SlaConfig for status={} priority={} — leaving SLA untracked for claim {}",
                                    claim.getStatus(), claim.getPriority(), claim.getClaimNumber());
                            claim.setSlaDueAt(null);
                        }
                );
    }
}
