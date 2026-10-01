-- MONOLITH MERGE: originally claims-service/V2__sla_tracking.sql
-- ============================================================
-- Claims Service - Phase 5: SLA tracking
--
-- Replaces the "no SLA-deadline field on claims" gap flagged in the
-- original audit (§4.6). Every non-terminal status transition now gets a
-- due date computed from sla_configs (status, priority) → max_hours;
-- SlaMonitorScheduler flags anything that passes its due date unresolved.
-- ============================================================

ALTER TABLE claims
    ADD COLUMN sla_due_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN sla_breached BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_claims_sla_due_at ON claims(sla_due_at) WHERE sla_breached = FALSE;
CREATE INDEX idx_claims_sla_breached ON claims(company_id, sla_breached);

CREATE TABLE sla_configs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status      VARCHAR(30) NOT NULL,
    priority    VARCHAR(20) NOT NULL,
    max_hours   INT NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (status, priority)
);

-- ------------------------------------------------------------
-- Seed: sensible default SLAs per status/priority. Starting points for a
-- claims ops team to tune via the admin API — not contractual/regulatory
-- SLAs, just reasonable defaults so the feature works out of the box.
-- CLOSED/SETTLED/REJECTED intentionally have no rows — they're terminal
-- statuses (see SlaService.TERMINAL) and never get a due date.
-- ------------------------------------------------------------
INSERT INTO sla_configs (status, priority, max_hours) VALUES
    ('SUBMITTED', 'CRITICAL', 4),   ('SUBMITTED', 'HIGH', 12),   ('SUBMITTED', 'NORMAL', 24),   ('SUBMITTED', 'LOW', 48),
    ('UNDER_REVIEW', 'CRITICAL', 24),  ('UNDER_REVIEW', 'HIGH', 48),  ('UNDER_REVIEW', 'NORMAL', 96),  ('UNDER_REVIEW', 'LOW', 168),
    ('DOCS_REQUIRED', 'CRITICAL', 72), ('DOCS_REQUIRED', 'HIGH', 120), ('DOCS_REQUIRED', 'NORMAL', 168), ('DOCS_REQUIRED', 'LOW', 240),
    ('SURVEYOR_ASSIGNED', 'CRITICAL', 24), ('SURVEYOR_ASSIGNED', 'HIGH', 48), ('SURVEYOR_ASSIGNED', 'NORMAL', 96), ('SURVEYOR_ASSIGNED', 'LOW', 168),
    ('SURVEYOR_REPORT_RECEIVED', 'CRITICAL', 24), ('SURVEYOR_REPORT_RECEIVED', 'HIGH', 48), ('SURVEYOR_REPORT_RECEIVED', 'NORMAL', 72), ('SURVEYOR_REPORT_RECEIVED', 'LOW', 120),
    ('APPROVED', 'CRITICAL', 24),  ('APPROVED', 'HIGH', 48),  ('APPROVED', 'NORMAL', 72),  ('APPROVED', 'LOW', 120);
