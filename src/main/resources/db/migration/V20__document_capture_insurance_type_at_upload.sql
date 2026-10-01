-- MONOLITH MERGE: originally document-service/V3__capture_insurance_type_at_upload.sql
-- ============================================================
-- Document Service - Phase 10: capture insuranceType/productName at upload
--
-- The Phase 9 changelog on the policy-service side flagged this as the real
-- fix: the extraction template can't reliably read insurance type or
-- product name off a PDF, so ingestions that need them were falling into a
-- manual-fix queue on a guess. The person uploading the document (an agent,
-- almost always) already knows both — they're selecting a document type and
-- an insurer at upload time already. Asking for two more fields there is a
-- far more reliable source of truth than OCR + keyword inference, and
-- removes the guessing entirely for the common case.
-- ============================================================

ALTER TABLE documents
    ADD COLUMN insurance_type VARCHAR(20),
    ADD COLUMN product_name   VARCHAR(255);

COMMENT ON COLUMN documents.insurance_type IS
    'LIFE / HEALTH / MOTOR — set by the uploader at upload time for POLICY_SCHEDULE-type documents. Passed through to policy-service on verify so ingestion does not have to infer it from extracted text.';
COMMENT ON COLUMN documents.product_name IS
    'The insurer''s product name as the uploader knows it (e.g. "Family Health Optima") — optional, but enables Policy.renewedFromPolicyId auto-linking on the policy-service side, which needs both insurer name and product name to match a renewal.';
