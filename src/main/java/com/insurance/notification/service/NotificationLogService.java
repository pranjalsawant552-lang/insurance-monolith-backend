package com.insurance.notification.service;

import com.insurance.notification.entity.NotificationLog;
import com.insurance.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read API behind GET /notifications — lets an admin/auditor see what was
 * actually sent, to whom, and whether it succeeded, replacing the "no way
 * to see what was sent" gap flagged in the original audit (§4.12).
 */
@Service
@RequiredArgsConstructor
public class NotificationLogService {

    private final NotificationLogRepository repository;

    public Page<NotificationLog> search(UUID recipientId, String recipientAddress,
                                         NotificationLog.Channel channel, NotificationLog.DeliveryStatus status,
                                         int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        return repository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (recipientId != null) predicates.add(cb.equal(root.get("recipientId"), recipientId));
            if (recipientAddress != null && !recipientAddress.isBlank())
                predicates.add(cb.equal(root.get("recipientAddress"), recipientAddress));
            if (channel != null) predicates.add(cb.equal(root.get("channel"), channel));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable);
    }
}
