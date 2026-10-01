package com.insurance.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One row per send attempt — replaces the "nothing is stored, sends are
 * fire-and-forget" gap flagged in the original audit (§4.12). Every email/
 * SMS/push/in-app send now writes a row here before the attempt, and
 * updates it with the outcome after — so there's always an answer to "did
 * this notification actually go out" and "what did we send this customer",
 * both explicitly called out as required for audit and retry logic.
 */
@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_notif_log_recipient", columnList = "recipient_id"),
        @Index(name = "idx_notif_log_status", columnList = "status"),
        @Index(name = "idx_notif_log_created_at", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    /** e.g. POLICY_ISSUED, CLAIM_APPROVED, PAYMENT_SUCCESS — the event/template this send was for. */
    @Column(nullable = false, length = 80)
    private String template;

    /** The customerId/userId this notification was for, when known — nullable because
     *  some events (e.g. before the contact-resolution fix) may not resolve one. */
    @Column(name = "recipient_id")
    private UUID recipientId;

    /** Actual destination address used (email address or phone number) — kept for audit
     *  even though it duplicates what customer-service holds, since a customer's contact
     *  details can change after the notification was sent; this is what was ACTUALLY sent to. */
    @Column(name = "recipient_address", length = 255)
    private String recipientAddress;

    @Column(length = 255)
    private String subject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.PENDING;

    /** Provider-assigned message id (e.g. SES/Twilio message id) once accepted, used to match delivery-receipt webhooks. */
    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 3;

    @Column(name = "next_retry_at")
    private ZonedDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "sent_at")
    private ZonedDateTime sentAt;

    public enum Channel { EMAIL, SMS, PUSH, IN_APP }

    public enum DeliveryStatus { PENDING, SENT, FAILED, BOUNCED, RETRYING, ABANDONED }
}
