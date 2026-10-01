package com.insurance.notification.controller;

import com.insurance.notification.dto.response.ApiResponse;
import com.insurance.notification.dto.response.NotificationLogResponse;
import com.insurance.notification.dto.response.PagedResponse;
import com.insurance.notification.entity.NotificationLog;
import com.insurance.notification.service.NotificationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Read-only view of what's actually been sent — Admin/Auditor only. There's
 * deliberately no write endpoint here: notifications are created only by
 * the Kafka consumers reacting to real domain events, never by direct API call.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Sent-notification history and delivery status")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationLogService notificationLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "Search sent notifications — filter by recipient, channel, delivery status")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationLogResponse>>> search(
            @RequestParam(required = false) UUID recipientId,
            @RequestParam(required = false) String recipientAddress,
            @RequestParam(required = false) NotificationLog.Channel channel,
            @RequestParam(required = false) NotificationLog.DeliveryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = notificationLogService.search(recipientId, recipientAddress, channel, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(result.map(NotificationLogResponse::from))));
    }
}
