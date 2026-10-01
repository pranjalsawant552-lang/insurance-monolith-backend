-- MONOLITH MERGE: originally audit-service/V1__initial_schema.sql
-- ============================================================
-- Audit Service - append-only cross-service audit trail
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE audit_log_entries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type       VARCHAR(50)  NOT NULL,
    entity_id         VARCHAR(100) NOT NULL,
    action            VARCHAR(50)  NOT NULL,
    actor_id          UUID,
    actor_role        VARCHAR(50),
    company_id        UUID,
    branch_id         UUID,
    before_state      JSONB,
    after_state       JSONB,
    summary           VARCHAR(500),
    ip_address        VARCHAR(64),
    correlation_id    VARCHAR(100),
    source_service    VARCHAR(50),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_log_entries(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_log_entries(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_created_at
    ON audit_log_entries(created_at);
CREATE INDEX idx_audit_company ON audit_log_entries(company_id);

-- ------------------------------------------------------------
-- Defense in depth: this table must never be updated or deleted
-- through any path. The application layer already enforces this
-- (AuditLogEntryRepository exposes no update/delete method), but
-- database-level privilege revocation is the real backstop.
--
-- Uncomment and adapt in production once the service's dedicated
-- DB role is provisioned (left commented here since the exact role
-- name/provisioning step is environment-specific and running this
-- against the wrong role would lock the service's own migrations
-- out too):
--
-- REVOKE UPDATE, DELETE ON audit_log_entries FROM audit_service_app_user;
-- GRANT SELECT, INSERT ON audit_log_entries TO audit_service_app_user;
-- ------------------------------------------------------------
