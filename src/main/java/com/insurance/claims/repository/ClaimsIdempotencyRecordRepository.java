package com.insurance.claims.repository;

import com.insurance.claims.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ClaimsIdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByIdempotencyKeyAndEndpoint(String idempotencyKey, String endpoint);
    long deleteByExpiresAtBefore(ZonedDateTime cutoff);
}
