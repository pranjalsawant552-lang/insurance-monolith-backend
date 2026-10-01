package com.insurance.notification.dto.response;

import com.insurance.notification.entity.NotificationLog;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationLogResponse {
    private UUID id;
    private String channel;
    private String template;
    private UUID recipientId;
    private String recipientAddress;
    private String subject;
    private String status;
    private String providerMessageId;
    private String failureReason;
    private int attemptCount;
    private ZonedDateTime createdAt;
    private ZonedDateTime sentAt;

    public static NotificationLogResponse from(NotificationLog n) {
        return NotificationLogResponse.builder()
                .id(n.getId())
                .channel(n.getChannel().name())
                .template(n.getTemplate())
                .recipientId(n.getRecipientId())
                .recipientAddress(n.getRecipientAddress())
                .subject(n.getSubject())
                .status(n.getStatus().name())
                .providerMessageId(n.getProviderMessageId())
                .failureReason(n.getFailureReason())
                .attemptCount(n.getAttemptCount())
                .createdAt(n.getCreatedAt())
                .sentAt(n.getSentAt())
                .build();
    }
}
