-- MONOLITH MERGE: originally risk-fraud-service/V1__initial_schema.sql
-- ============================================================
-- Risk & Fraud Service - initial schema
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE risk_factor_configs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    insurance_type    VARCHAR(20) NOT NULL,
    factor_type       VARCHAR(50) NOT NULL,
    min_value         NUMERIC(10,2),
    max_value         NUMERIC(10,2),
    condition_value   VARCHAR(100),
    label             VARCHAR(150),
    weight            NUMERIC(6,2) NOT NULL,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_factors_type ON risk_factor_configs(insurance_type, active);

CREATE TABLE risk_assessments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id               UUID,
    customer_id             UUID,
    insurance_type          VARCHAR(20) NOT NULL,
    product_id              UUID NOT NULL,
    age                     INT,
    bmi                     NUMERIC(5,2),
    smoker                  BOOLEAN,
    pre_existing_condition  BOOLEAN,
    occupation_class        VARCHAR(50),
    sum_assured             NUMERIC(15,2),
    annual_income           NUMERIC(15,2),
    prior_claims_count      INT,
    vehicle_age             INT,
    vehicle_type            VARCHAR(50),
    location_risk_zone      VARCHAR(50),
    risk_score              NUMERIC(5,2) NOT NULL,
    factors_applied         JSONB NOT NULL,
    decision                VARCHAR(20) NOT NULL,
    requested_by            UUID,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_assessments_policy ON risk_assessments(policy_id);
CREATE INDEX idx_risk_assessments_created_at ON risk_assessments(created_at);

CREATE TABLE fraud_rule_configs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_type         VARCHAR(50) NOT NULL UNIQUE,
    threshold_days    INT,
    weight            NUMERIC(5,2) NOT NULL,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    description       VARCHAR(255),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE fraud_evaluations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id          UUID NOT NULL,
    policy_id         UUID NOT NULL,
    customer_id       UUID,
    fraud_score       NUMERIC(5,2) NOT NULL,
    triggered_rules   JSONB NOT NULL,
    recommend_flag    BOOLEAN NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_evaluations_claim ON fraud_evaluations(claim_id);
CREATE INDEX idx_fraud_evaluations_created_at ON fraud_evaluations(created_at);

-- ------------------------------------------------------------
-- Seed: sensible default risk factors per insurance line. Starting points
-- for an underwriting team to tune, not actuarially validated numbers —
-- see the Phase 4 changelog.
-- ------------------------------------------------------------
INSERT INTO risk_factor_configs (insurance_type, factor_type, min_value, max_value, condition_value, label, weight) VALUES
    ('LIFE', 'AGE', 0, 30, NULL, 'Age up to 30', 5),
    ('LIFE', 'AGE', 31, 45, NULL, 'Age 31-45', 10),
    ('LIFE', 'AGE', 46, 60, NULL, 'Age 46-60', 25),
    ('LIFE', 'AGE', 61, NULL, NULL, 'Age 61+', 45),
    ('LIFE', 'SMOKER', NULL, NULL, 'TRUE', 'Smoker', 20),
    ('LIFE', 'PRE_EXISTING_CONDITION', NULL, NULL, 'TRUE', 'Pre-existing condition disclosed', 15),
    ('LIFE', 'BMI', 30, NULL, NULL, 'BMI 30+ (obese)', 10),
    ('LIFE', 'SUM_ASSURED_RATIO', 15, NULL, NULL, 'Sum assured > 15x annual income', 15),

    ('HEALTH', 'AGE', 0, 35, NULL, 'Age up to 35', 5),
    ('HEALTH', 'AGE', 36, 50, NULL, 'Age 36-50', 15),
    ('HEALTH', 'AGE', 51, NULL, NULL, 'Age 51+', 30),
    ('HEALTH', 'PRE_EXISTING_CONDITION', NULL, NULL, 'TRUE', 'Pre-existing condition disclosed', 25),
    ('HEALTH', 'BMI', 30, NULL, NULL, 'BMI 30+ (obese)', 12),
    ('HEALTH', 'PRIOR_CLAIMS_COUNT', 2, NULL, NULL, '2+ prior claims', 15),

    ('MOTOR', 'VEHICLE_AGE', 8, NULL, NULL, 'Vehicle 8+ years old', 15),
    ('MOTOR', 'VEHICLE_TYPE', NULL, NULL, 'COMMERCIAL', 'Commercial vehicle', 20),
    ('MOTOR', 'VEHICLE_TYPE', NULL, NULL, 'SUV', 'SUV', 8),
    ('MOTOR', 'PRIOR_CLAIMS_COUNT', 1, NULL, NULL, '1+ prior claims', 20),
    ('MOTOR', 'LOCATION_RISK_ZONE', NULL, NULL, 'HIGH', 'High-risk location zone', 15);

-- ------------------------------------------------------------
-- Seed: the four fraud rule types, disabled by default where they need
-- upstream data this phase doesn't wire in yet (DOCUMENT_MISMATCH).
-- ------------------------------------------------------------
INSERT INTO fraud_rule_configs (rule_type, threshold_days, weight, active, description) VALUES
    ('EARLY_CLAIM_AFTER_ISSUANCE', 90, 40, TRUE, 'Claim filed within 90 days of policy commencement'),
    ('DUPLICATE_CLAIM', 30, 50, TRUE, 'Another claim on the same policy within a 30-day window'),
    ('HIGH_CLAIM_FREQUENCY', 365, 25, TRUE, '3 or more claims by the same customer within a year'),
    ('DOCUMENT_MISMATCH', NULL, 30, FALSE, 'Claim document identity does not match policyholder KYC — requires document-service OCR integration, not yet wired (see Phase 4 changelog)');
