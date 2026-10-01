-- MONOLITH MERGE: originally policy-service/V1__initial_schema.sql
-- ============================================================
-- Policy Service Schema
-- ============================================================

-- MONOLITH MERGE NOTE: policy-service's own local "insurance_products" /
-- "product_riders" tables were deliberately dropped here — they were a
-- never-synced copy of product-service's real catalog (see V9's original
-- comment) that this schema now shares directly with product-service's V5
-- migration. Verified zero rows were ever written to the local copies by
-- any service code before removing them. policies.product_id below now has
-- no local FK (never did, functionally — see V9), same pattern already
-- used for customer_id/agent_id.

-- Main policies table
CREATE TABLE policies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_number       VARCHAR(50) NOT NULL UNIQUE,
    product_id          UUID NOT NULL,
    customer_id         UUID NOT NULL,           -- FK to customer-service (no DB constraint across services)
    agent_id            UUID,                    -- FK to agent-service
    company_id          UUID NOT NULL,
    branch_id           UUID,

    -- Coverage
    insurance_type      VARCHAR(20) NOT NULL,
    sum_assured         NUMERIC(15,2) NOT NULL,
    premium_amount      NUMERIC(12,2) NOT NULL,
    premium_frequency   VARCHAR(20) NOT NULL,    -- MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY
    policy_term_years   INT NOT NULL,

    -- Dates
    commencement_date   DATE NOT NULL,
    maturity_date       DATE NOT NULL,
    next_due_date       DATE,
    last_paid_date      DATE,

    -- Status
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    -- PENDING, ACTIVE, LAPSED, SURRENDERED, MATURED, CANCELLED, CLAIMED

    -- Financial
    total_premium_paid  NUMERIC(15,2) NOT NULL DEFAULT 0,
    loan_outstanding    NUMERIC(15,2) DEFAULT 0,

    -- Grace period
    grace_period_days   INT NOT NULL DEFAULT 30,

    -- Notes
    notes               TEXT,
    rejection_reason    TEXT,

    -- Soft delete & audit
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    deleted_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by          UUID NOT NULL,
    updated_by          UUID,
    issued_at           TIMESTAMP WITH TIME ZONE,
    issued_by           UUID
);

CREATE INDEX idx_policies_customer ON policies(customer_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_policies_agent ON policies(agent_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_policies_status ON policies(status) WHERE is_deleted = FALSE;
CREATE INDEX idx_policies_company ON policies(company_id);
CREATE INDEX idx_policies_due_date ON policies(next_due_date) WHERE status = 'ACTIVE';
CREATE INDEX idx_policies_number ON policies(policy_number);

-- Policy riders (selected per-policy)
CREATE TABLE policy_riders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    rider_id        UUID NOT NULL,
    premium         NUMERIC(10,2) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

-- Life insurance specific
CREATE TABLE life_policy_details (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id           UUID NOT NULL UNIQUE REFERENCES policies(id) ON DELETE CASCADE,
    plan_type           VARCHAR(50),             -- TERM, ENDOWMENT, WHOLE_LIFE, ULIP
    death_benefit       NUMERIC(15,2),
    survival_benefit    NUMERIC(15,2),
    bonus_accrued       NUMERIC(12,2) DEFAULT 0
);

-- Life insurance nominees
CREATE TABLE policy_nominees (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    full_name       VARCHAR(255) NOT NULL,
    relationship    VARCHAR(100) NOT NULL,
    date_of_birth   DATE,
    share_percent   NUMERIC(5,2) NOT NULL DEFAULT 100,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    mobile          VARCHAR(20),
    email           VARCHAR(255),
    address         TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Health insurance specific
CREATE TABLE health_policy_details (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id           UUID NOT NULL UNIQUE REFERENCES policies(id) ON DELETE CASCADE,
    plan_type           VARCHAR(50),             -- INDIVIDUAL, FAMILY, SENIOR
    room_rent_limit     NUMERIC(10,2),
    day_care_covered    BOOLEAN DEFAULT TRUE,
    maternity_covered   BOOLEAN DEFAULT FALSE,
    pre_existing_wait   INT DEFAULT 48,          -- months waiting period
    network_hospital_id UUID
);

-- Health family members covered
CREATE TABLE health_insured_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    full_name       VARCHAR(255) NOT NULL,
    relationship    VARCHAR(100) NOT NULL,
    date_of_birth   DATE NOT NULL,
    gender          VARCHAR(10),
    sum_insured     NUMERIC(12,2)
);

-- Motor insurance specific
CREATE TABLE motor_policy_details (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id           UUID NOT NULL UNIQUE REFERENCES policies(id) ON DELETE CASCADE,
    vehicle_number      VARCHAR(20) NOT NULL,
    vehicle_make        VARCHAR(100),
    vehicle_model       VARCHAR(100),
    vehicle_year        INT,
    vehicle_type        VARCHAR(50),             -- TWO_WHEELER, CAR, COMMERCIAL
    engine_number       VARCHAR(100),
    chassis_number      VARCHAR(100),
    cubic_capacity      INT,
    seating_capacity    INT,
    idv_value           NUMERIC(12,2),           -- Insured Declared Value
    policy_type         VARCHAR(30),             -- COMPREHENSIVE, THIRD_PARTY
    ncb_percent         NUMERIC(5,2) DEFAULT 0,  -- No Claim Bonus
    garage_network_id   UUID
);

-- Policy status history (audit trail)
CREATE TABLE policy_status_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID NOT NULL REFERENCES policies(id),
    old_status      VARCHAR(30),
    new_status      VARCHAR(30) NOT NULL,
    reason          TEXT,
    changed_by      UUID NOT NULL,
    changed_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ip_address      VARCHAR(45)
);

CREATE INDEX idx_status_history_policy ON policy_status_history(policy_id);

-- Policy documents
CREATE TABLE policy_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID NOT NULL REFERENCES policies(id),
    document_type   VARCHAR(100) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,       -- S3 key or MinIO path
    file_size_bytes BIGINT,
    mime_type       VARCHAR(100),
    uploaded_by     UUID NOT NULL,
    uploaded_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_policy_docs_policy ON policy_documents(policy_id) WHERE is_deleted = FALSE;

-- Updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ language 'plpgsql';

CREATE TRIGGER trg_policies_updated_at
    BEFORE UPDATE ON policies
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_products_updated_at
ON insurance_products;

CREATE TRIGGER trg_products_updated_at
BEFORE UPDATE ON insurance_products
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();