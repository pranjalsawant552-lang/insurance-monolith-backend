package com.insurance.policy.event;

import com.insurance.policy.service.PolicyIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.insurance.monolith.events.DomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The other half of document-service's extraction pipeline (Phase 8) — this
 * is what was explicitly flagged as not-yet-built in that phase's changelog:
 * "policy-service does not yet consume verified extractions". Every time a
 * reviewer verifies a POLICY_SCHEDULE extraction over there, this listener
 * turns it into a Policy record over here.
 *
 * Deliberately never lets an exception escape to the Kafka container:
 * PolicyIngestionService.ingestFromVerifiedExtraction() already catches
 * everything internally and routes failures to policy_ingestion_failures,
 * but this handler double-guards that contract so a future change to that
 * method can't accidentally turn one bad document into a stuck/looping
 * consumer for everyone else's uploads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyDocumentVerifiedConsumer {

    private final PolicyIngestionService ingestionService;

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'policy.document.verified'")
    public void handle(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        try {
            ingestionService.ingestFromVerifiedExtraction(event);
        } catch (Exception e) {
            log.error("Unexpected error consuming policy.document.verified event {}: {}", event, e.getMessage(), e);
        }
    }
}
