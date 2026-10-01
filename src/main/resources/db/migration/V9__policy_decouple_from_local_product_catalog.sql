-- MONOLITH MERGE: originally policy-service/V2__decouple_from_local_product_catalog.sql
-- ============================================================
-- Policy Service - Phase 3: stop duplicating the product catalog
--
-- Context: policies.product_id was a foreign key into this service's own
-- local insurance_products table — a copy that nothing ever kept in sync
-- with product-service's real catalog (no Kafka consumer, no scheduled
-- reconciliation, nothing). Found while wiring policy-service to
-- product-service's new premium engine. product_id now points at
-- product-service's product id directly (no local FK — cross-service
-- references don't get a DB-level constraint, same pattern already used
-- for customer_id/agent_id in this exact table). product_code/product_name
-- are denormalized snapshots taken at issuance so the policy list/detail
-- screens don't need a live call to product-service just to render a name.
-- ============================================================

ALTER TABLE policies DROP CONSTRAINT IF EXISTS policies_product_id_fkey;

ALTER TABLE policies
    ADD COLUMN product_code VARCHAR(50),
    ADD COLUMN product_name VARCHAR(255),
    ADD COLUMN product_version INT,
    ADD COLUMN premium_calculation_id UUID;

COMMENT ON COLUMN policies.product_id IS
    'References product-service''s insurance_products.id — NOT a local FK. '
    'This service no longer maintains its own copy of the product catalog.';

COMMENT ON COLUMN policies.premium_calculation_id IS
    'References product-service''s premium_calculation_audits.id — the full '
    'factor-by-factor breakdown of how premium_amount was derived.';

-- MONOLITH MERGE NOTE: the follow-up this comment originally deferred —
-- "flagged as a fast-follow full removal" — is done as of the monolith
-- conversion. V8 in this merged sequence no longer creates the local
-- insurance_products/product_riders tables at all (they'd collide with
-- product-service's real ones, V5, in a single shared schema), and
-- PolicyRider's JPA mapping was cut over from a @ManyToOne on the local
-- ProductRider entity to a plain riderId UUID column — see PolicyRider.java.
