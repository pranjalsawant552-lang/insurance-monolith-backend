package com.insurance.customer.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.insurance.monolith.events.DomainEventBus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes a generic "this record changed" event to the shared
 * "audit.entity-changed" Kafka topic that audit-service listens on. This is
 * intentionally separate from this service's own domain-event publisher
 * (e.g. the *EventPublisher classes notification-service consumes) — mixing
 * the two would force audit-service to understand every service's event
 * shape. Instead every service publishes the exact same generic envelope
 * here, and only the entityType/action/before/after payload differs.
 *
 * Reads the caller's identity from the request attributes HeaderAuthFilter
 * already populated (userId/companyId/branchId) plus the role header, so
 * callers of this publisher don't need to thread actor details through
 * every method signature by hand.
 */
@Slf4j
@Component("customerAuditEventPublisher")
@RequiredArgsConstructor
public class AuditEventPublisher {

    private final DomainEventBus domainEventBus;

    private static final String TOPIC = "audit.entity-changed";
    private static final String SOURCE_SERVICE = "customer-service";

    @Async
    public void publish(String entityType, String entityId, String action,
                         Map<String, Object> beforeState, Map<String, Object> afterState,
                         String summary) {

        Map<String, Object> event = new HashMap<>();
        event.put("entityType", entityType);
        event.put("entityId", entityId);
        event.put("action", action);
        event.put("sourceService", SOURCE_SERVICE);
        event.put("summary", summary);
        event.put("timestamp", ZonedDateTime.now().toString());
        if (beforeState != null) event.put("beforeState", beforeState);
        if (afterState != null) event.put("afterState", afterState);

        HttpRequestActor actor = currentActor();
        if (actor.userId != null) event.put("actorId", actor.userId);
        if (actor.role != null) event.put("actorRole", actor.role);
        if (actor.companyId != null) event.put("companyId", actor.companyId);
        if (actor.branchId != null) event.put("branchId", actor.branchId);
        if (actor.ipAddress != null) event.put("ipAddress", actor.ipAddress);

        domainEventBus.publish(TOPIC, entityId, event);
        log.debug("Published audit event {} {} {}", entityType, entityId, action);
    }

    private HttpRequestActor currentActor() {
        HttpRequestActor actor = new HttpRequestActor();
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                var request = attrs.getRequest();
                Object userId = request.getAttribute("userId");
                Object companyId = request.getAttribute("companyId");
                Object branchId = request.getAttribute("branchId");
                actor.userId = userId != null ? userId.toString() : null;
                actor.companyId = companyId != null ? companyId.toString() : null;
                actor.branchId = branchId != null ? branchId.toString() : null;
                actor.role = request.getHeader("X-User-Role");
                actor.ipAddress = request.getHeader("X-Forwarded-For") != null
                        ? request.getHeader("X-Forwarded-For") : request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("No request context available for audit actor enrichment (likely a scheduled/async job): {}", e.getMessage());
        }
        return actor;
    }

    private static class HttpRequestActor {
        String userId;
        String role;
        String companyId;
        String branchId;
        String ipAddress;
    }
}
