package com.insurance.notification.repository;

import com.insurance.notification.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID>,
        JpaSpecificationExecutor<NotificationLog> {

    Page<NotificationLog> findByRecipientId(UUID recipientId, Pageable pageable);

    List<NotificationLog> findByStatusAndNextRetryAtBefore(NotificationLog.DeliveryStatus status, ZonedDateTime cutoff);
}
