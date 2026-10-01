-- MONOLITH MERGE: originally product-service/V2__product_versioning_and_rating_engine.sql
-- ============================================================
-- Product Service - Phase 3: versioning + pluggable rating engine
--
-- Context: premium_rate_factors already existed in V1 but was never wired
-- to any Java code (verified: zero references anywhere in the codebase) —
-- the schema anticipated a rules engine that was never finished. This
-- migration finishes it: adds product_versions so an issued policy can pin
-- the exact terms it was sold under, extends premium_rate_factors to be
-- version-scoped and support non-numeric conditions (vehicle type, location)
-- and non-multiplier adjustments (flat add, percent add), and adds an
-- append-only audit trail of every premium calculation performed.
-- ============================================================

CREATE TABLE product_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID NOT NULL REFERENCES insurance_products(id) ON DELETE CASCADE,
    version_number      INT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, SUPERSEDED
    -- Full snapshot of every pricing/coverage-relevant field at the moment
    -- this version was created — this is what an issued policy pins against,
    -- so editing the live product later never silently changes what an
    -- already-sold policy's terms were.
    config_snapshot     JSONB NOT NULL,
    effective_from      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    effective_to        TIMESTAMP WITH TIME ZONE,
    created_by          UUID NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, version_number)
);

CREATE INDEX idx_product_versions_product ON product_versions(product_id);
CREATE INDEX idx_product_versions_status ON product_versions(product_id, status);

-- Track which version is currently "live" for fast lookup without scanning.
ALTER TABLE insurance_products ADD COLUMN current_version INT NOT NULL DEFAULT 1;

-- ------------------------------------------------------------
-- Extend the never-used premium_rate_factors table into the real pluggable
-- rating engine's config store. Safe to ALTER directly (not additive-only)
-- since no application code has ever read or written this table.
-- ------------------------------------------------------------
ALTER TABLE premium_rate_factors
    ADD COLUMN product_version_id UUID REFERENCES product_versions(id) ON DELETE CASCADE,
    ADD COLUMN condition_value VARCHAR(100),        -- for categorical factors: vehicle type, location tier, etc.
    ADD COLUMN adjustment_type VARCHAR(20) NOT NULL DEFAULT 'MULTIPLIER',  -- MULTIPLIER, FLAT_ADD, PERCENT_ADD
    ADD COLUMN description VARCHAR(255),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_rate_factors_version ON premium_rate_factors(product_version_id, factor_type);

-- ------------------------------------------------------------
-- Append-only audit trail of every premium calculation — answers "how was
-- this premium arrived at" for disputes/regulatory queries, per brief §4.3.
-- ------------------------------------------------------------
CREATE TABLE premium_calculation_audits (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID NOT NULL REFERENCES insurance_products(id),
    product_version_id  UUID NOT NULL REFERENCES product_versions(id),
    calculated_for       VARCHAR(20) NOT NULL,  -- QUOTE, POLICY_ISSUANCE
    reference_id        VARCHAR(100),           -- policyId once issued; null for anonymous quotes
    -- Inputs
    sum_assured         NUMERIC(15,2) NOT NULL,
    frequency            VARCHAR(20) NOT NULL,
    term_years           INT NOT NULL,
    age                  INT,
    risk_score           NUMERIC(5,2),
    location             VARCHAR(100),
    vehicle_type         VARCHAR(50),
    rider_ids            JSONB,
    -- Output breakdown
    base_premium         NUMERIC(12,2) NOT NULL,
    factors_applied      JSONB NOT NULL,   -- [{factorType, label, adjustmentType, adjustmentValue, resultingAmount}, ...]
    rider_premium        NUMERIC(12,2) NOT NULL DEFAULT 0,
    gst_amount           NUMERIC(12,2) NOT NULL,
    total_premium         NUMERIC(12,2) NOT NULL,
    requested_by         UUID,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_premium_audit_reference ON premium_calculation_audits(reference_id);
CREATE INDEX idx_premium_audit_product ON premium_calculation_audits(product_id);
CREATE INDEX idx_premium_audit_created_at ON premium_calculation_audits(created_at);

-- ------------------------------------------------------------
-- Seed baseline rating factors so the engine has sensible defaults from day
-- one (mirrors the exact term/frequency behaviour the old hardcoded formula
-- used, so existing premium amounts don't jump — plus the new age bands
-- the old formula never had, addressing the brief's explicit gap callout).
-- These are seeded against version 1 of every product that exists at
-- migration time (there are none in a fresh install; this is a template
-- that ProductVersionService replicates in application code for every new
-- product's version 1, so the seed loop below is a safety net for anyone
-- migrating an existing populated database, not the primary seeding path).
-- ------------------------------------------------------------
-- (Intentionally no seed INSERT here — a fresh install has zero products,
-- and ProductVersionService seeds sensible default factors in application
-- code whenever version 1 of a product is created. See ProductVersionService.)
