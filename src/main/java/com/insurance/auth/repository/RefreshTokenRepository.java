package com.insurance.auth.repository;

import com.insurance.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now, r.revokeReason = :reason WHERE r.user.id = :userId AND r.revoked = false")
    void revokeAllForUser(@Param("userId") UUID userId, @Param("now") ZonedDateTime now, @Param("reason") String reason);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now, r.revokeReason = :reason WHERE r.tokenHash = :hash")
    void revokeByTokenHash(@Param("hash") String hash, @Param("now") ZonedDateTime now, @Param("reason") String reason);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") ZonedDateTime cutoff);

    long countByUserIdAndRevokedFalse(UUID userId);
}
