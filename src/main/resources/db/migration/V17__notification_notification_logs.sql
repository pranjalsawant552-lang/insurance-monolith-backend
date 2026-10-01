-- MONOLITH MERGE: originally notification-service/V1__notification_logs.sql
-- ============================================================
-- Notification Service - Phase 7: persistence + delivery tracking
--
-- Replaces the "sends are fire-and-forget, nothing is stored" gap flagged
-- in the original audit (§4.12).
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE notification_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel             VARCHAR(20)  NOT NULL,
    template            VARCHAR(80)  NOT NULL,
    recipient_id        UUID,
    recipient_address   VARCHAR(255),
    subject             VARCHAR(255),
    payload             JSONB,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    provider_message_id VARCHAR(100),
    failure_reason      VARCHAR(500),
    attempt_count       INT NOT NULL DEFAULT 0,
    max_attempts        INT NOT NULL DEFAULT 3,
    next_retry_at       TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at             TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_notif_log_recipient ON notification_logs(recipient_id);
CREATE INDEX idx_notif_log_status ON notification_logs(status);
CREATE INDEX idx_notif_log_created_at ON notification_logs(created_at);
CREATE INDEX idx_notif_log_retry ON notification_logs(status, next_retry_at) WHERE status = 'FAILED';
