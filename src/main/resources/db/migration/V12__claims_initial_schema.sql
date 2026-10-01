-- MONOLITH MERGE: originally claims-service/V1__initial_schema.sql
-- ============================================================
-- Claims Service Schema
-- ============================================================

-- Master claims table
CREATE TABLE claims (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_number        VARCHAR(30) NOT NULL UNIQUE,

    -- Policy and customer references (cross-service, no FK constraint)
    policy_id           UUID NOT NULL,
    policy_number       VARCHAR(50) NOT NULL,
    customer_id         UUID NOT NULL,
    company_id          UUID NOT NULL,
    branch_id           UUID,

    -- Claim type
    insurance_type      VARCHAR(20) NOT NULL,   -- LIFE, HEALTH, MOTOR
    claim_type          VARCHAR(50) NOT NULL,   -- DEATH, MATURITY, ACCIDENT, HOSPITALIZATION, etc.

    -- Claim details
    incident_date       DATE NOT NULL,
    reported_date       DATE NOT NULL DEFAULT CURRENT_DATE,
    description         TEXT NOT NULL,
    claimed_amount      NUMERIC(15,2) NOT NULL,
    approved_amount     NUMERIC(15,2),
    settled_amount      NUMERIC(15,2),

    -- Workflow status
    status              VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    -- SUBMITTED → UNDER_REVIEW → DOCS_REQUIRED → SURVEYOR_ASSIGNED →
    -- SURVEYOR_REPORT_RECEIVED → APPROVED → REJECTED → SETTLED → CLOSED

    -- Assignment
    assigned_to         UUID,               -- claims officer
    surveyor_id         UUID,               -- external surveyor

    -- Rejection / settlement
    rejection_reason    TEXT,
    settlement_date     DATE,
    settlement_reference VARCHAR(100),

    -- Priority
    priority            VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    -- LOW, NORMAL, HIGH, CRITICAL

    -- Fraud flag
    fraud_flag          BOOLEAN NOT NULL DEFAULT FALSE,
    fraud_score         NUMERIC(5,2),

    -- Soft delete
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    deleted_by          UUID,

    -- Audit
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by          UUID NOT NULL,
    updated_by          UUID
);

CREATE INDEX idx_claims_policy       ON claims(policy_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_claims_customer     ON claims(customer_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_claims_status       ON claims(status) WHERE is_deleted = FALSE;
CREATE INDEX idx_claims_company      ON claims(company_id);
CREATE INDEX idx_claims_assigned     ON claims(assigned_to);
CREATE INDEX idx_claims_number       ON claims(claim_number);
CREATE INDEX idx_claims_fraud        ON claims(fraud_flag) WHERE fraud_flag = TRUE;

-- Claim workflow history
CREATE TABLE claim_workflow_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID NOT NULL REFERENCES claims(id),
    old_status      VARCHAR(30),
    new_status      VARCHAR(30) NOT NULL,
    action          VARCHAR(100),
    comments        TEXT,
    performed_by    UUID NOT NULL,
    performed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_claim_workflow ON claim_workflow_history(claim_id);

-- Claim documents
CREATE TABLE claim_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID NOT NULL REFERENCES claims(id),
    document_type   VARCHAR(100) NOT NULL,
    -- DEATH_CERTIFICATE, POLICE_FIR, HOSPITAL_BILL, DISCHARGE_SUMMARY,
    -- REPAIR_ESTIMATE, PHOTOS, ID_PROOF, BANK_DETAILS, etc.
    file_name       VARCHAR(255) NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT,
    mime_type       VARCHAR(100),
    uploaded_by     UUID NOT NULL,
    uploaded_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_mandatory    BOOLEAN DEFAULT FALSE,
    is_verified     BOOLEAN DEFAULT FALSE,
    verified_by     UUID,
    verified_at     TIMESTAMP WITH TIME ZONE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_claim_docs ON claim_documents(claim_id) WHERE is_deleted = FALSE;

-- Surveyor reports
CREATE TABLE surveyor_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID NOT NULL REFERENCES claims(id),
    surveyor_id     UUID NOT NULL,
    surveyor_name   VARCHAR(255),
    survey_date     DATE NOT NULL,
    assessed_amount NUMERIC(15,2) NOT NULL,
    findings        TEXT NOT NULL,
    recommendation  VARCHAR(30) NOT NULL,   -- APPROVE, REJECT, PARTIAL
    report_key      VARCHAR(500),           -- S3 key for PDF report
    submitted_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Life claim specific
CREATE TABLE life_claim_details (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID NOT NULL UNIQUE REFERENCES claims(id),
    claimant_name   VARCHAR(255) NOT NULL,
    claimant_relation VARCHAR(100) NOT NULL,
    claimant_mobile VARCHAR(20),
    cause_of_death  VARCHAR(255),
    death_date      DATE,
    death_place     VARCHAR(255)
);

-- Health claim specific
CREATE TABLE health_claim_details (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID NOT NULL UNIQUE REFERENCES claims(id),
    hospital_name   VARCHAR(255),
    hospital_id     UUID,
    admission_date  DATE,
    discharge_date  DATE,
    diagnosis       TEXT,
    treatment       TEXT,
    is_cashless     BOOLEAN DEFAULT FALSE,
    tpa_reference   VARCHAR(100)            -- Third Party Administrator reference
);

-- Motor claim specific
CREATE TABLE motor_claim_details (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID NOT NULL UNIQUE REFERENCES claims(id),
    vehicle_number  VARCHAR(20),
    accident_location TEXT,
    accident_description TEXT,
    police_fir_number VARCHAR(100),
    garage_name     VARCHAR(255),
    garage_id       UUID,
    repair_estimate NUMERIC(12,2),
    final_repair_cost NUMERIC(12,2),
    is_total_loss   BOOLEAN DEFAULT FALSE
);

-- Claim notes (internal CRM)
CREATE TABLE claim_notes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id    UUID NOT NULL REFERENCES claims(id),
    note        TEXT NOT NULL,
    is_internal BOOLEAN DEFAULT TRUE,
    created_by  UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ language 'plpgsql';

CREATE TRIGGER trg_claims_updated_at
    BEFORE UPDATE ON claims
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
