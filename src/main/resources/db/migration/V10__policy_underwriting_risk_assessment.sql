-- MONOLITH MERGE: originally policy-service/V3__underwriting_risk_assessment.sql
-- ============================================================
-- Policy Service - Phase 4: underwriting risk assessment
--
-- NOTE: this is a new V3 migration rather than editing V2, deliberately —
-- V2 was already delivered as part of Phase 3. Modifying an already-shipped
-- migration file breaks Flyway's checksum validation for anyone who applied
-- it already. Every phase from here on gets its own new migration file, never
-- an edit to a prior one, even for a single-column addition like this.
-- ============================================================

ALTER TABLE policies ADD COLUMN risk_assessment_id UUID;

COMMENT ON COLUMN policies.risk_assessment_id IS
    'References risk-fraud-service''s risk_assessments.id — the full underwriting score breakdown that decided whether this policy needed manual review.';
