package com.insurance.auth.repository;

import com.insurance.auth.entity.OtpRecord;
import com.insurance.auth.entity.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRepository extends JpaRepository<OtpRecord, UUID> {

    Optional<OtpRecord> findTopByIdentifierAndOtpTypeAndUsedFalseOrderByCreatedAtDesc(
            String identifier, OtpType otpType);

    @Modifying
    @Query("UPDATE OtpRecord o SET o.used = true WHERE o.identifier = :identifier AND o.otpType = :type AND o.used = false")
    void invalidateAllForIdentifier(@Param("identifier") String identifier, @Param("type") OtpType type);

    @Modifying
    @Query("DELETE FROM OtpRecord o WHERE o.expiresAt < :cutoff")
    int deleteExpiredOtps(@Param("cutoff") ZonedDateTime cutoff);

    long countByIdentifierAndOtpTypeAndCreatedAtAfter(String identifier, OtpType type, ZonedDateTime after);
}
