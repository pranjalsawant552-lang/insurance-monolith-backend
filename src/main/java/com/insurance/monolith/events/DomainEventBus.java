package com.insurance.monolith.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Drop-in replacement for every module's former KafkaTemplate<String,Object>
 * dependency. publish() is synchronous from the caller's point of view
 * (Spring calls every matching @EventListener in-line), but every listener
 * in this codebase is itself annotated @Async, so in practice this behaves
 * the same as the old fire-and-forget Kafka send: the publishing thread
 * does not wait on consumers. Wrapped in try/catch so a listener exception
 * can never propagate back and fail the publishing transaction — matching
 * every original publisher's "never let audit/notification/analytics
 * plumbing fail the actual business operation" soft-fail behavior.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventBus {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(String topic, String key, Map<String, Object> payload) {
        try {
            applicationEventPublisher.publishEvent(new DomainEvent(topic, key, payload));
        } catch (RuntimeException e) {
            log.error("Failed to publish in-process event {} {}: {}", topic, key, e.getMessage(), e);
        }
    }
}
