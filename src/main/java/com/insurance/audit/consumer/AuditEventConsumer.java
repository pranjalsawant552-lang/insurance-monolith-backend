package com.insurance.audit.consumer;

import com.insurance.audit.entity.AuditLogEntry;
import com.insurance.audit.service.AuditLogService;
import com.insurance.monolith.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens on the single shared "audit.entity-changed" topic every other
 * service publishes to (see each service's new AuditEventPublisher). Using
 * one topic with a "sourceService" field — rather than one topic per
 * service — keeps this consumer, and every publisher, trivially simple:
 * one listener, one insert path, no per-service wiring to maintain here as
 * new services are added.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final AuditLogService auditLogService;

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'audit.entity-changed'")
    public void onEntityChanged(DomainEvent domainEvent) {
        Map<String, Object> payload = domainEvent.payload();
        try {
            // The Kafka record's own topic-partition-header source is not trusted for
            // "which service sent this" — each publisher stamps its own name into the
            // payload so it survives retries/replays and doesn't depend on consumer config.
            String sourceService = payload.get("sourceService") != null
                    ? payload.get("sourceService").toString() : "unknown";

            AuditLogEntry entry = auditLogService.fromEventPayload(payload, sourceService);
            auditLogService.record(entry);

            log.debug("Recorded audit entry: {} {} {} by service {}",
                    entry.getEntityType(), entry.getEntityId(), entry.getAction(), sourceService);
        } catch (Exception e) {
            // Never let a malformed audit event take down the consumer — log and move on.
            // A dropped audit row is bad; a stuck consumer that silently stops recording
            // audit events for every service is worse.
            log.error("Failed to process audit event: {}", e.getMessage(), e);
        }
    }
}
