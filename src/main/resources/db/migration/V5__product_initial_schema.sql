-- MONOLITH MERGE: originally product-service/V1__initial_schema.sql
-- ============================================================
-- Product Service Schema  (centralised plan configuration)
-- ============================================================

CREATE TABLE insurance_products (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    insurance_type      VARCHAR(20) NOT NULL,   -- LIFE, HEALTH, MOTOR
    category            VARCHAR(50),            -- TERM, ENDOWMENT, FLOATER, COMPREHENSIVE, etc.
    description         TEXT,
    key_features        TEXT,                   -- JSON array of feature strings
    base_premium        NUMERIC(12,2) NOT NULL,
    min_sum_assured     NUMERIC(15,2),
    max_sum_assured     NUMERIC(15,2),
    min_age             INT,
    max_age             INT,
    min_term_years      INT,
    max_term_years      INT,
    grace_period_days   INT NOT NULL DEFAULT 30,
    free_look_days      INT NOT NULL DEFAULT 15,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    is_featured         BOOLEAN NOT NULL DEFAULT FALSE,
    company_id          UUID NOT NULL,
    branch_id           UUID,
    created_by          UUID NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_products_type    ON insurance_products(insurance_type) WHERE is_deleted = FALSE;
CREATE INDEX idx_products_company ON insurance_products(company_id);
CREATE INDEX idx_products_active  ON insurance_products(is_active) WHERE is_deleted = FALSE;

-- Riders / add-ons attached to products
CREATE TABLE product_riders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID NOT NULL REFERENCES insurance_products(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    code                VARCHAR(50) NOT NULL,
    description         TEXT,
    additional_premium  NUMERIC(10,2) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE
);

-- Premium rate table (factor-based calculation)
CREATE TABLE premium_rate_factors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES insurance_products(id) ON DELETE CASCADE,
    factor_type     VARCHAR(50) NOT NULL,   -- AGE_BAND, TERM, FREQUENCY, SUM_ASSURED_BAND
    min_value       NUMERIC(10,2),
    max_value       NUMERIC(10,2),
    label           VARCHAR(100),
    multiplier      NUMERIC(8,4) NOT NULL DEFAULT 1.0000,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rate_factors_product ON premium_rate_factors(product_id, factor_type);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ language 'plpgsql';

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON insurance_products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
