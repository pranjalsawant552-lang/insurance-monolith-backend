package com.insurance.payment.repository;

import com.insurance.payment.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByIdempotencyKeyAndEndpoint(String idempotencyKey, String endpoint);
    long deleteByExpiresAtBefore(ZonedDateTime cutoff);
}
