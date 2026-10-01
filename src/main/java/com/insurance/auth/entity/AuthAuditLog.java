package com.insurance.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "auth_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "device_id")
    private UUID deviceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    // Event type constants
    public static final String LOGIN_SUCCESS      = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED       = "LOGIN_FAILED";
    public static final String LOGOUT             = "LOGOUT";
    public static final String REGISTER           = "REGISTER";
    public static final String EMAIL_VERIFIED     = "EMAIL_VERIFIED";
    public static final String MOBILE_VERIFIED    = "MOBILE_VERIFIED";
    public static final String ACCOUNT_LOCKED     = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_UNLOCKED   = "ACCOUNT_UNLOCKED";
    public static final String PASSWORD_RESET     = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED   = "PASSWORD_CHANGED";
    public static final String TOTP_ENABLED       = "TOTP_ENABLED";
    public static final String TOTP_DISABLED      = "TOTP_DISABLED";
    public static final String ROLE_PERMISSION_GRANTED = "ROLE_PERMISSION_GRANTED";
    public static final String ROLE_PERMISSION_REVOKED = "ROLE_PERMISSION_REVOKED";
    public static final String ROLE_CREATED             = "ROLE_CREATED";
    public static final String PERMISSION_CREATED       = "PERMISSION_CREATED";
    public static final String DEVICE_TRUSTED     = "DEVICE_TRUSTED";
    public static final String TOKEN_REFRESH      = "TOKEN_REFRESH";
    public static final String TOKEN_REVOKED      = "TOKEN_REVOKED";
    public static final String API_KEY_CREATED    = "API_KEY_CREATED";
}
