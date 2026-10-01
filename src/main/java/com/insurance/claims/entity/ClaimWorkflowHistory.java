package com.insurance.claims.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity @Table(name = "claim_workflow_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimWorkflowHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "claim_id") private Claim claim;
    @Column(name = "old_status") private String oldStatus;
    @Column(name = "new_status", nullable = false) private String newStatus;
    @Column private String action;
    @Column(columnDefinition = "TEXT") private String comments;
    @Column(name = "performed_by", nullable = false) private UUID performedBy;
    @Column(name = "performed_at", updatable = false)
    @Builder.Default private ZonedDateTime performedAt = ZonedDateTime.now();
}
