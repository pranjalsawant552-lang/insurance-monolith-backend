package com.insurance.audit.service;

import com.insurance.audit.entity.AuditLogEntry;
import com.insurance.audit.repository.AuditLogEntryRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogEntryRepository repository;

    /** Called by the Kafka consumer — the only write path into this table. */
    public void record(AuditLogEntry entry) {
        repository.save(entry);
    }

    public Page<AuditLogEntry> search(String entityType, String entityId, UUID actorId,
                                       String action, UUID companyId,
                                       ZonedDateTime from, ZonedDateTime to,
                                       int page, int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        return repository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("entityType")), entityType.toUpperCase()));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("action")), action.toUpperCase()));
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public Page<AuditLogEntry> historyForEntity(String entityType, String entityId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                entityType.toUpperCase(), entityId, pageable);
    }

    public long totalCount() {
        return repository.count();
    }

    @SuppressWarnings("unchecked")
    public AuditLogEntry fromEventPayload(Map<String, Object> payload, String sourceService) {
        AuditLogEntry.AuditLogEntryBuilder builder = AuditLogEntry.builder()
                .entityType(str(payload.get("entityType")))
                .entityId(str(payload.get("entityId")))
                .action(str(payload.get("action")))
                .actorRole(str(payload.get("actorRole")))
                .summary(str(payload.get("summary")))
                .ipAddress(str(payload.get("ipAddress")))
                .correlationId(str(payload.get("correlationId")))
                .sourceService(sourceService);

        uuidOrNull(payload.get("actorId")).ifPresent(builder::actorId);
        uuidOrNull(payload.get("companyId")).ifPresent(builder::companyId);
        uuidOrNull(payload.get("branchId")).ifPresent(builder::branchId);

        Object before = payload.get("beforeState");
        Object after = payload.get("afterState");
        if (before instanceof Map) builder.beforeState((Map<String, Object>) before);
        if (after instanceof Map) builder.afterState((Map<String, Object>) after);

        return builder.build();
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private java.util.Optional<UUID> uuidOrNull(Object o) {
        if (o == null) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(UUID.fromString(o.toString()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
