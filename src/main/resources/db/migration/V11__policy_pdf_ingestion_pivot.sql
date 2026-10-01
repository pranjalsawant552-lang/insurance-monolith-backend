-- MONOLITH MERGE: originally policy-service/V4__pdf_ingestion_pivot.sql
-- ============================================================
-- Policy Service - Phase 9: PDF-ingestion pivot
--
-- Context: the platform no longer prices or issues policies itself.
-- Insurers issue the policy; an agent/customer uploads the resulting PDF;
-- document-service extracts + a human verifies the fields; THIS service
-- now creates the Policy record directly from that verified data, either
-- automatically (Kafka event from document-service on verify) or via a
-- manual fallback endpoint. product_id can no longer be required — an
-- ingested policy was sold by an outside insurer against a product that
-- very likely has no row in this platform's own product catalog at all.
--
-- Per Phase 3's migration note: never edit a shipped migration, always add
-- a new one, even for what looks like a small change.
-- ============================================================

-- product_id's FK constraint was already dropped in V2 (cross-service
-- reference, no DB-level constraint) but it was still NOT NULL — an
-- ingested policy has no local product-service row to point at.
ALTER TABLE policies ALTER COLUMN product_id DROP NOT NULL;

ALTER TABLE policies
    ADD COLUMN insurer_name            VARCHAR(255),
    ADD COLUMN ingestion_source        VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_extraction_id    UUID,
    ADD COLUMN source_document_id      UUID,
    ADD COLUMN renewed_from_policy_id  UUID;

COMMENT ON COLUMN policies.insurer_name IS
    'The outside insurance company that issued this policy (Star Health, HDFC ERGO, etc.) — always set for an ingested policy; null for the legacy self-issuance path.';
COMMENT ON COLUMN policies.ingestion_source IS
    'PDF_INGESTION = created from a verified document-service extraction (the normal path). MANUAL = ops/agent typed the policy in directly (PDF unavailable/unreadable). SELF_ISSUED = legacy pre-pivot path, kept for historical records only.';
COMMENT ON COLUMN policies.source_extraction_id IS
    'References document-service''s extraction_results.id that this policy was created from — the audit trail back to the original PDF and the reviewer who verified it.';
COMMENT ON COLUMN policies.source_document_id IS
    'References document-service''s documents.id — the original policy-schedule PDF, always kept as the source of truth per the client briefing.';
COMMENT ON COLUMN policies.renewed_from_policy_id IS
    'Self-reference to the prior term''s Policy row when this ingestion was auto-linked as a renewal (same customer + insurer + product, commencement date close to the prior policy''s maturity date).';

CREATE INDEX idx_policies_source_extraction ON policies(source_extraction_id) WHERE source_extraction_id IS NOT NULL;
CREATE INDEX idx_policies_renewed_from ON policies(renewed_from_policy_id) WHERE renewed_from_policy_id IS NOT NULL;

-- Ingestion failures: when an automatic (Kafka-driven) or manual ingestion
-- can't be mapped into a valid Policy (a required field was missing/
-- unparseable from the verified extraction), it lands here instead of
-- silently failing or crashing the Kafka consumer — an ops queue to fix by
-- hand via POST /policies/ingestion-failures/{id}/resolve.
CREATE TABLE policy_ingestion_failures (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_extraction_id UUID,
    source_document_id  UUID,
    company_id          UUID NOT NULL,
    customer_id         UUID,
    raw_fields          JSONB,
    error_message       VARCHAR(1000) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, RESOLVED, DISMISSED
    resolved_policy_id  UUID,
    resolved_by         UUID,
    resolved_at         TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ingestion_failures_status ON policy_ingestion_failures(status);
CREATE INDEX idx_ingestion_failures_company ON policy_ingestion_failures(company_id);
