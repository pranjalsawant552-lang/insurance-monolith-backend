package com.insurance.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.payment.entity.IdempotencyRecord;
import com.insurance.payment.repository.PaymentIdempotencyRecordRepository;
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
 * Makes a write endpoint safe to retry. Usage in a controller:
 *
 * <pre>
 * Optional&lt;CachedResponse&gt; cached = idempotencyService.checkExisting(key, "POST /payments/manual", req);
 * if (cached.isPresent()) return cached.get().toResponseEntity();
 * // ... normal processing ...
 * idempotencyService.store(key, "POST /payments/manual", req, 200, responseBody);
 * </pre>
 *
 * Three outcomes when a key is supplied:
 * <ol>
 *   <li>Never seen before → proceed normally, then store the result.</li>
 *   <li>Seen before, same request body → this is a legitimate retry (client
 *       timed out waiting for the first response, or a proxy retried). The
 *       original response is replayed verbatim; the write is NOT
 *       re-executed.</li>
 *   <li>Seen before, different request body → the caller reused a key for a
 *       genuinely different operation, almost always a caller bug. Rejected
 *       with 409 CONFLICT rather than silently processing (which could
 *       silently execute the wrong operation) or silently replaying the old
 *       response (which would silently drop the new request).</li>
 * </ol>
 */
@Slf4j
@Service("paymentIdempotencyService")
@RequiredArgsConstructor
public class IdempotencyService {

    private final PaymentIdempotencyRecordRepository repository;
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
            // Race: two concurrent requests with the same key both passed checkExisting()
            // before either had stored a result. The unique constraint on
            // (idempotency_key, endpoint) catches this — whichever request loses the
            // race just doesn't overwrite the winner's stored response. Not re-thrown:
            // the winner's response is what should be considered authoritative either way.
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
