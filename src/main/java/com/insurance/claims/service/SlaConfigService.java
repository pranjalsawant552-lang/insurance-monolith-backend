package com.insurance.claims.service;

import com.insurance.claims.entity.ClaimPriority;
import com.insurance.claims.entity.ClaimStatus;
import com.insurance.claims.entity.SlaConfig;
import com.insurance.claims.repository.SlaConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlaConfigService {

    private final SlaConfigRepository repository;
    private final com.insurance.claims.event.AuditEventPublisher auditEventPublisher;

    public List<SlaConfig> listAll() {
        return repository.findAllByOrderByStatusAscPriorityAsc();
    }

    @Transactional
    public SlaConfig upsert(ClaimStatus status, ClaimPriority priority, int maxHours) {
        SlaConfig config = repository.findByStatusAndPriority(status, priority)
                .orElse(SlaConfig.builder().status(status).priority(priority).build());
        config.setMaxHours(maxHours);
        config.setUpdatedAt(ZonedDateTime.now());
        SlaConfig saved = repository.save(config);

        auditEventPublisher.publish("SLA_CONFIG", saved.getId().toString(), "UPSERT",
                null,
                java.util.Map.of("status", status.name(), "priority", priority.name(), "maxHours", String.valueOf(maxHours)),
                "SLA threshold set: " + status + "/" + priority + " → " + maxHours + "h");

        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SLA config not found: " + id);
        }
        repository.deleteById(id);
        auditEventPublisher.publish("SLA_CONFIG", id.toString(), "DELETE", null, null, "SLA threshold removed: " + id);
    }
}
