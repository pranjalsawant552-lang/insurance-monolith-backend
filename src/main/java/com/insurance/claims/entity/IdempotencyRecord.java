package com.insurance.claims.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Same pattern as payment-service's IdempotencyRecord (Phase 6) — makes the
 * two highest-stakes claim actions (approve, settle) safe to retry.
 * Approving or settling a claim twice due to a network retry is exactly the
 * kind of double-processing bug idempotency keys exist to prevent, even
 * though claims-service isn't itself a payment system.
 */
@Entity(name = "ClaimsIdempotencyRecord")
@Table(name = "claims_idempotency_records", uniqueConstraints = {
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

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;
}
