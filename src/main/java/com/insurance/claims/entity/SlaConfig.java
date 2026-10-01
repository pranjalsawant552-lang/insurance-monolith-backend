package com.insurance.claims.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Admin-editable SLA threshold for how long a claim may sit in a given
 * status at a given priority before it's considered breached. Same
 * "pluggable, not hardcoded" pattern established for rating factors
 * (Phase 3) and risk factors (Phase 4) — an ops team edits rows through
 * ClaimController's admin endpoints, no redeploy needed to change SLAs.
 */
@Entity
@Table(name = "sla_configs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"status", "priority"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SlaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimPriority priority;

    @Column(name = "max_hours", nullable = false)
    private int maxHours;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private ZonedDateTime updatedAt = ZonedDateTime.now();
}
