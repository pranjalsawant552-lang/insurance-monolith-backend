-- MONOLITH MERGE: originally claims-service/V3__idempotency_keys.sql
-- ============================================================
-- Claims Service - Phase 6: idempotency keys on approve/settle
--
-- New V3 file, not an edit to V2 (Phase 5's SLA migration) — V2 was already
-- delivered in a prior zip. Every phase gets its own new migration, never
-- an edit to a prior one (see Phase 4's changelog for why).
-- ============================================================

CREATE TABLE claims_idempotency_records (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key   VARCHAR(200) NOT NULL,
    endpoint          VARCHAR(100) NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    response_status   INT NOT NULL,
    response_body     TEXT NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (idempotency_key, endpoint)
);

CREATE INDEX idx_claims_idempotency_expires_at ON claims_idempotency_records(expires_at);
