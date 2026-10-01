package com.insurance.auth.repository;

import com.insurance.auth.entity.User;
import com.insurance.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedFalse(String email);

    Optional<User> findByMobileNumberAndDeletedFalse(String mobileNumber);

    Optional<User> findByOauthProviderAndOauthProviderIdAndDeletedFalse(String provider, String providerId);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByMobileNumberAndDeletedFalse(String mobileNumber);

    // Bootstrap gate: the one-time admin-creation endpoint refuses to run once any
    // SUPER_ADMIN already exists, so it can't be (mis)used as a permanent backdoor.
    boolean existsByRoleAndDeletedFalse(UserRole role);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1, u.updatedAt = :now WHERE u.id = :id")
    void incrementFailedAttempts(@Param("id") UUID id, @Param("now") ZonedDateTime now);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginAt, u.lastLoginIp = :ip, u.failedLoginAttempts = 0, u.updatedAt = :now WHERE u.id = :id")
    void recordSuccessfulLogin(@Param("id") UUID id, @Param("loginAt") ZonedDateTime loginAt,
                               @Param("ip") String ip, @Param("now") ZonedDateTime now);
}
