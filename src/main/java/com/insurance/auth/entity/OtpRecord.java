package com.insurance.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "otp_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String identifier;     // email or mobile

    @Column(name = "otp_code", nullable = false)
    private String otpCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "otp_type", nullable = false)
    private OtpType otpType;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Column(name = "ip_address")
    private String ipAddress;

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiresAt);
    }

    public boolean isMaxAttemptsReached() {
        return attempts >= 3;
    }
}
