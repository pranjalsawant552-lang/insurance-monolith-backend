package com.insurance.policy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * SECURITY/CORRECTNESS FIX (audit report §4.7): the renewal-reminder
 * scheduler used to re-query the same 30-day window every day and
 * re-publish a RENEWAL_DUE event for every policy still in it — a policy
 * due in 25 days got a reminder queued once a day for 25 straight days,
 * not once. This table records exactly which staged milestone
 * (60/45/30/15/7/1 days out, configurable — see application.yml
 * "policy.renewal-reminder.milestone-days") has already been sent for a
 * given policy, so each milestone fires exactly once. The unique
 * constraint on (policy_id, milestone_days) is the actual dedup guarantee
 * — it protects against duplicate sends even if the scheduler somehow ran
 * twice concurrently (e.g. two app instances), not just against the
 * "same day" case a simple in-memory check would only half-solve.
 */
@Entity
@Table(name = "policy_renewal_reminders",
        uniqueConstraints = @UniqueConstraint(columnNames = {"policy_id", "milestone_days"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicyRenewalReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    /** Which staged milestone this reminder was for — 60, 45, 30, 15, 7, or 1 (days before due date). */
    @Column(name = "milestone_days", nullable = false)
    private int milestoneDays;

    @Column(name = "sent_at", nullable = false)
    private ZonedDateTime sentAt;
}
