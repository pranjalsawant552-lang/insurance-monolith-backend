package com.insurance.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "trusted_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_fingerprint", nullable = false)
    private String deviceFingerprint;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "device_type")
    private String deviceType;

    @Column
    private String browser;

    @Column
    private String os;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "is_trusted", nullable = false)
    @Builder.Default
    private boolean trusted = false;

    @Column(name = "trusted_at")
    private ZonedDateTime trustedAt;

    @Column(name = "last_used_at")
    private ZonedDateTime lastUsedAt;

    @Column(name = "expires_at")
    private ZonedDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public boolean isExpired() {
        return expiresAt != null && ZonedDateTime.now().isAfter(expiresAt);
    }
}
