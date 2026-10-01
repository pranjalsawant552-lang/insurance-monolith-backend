-- MONOLITH MERGE: originally customer-service/V1__initial_schema.sql
-- ============================================================
-- Customer Service Schema
-- ============================================================

CREATE TABLE customers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID UNIQUE,                -- links to auth-service user
    customer_code       VARCHAR(20) NOT NULL UNIQUE,

    -- Personal info
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    date_of_birth       DATE NOT NULL,
    gender              VARCHAR(10) NOT NULL,
    marital_status      VARCHAR(20),
    occupation          VARCHAR(100),
    annual_income       NUMERIC(15,2),
    nationality         VARCHAR(50) DEFAULT 'Indian',

    -- Contact
    email               VARCHAR(255) NOT NULL UNIQUE,
    mobile_number       VARCHAR(20) NOT NULL,
    alternate_mobile    VARCHAR(20),
    alternate_email     VARCHAR(255),

    -- KYC
    kyc_status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    -- PENDING, IN_REVIEW, VERIFIED, REJECTED
    kyc_verified_at     TIMESTAMP WITH TIME ZONE,
    kyc_verified_by     UUID,
    kyc_rejection_reason TEXT,

    -- Identity
    pan_number          VARCHAR(20),
    aadhaar_number      VARCHAR(30),  -- encrypted
    passport_number     VARCHAR(30),
    driving_license     VARCHAR(30),

    -- Health (for health insurance underwriting)
    blood_group         VARCHAR(5),
    height_cm           NUMERIC(5,1),
    weight_kg           NUMERIC(5,1),
    smoker              BOOLEAN DEFAULT FALSE,
    pre_existing_conditions TEXT,

    -- Multi-tenant
    company_id          UUID NOT NULL,
    branch_id           UUID,
    assigned_agent_id   UUID,

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

CREATE INDEX idx_customers_email ON customers(email) WHERE is_deleted = FALSE;
CREATE INDEX idx_customers_mobile ON customers(mobile_number) WHERE is_deleted = FALSE;
CREATE INDEX idx_customers_kyc ON customers(kyc_status) WHERE is_deleted = FALSE;
CREATE INDEX idx_customers_company ON customers(company_id);
CREATE INDEX idx_customers_agent ON customers(assigned_agent_id);
CREATE INDEX idx_customers_user_id ON customers(user_id);

-- Customer addresses
CREATE TABLE customer_addresses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    address_type    VARCHAR(20) NOT NULL,   -- PERMANENT, CORRESPONDENCE, OFFICE
    address_line1   VARCHAR(255) NOT NULL,
    address_line2   VARCHAR(255),
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100) NOT NULL,
    pincode         VARCHAR(10) NOT NULL,
    country         VARCHAR(50) DEFAULT 'India',
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_address_customer ON customer_addresses(customer_id);

-- KYC documents uploaded by customer
CREATE TABLE kyc_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    document_type   VARCHAR(50) NOT NULL,   -- AADHAAR, PAN, PASSPORT, DRIVING_LICENSE, VOTER_ID, UTILITY_BILL, PHOTO
    file_name       VARCHAR(255) NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT,
    mime_type       VARCHAR(100),
    ocr_verified    BOOLEAN DEFAULT FALSE,
    ocr_data        JSONB,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING, VERIFIED, REJECTED
    rejection_reason TEXT,
    uploaded_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    verified_at     TIMESTAMP WITH TIME ZONE,
    verified_by     UUID,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_kyc_docs_customer ON kyc_documents(customer_id);

-- Bank accounts for premium payment / claim disbursement
CREATE TABLE customer_bank_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    bank_name       VARCHAR(100) NOT NULL,
    account_number  VARCHAR(30) NOT NULL,  -- encrypted at application level
    ifsc_code       VARCHAR(15) NOT NULL,
    account_type    VARCHAR(20) NOT NULL,  -- SAVINGS, CURRENT
    account_holder  VARCHAR(255) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    is_verified     BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);

-- Communication preferences
CREATE TABLE customer_preferences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL UNIQUE REFERENCES customers(id) ON DELETE CASCADE,
    email_alerts    BOOLEAN DEFAULT TRUE,
    sms_alerts      BOOLEAN DEFAULT TRUE,
    push_alerts     BOOLEAN DEFAULT TRUE,
    preferred_lang  VARCHAR(10) DEFAULT 'en',
    renewal_reminder_days INT DEFAULT 30,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Customer notes (CRM style)
CREATE TABLE customer_notes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    note        TEXT NOT NULL,
    created_by  UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ language 'plpgsql';

CREATE TRIGGER trg_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
