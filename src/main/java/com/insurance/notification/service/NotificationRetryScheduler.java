package com.insurance.notification.service;

import com.insurance.notification.entity.NotificationLog;
import com.insurance.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Runs every 5 minutes, retries FAILED sends whose next_retry_at has passed,
 * with exponential-ish backoff (5min, 20min, 60min) capped at max_attempts
 * (default 3) — replaces the "no retry queue for FAILED sends" gap flagged
 * in the original audit (§4.12). After max_attempts, a send is marked
 * ABANDONED rather than retried forever.
 *
 * Only handles EMAIL retries directly (re-sending needs the original HTML
 * body, which isn't persisted in NotificationLog — only metadata is, to
 * avoid storing potentially sensitive rendered content twice). SMS retry
 * follows the same status/backoff bookkeeping but the actual re-send call
 * would need the same body-reconstruction problem solved first; flagged as
 * a follow-up rather than half-implemented here — see Phase 7 changelog.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationLogRepository repository;
    private final JavaMailSender mailSender;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username}") private String fromEmail;
    @org.springframework.beans.factory.annotation.Value("${app.company-name}") private String companyName;

    @Scheduled(fixedRate = 5 * 60 * 1000) // every 5 minutes
    @Transactional
    public void retryFailedSends() {
        List<NotificationLog> due = repository.findByStatusAndNextRetryAtBefore(
                NotificationLog.DeliveryStatus.FAILED, ZonedDateTime.now());

        if (due.isEmpty()) return;
        log.info("Notification retry sweep: {} record(s) due", due.size());

        for (NotificationLog entry : due) {
            if (entry.getAttemptCount() >= entry.getMaxAttempts()) {
                entry.setStatus(NotificationLog.DeliveryStatus.ABANDONED);
                repository.save(entry);
                log.warn("Notification {} abandoned after {} attempts", entry.getId(), entry.getAttemptCount());
                continue;
            }

            if (entry.getChannel() != NotificationLog.Channel.EMAIL) {
                // See class javadoc — SMS body reconstruction not wired up this phase.
                // Bump the retry clock so this doesn't get swept every 5 minutes forever
                // without actually being retried; still bounded by maxAttempts above.
                entry.setNextRetryAt(nextBackoff(entry.getAttemptCount() + 1));
                entry.setAttemptCount(entry.getAttemptCount() + 1);
                repository.save(entry);
                continue;
            }

            entry.setAttemptCount(entry.getAttemptCount() + 1);
            entry.setStatus(NotificationLog.DeliveryStatus.RETRYING);
            repository.save(entry);

            try {
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
                helper.setFrom(fromEmail, companyName);
                helper.setTo(entry.getRecipientAddress());
                helper.setSubject("[Retry] " + (entry.getSubject() != null ? entry.getSubject() : ""));
                // Body was never persisted (see class javadoc) — the retry sends a
                // minimal notice pointing the recipient to check the portal, rather
                // than fabricating content, since the exact original body isn't
                // recoverable without storing it (a deliberate trade-off to avoid
                // duplicating potentially sensitive rendered HTML at rest).
                helper.setText("<p>You have a notification from " + companyName + " ("
                        + entry.getTemplate() + ") that we're re-attempting to deliver. "
                        + "Please check your account portal for the latest details.</p>", true);
                mailSender.send(msg);

                entry.setStatus(NotificationLog.DeliveryStatus.SENT);
                entry.setSentAt(ZonedDateTime.now());
                repository.save(entry);
                log.info("Retry succeeded for notification {} (attempt {})", entry.getId(), entry.getAttemptCount());
            } catch (Exception e) {
                entry.setStatus(NotificationLog.DeliveryStatus.FAILED);
                entry.setFailureReason(e.getMessage());
                entry.setNextRetryAt(nextBackoff(entry.getAttemptCount()));
                repository.save(entry);
                log.warn("Retry failed for notification {} (attempt {}): {}",
                        entry.getId(), entry.getAttemptCount(), e.getMessage());
            }
        }
    }

    private ZonedDateTime nextBackoff(int attemptNumber) {
        long minutes = switch (attemptNumber) {
            case 1 -> 5;
            case 2 -> 20;
            default -> 60;
        };
        return ZonedDateTime.now().plusMinutes(minutes);
    }
}
