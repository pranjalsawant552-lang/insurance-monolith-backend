-- MONOLITH FIX (audit report §4.7): renewal-reminder duplicate-send bug.
-- The unique constraint here is the actual guarantee — a policy can only
-- ever have one row per (policy_id, milestone_days) combination, so a
-- given staged reminder (e.g. "30 days out") can only ever be recorded
-- once per policy no matter how many times the scheduler runs.

CREATE TABLE policy_renewal_reminders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID NOT NULL,
    milestone_days  INT NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_policy_renewal_reminder UNIQUE (policy_id, milestone_days)
);

CREATE INDEX idx_policy_renewal_reminders_policy ON policy_renewal_reminders(policy_id);
