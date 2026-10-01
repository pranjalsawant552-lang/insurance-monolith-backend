-- MONOLITH FIX (audit report §6 / §38): none of the entities in this
-- codebase had optimistic locking, so two concurrent updates to the same
-- row (e.g. two staff members verifying the same extraction, two claim
-- approvals racing) would silently last-write-wins with no conflict
-- detection at all. Adds a `version` column to the seven highest-risk
-- entities named explicitly in the audit — customers, policies, claims,
-- extraction_results (the verification-queue concurrency gap called out
-- specifically), documents, agent_commissions, and payment_transactions.
-- DEFAULT 0 backfills existing rows so this is a safe, non-breaking
-- addition to a table that may already have data.

ALTER TABLE customers            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE policies             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE claims               ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE extraction_results   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE documents            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE agent_commissions    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_transactions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
