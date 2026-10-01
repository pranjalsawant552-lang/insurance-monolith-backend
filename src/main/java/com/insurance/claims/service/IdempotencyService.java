package com.insurance.claims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.claims.entity.IdempotencyRecord;
import com.insurance.claims.repository.ClaimsIdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Same design as payment-service's IdempotencyService (Phase 6) — see that
 * class's javadoc for the full three-outcome replay/conflict logic. Kept as
 * a straight copy rather than a shared library, consistent with how every
 * other piece of small cross-service infra in this codebase (HeaderAuthFilter,
 * AuditEventPublisher) has been replicated per-service rather than factored
 * into a shared module the build doesn't currently support.
 */
@Slf4j
@Service("claimsIdempotencyService")
@RequiredArgsConstructor
public class IdempotencyService {

    private final ClaimsIdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    private static final int TTL_HOURS = 48;

    public record CachedResponse(int status, String body) {}

    @Transactional(readOnly = true)
    public Optional<CachedResponse> checkExisting(String idempotencyKey, String endpoint, Object requestPayload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();

        String requestHash = hash(requestPayload);
        Optional<IdempotencyRecord> existing = repository.findByIdempotencyKeyAndEndpoint(idempotencyKey, endpoint);
        if (existing.isEmpty()) return Optional.empty();

        IdempotencyRecord record = existing.get();
        if (!record.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key '" + idempotencyKey + "' was already used for a different request on this endpoint. "
                            + "Use a new key for a new request.");
        }

        log.info("Idempotent replay: key={} endpoint={} — returning cached response, not re-executing.", idempotencyKey, endpoint);
        return Optional.of(new CachedResponse(record.getResponseStatus(), record.getResponseBody()));
    }

    @Transactional
    public void store(String idempotencyKey, String endpoint, Object requestPayload, int responseStatus, Object responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;

        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .endpoint(endpoint)
                .requestHash(hash(requestPayload))
                .responseStatus(responseStatus)
                .responseBody(writeJson(responseBody))
                .expiresAt(ZonedDateTime.now().plusHours(TTL_HOURS))
                .build();

        try {
            repository.save(record);
        } catch (DataIntegrityViolationException e) {
            log.warn("Idempotency record for key={} endpoint={} already stored by a concurrent request.", idempotencyKey, endpoint);
        }
    }

    @Transactional
    public long purgeExpired() {
        return repository.deleteByExpiresAtBefore(ZonedDateTime.now());
    }

    private String hash(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to hash idempotency request payload");
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize idempotency response payload");
        }
    }
}
