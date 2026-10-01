package com.insurance.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Backs idempotency-key handling on every money-touching write endpoint —
 * the CRITICAL gap flagged in the original audit (§4.8, §9): none of
 * create-order/verify/manual/claim-settlement/confirm-disbursement could
 * previously survive a client retry safely (a network blip + retry could
 * double-charge or double-disburse).
 *
 * Scoped by (idempotency_key, endpoint) rather than just the key alone —
 * the same key value reused against a different endpoint is a different
 * logical operation, not a replay of the same one. request_hash detects the
 * one dangerous case this pattern must reject outright: the same key reused
 * with a genuinely different request body, which almost always means a bug
 * in the caller, not a legitimate retry — see IdempotencyService.
 */
@Entity(name = "PaymentIdempotencyRecord")
@Table(name = "payment_idempotency_records", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"idempotency_key", "endpoint"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(nullable = false, length = 100)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Lob
    @Column(name = "response_body", columnDefinition = "text", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    /** Idempotency records are kept 48h — long enough to cover realistic retry
     *  windows (client-side backoff, mobile network flakiness) without the
     *  table growing forever. Cleaned up by a scheduled job. */
    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;
}
