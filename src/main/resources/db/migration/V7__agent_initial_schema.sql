-- MONOLITH MERGE: originally agent-service/V1__initial_schema.sql
-- ============================================================
-- Agent Service Schema
-- ============================================================

CREATE TABLE agents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_code          VARCHAR(20) NOT NULL UNIQUE,
    user_id             UUID,                   -- links to auth-service user
    agent_type          VARCHAR(20) NOT NULL,   -- AGENT, BROKER, CORPORATE_AGENT
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    mobile_number       VARCHAR(20) NOT NULL,
    pan_number          VARCHAR(20),
    license_number      VARCHAR(50),            -- IRDAI license number
    license_expiry      DATE,
    qualification       VARCHAR(100),

    -- Hierarchy
    manager_id          UUID REFERENCES agents(id),
    company_id          UUID NOT NULL,
    branch_id           UUID,
    region              VARCHAR(100),

    -- Commission config
    commission_rate_life    NUMERIC(5,2) DEFAULT 30,    -- % of first year premium
    commission_rate_health  NUMERIC(5,2) DEFAULT 15,
    commission_rate_motor   NUMERIC(5,2) DEFAULT 10,

    -- Bank details for commission payout
    bank_name           VARCHAR(100),
    account_number      VARCHAR(30),
    ifsc_code           VARCHAR(15),
    account_holder      VARCHAR(255),

    -- Status
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    -- ACTIVE, INACTIVE, SUSPENDED, TERMINATED

    -- Soft delete & audit
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    deleted_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by          UUID NOT NULL
);

CREATE INDEX idx_agents_company  ON agents(company_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_agents_manager  ON agents(manager_id);
CREATE INDEX idx_agents_code     ON agents(agent_code);
CREATE INDEX idx_agents_license  ON agents(license_number);

-- Commission records
CREATE TABLE agent_commissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id            UUID NOT NULL REFERENCES agents(id),
    policy_id           UUID NOT NULL,
    policy_number       VARCHAR(50) NOT NULL,
    customer_id         UUID NOT NULL,
    company_id          UUID NOT NULL,
    insurance_type      VARCHAR(20) NOT NULL,
    commission_type     VARCHAR(30) NOT NULL,   -- FIRST_YEAR, RENEWAL, BONUS
    premium_amount      NUMERIC(12,2) NOT NULL,
    commission_rate     NUMERIC(5,2) NOT NULL,
    commission_amount   NUMERIC(12,2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING, APPROVED, PAID, CANCELLED
    payment_reference   VARCHAR(100),
    paid_at             TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commission_agent  ON agent_commissions(agent_id);
CREATE INDEX idx_commission_policy ON agent_commissions(policy_id);
CREATE INDEX idx_commission_status ON agent_commissions(status);

-- Agent performance summary (updated by scheduler)
CREATE TABLE agent_performance (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id            UUID NOT NULL UNIQUE REFERENCES agents(id),
    total_policies      INT DEFAULT 0,
    active_policies     INT DEFAULT 0,
    total_premium       NUMERIC(15,2) DEFAULT 0,
    total_commission    NUMERIC(12,2) DEFAULT 0,
    pending_commission  NUMERIC(12,2) DEFAULT 0,
    last_calculated_at  TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ language 'plpgsql';

CREATE TRIGGER trg_agents_updated_at
    BEFORE UPDATE ON agents FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_commission_updated_at
    BEFORE UPDATE ON agent_commissions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
