package com.insurance.monolith.events;

import java.util.Map;

/**
 * MONOLITH CONVERSION NOTE: replaces Kafka as the cross-module event
 * transport. Every module used to publish a generic Map<String,Object>
 * envelope to a named Kafka topic (policy.events, claim.events,
 * payment.events, customer.events, auth.user.events,
 * policy.document.verified, audit.entity-changed) and other modules'
 * @KafkaListener beans consumed it. Now every one of those Map envelopes is
 * wrapped in this single event type and published in-process via Spring's
 * ApplicationEventPublisher; consumers switch from @KafkaListener(topics=...)
 * to @EventListener with a `#event.topic == '...'` condition. The payload
 * shape itself (the Map contents each publisher builds) is untouched, so
 * every consumer's field-extraction logic (getString/getUuid helpers etc.)
 * did not need to change — only the transport did.
 *
 * Trade-off, stated plainly: this is now in-memory pub/sub, not a durable
 * log. If the JVM crashes between publish and a listener finishing, that
 * event is gone — there is no replay, no consumer-group offset to resume
 * from, the way Kafka provided. For an audit trail in particular that's a
 * meaningfully different durability guarantee than the original design
 * assumed; see MONOLITH-CONVERSION-NOTES.md for the discussion of when
 * that trade-off is/isn't acceptable and what to do if it isn't (e.g. wrap
 * the audit listener in @TransactionalEventListener + an outbox table
 * instead of relying on this alone).
 */
public record DomainEvent(String topic, String key, Map<String, Object> payload) {
}
