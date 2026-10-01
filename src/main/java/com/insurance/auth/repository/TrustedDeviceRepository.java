package com.insurance.auth.repository;

import com.insurance.auth.entity.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

    Optional<TrustedDevice> findByUserIdAndDeviceFingerprint(UUID userId, String fingerprint);

    List<TrustedDevice> findByUserIdAndTrustedTrue(UUID userId);

    void deleteByUserIdAndDeviceFingerprint(UUID userId, String fingerprint);
}
