package com.insurance.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy_status_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicyStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "policy_id") private Policy policy;
    @Column(name = "old_status") private String oldStatus;
    @Column(name = "new_status", nullable = false) private String newStatus;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(name = "changed_by", nullable = false) private UUID changedBy;
    @Column(name = "changed_at", updatable = false)
    @Builder.Default private ZonedDateTime changedAt = ZonedDateTime.now();
    @Column(name = "ip_address") private String ipAddress;
}
