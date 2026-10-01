-- MONOLITH MERGE: originally payment-service/V1__initial_schema.sql
-- ============================================================
-- Payment Service Schema
-- ============================================================

CREATE TABLE payment_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_number  VARCHAR(40) NOT NULL UNIQUE,

    -- References (cross-service, no FK constraints)
    policy_id           UUID NOT NULL,
    policy_number       VARCHAR(50) NOT NULL,
    customer_id         UUID NOT NULL,
    company_id          UUID NOT NULL,

    -- Transaction details
    transaction_type    VARCHAR(30) NOT NULL,
    -- PREMIUM, CLAIM_SETTLEMENT, REFUND, COMMISSION

    amount              NUMERIC(12,2) NOT NULL,
    currency            VARCHAR(5) NOT NULL DEFAULT 'INR',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING, PROCESSING, SUCCESS, FAILED, REFUNDED

    -- Payment method
    payment_method      VARCHAR(30),
    -- UPI, NET_BANKING, CARD, CHEQUE, NEFT, RTGS, CASH

    -- Gateway details
    gateway_name        VARCHAR(50),   -- RAZORPAY, STRIPE, PAYU, MANUAL
    gateway_order_id    VARCHAR(100),
    gateway_payment_id  VARCHAR(100),
    gateway_signature   VARCHAR(255),
    gateway_response    JSONB,

    -- For claim settlement / refund
    bank_account_number VARCHAR(30),
    ifsc_code           VARCHAR(15),
    utr_number          VARCHAR(50),   -- Unique Transaction Reference

    -- Descriptions
    description         VARCHAR(500),
    failure_reason      VARCHAR(255),

    -- Premium details (when type = PREMIUM)
    premium_period_from DATE,
    premium_period_to   DATE,
    premium_due_date    DATE,
    is_late_payment     BOOLEAN DEFAULT FALSE,
    late_fee            NUMERIC(8,2) DEFAULT 0,

    -- Soft delete & audit
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by          UUID,
    processed_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_payment_policy     ON payment_transactions(policy_id);
CREATE INDEX idx_payment_customer   ON payment_transactions(customer_id);
CREATE INDEX idx_payment_status     ON payment_transactions(status);
CREATE INDEX idx_payment_gateway_id ON payment_transactions(gateway_payment_id);
CREATE INDEX idx_payment_number     ON payment_transactions(transaction_number);

-- Payment receipts / invoices
CREATE TABLE payment_receipts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES payment_transactions(id),
    receipt_number  VARCHAR(30) NOT NULL UNIQUE,
    receipt_pdf_key VARCHAR(500),    -- S3 key
    generated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Premium billing schedule
CREATE TABLE billing_schedule (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID NOT NULL,
    policy_number   VARCHAR(50) NOT NULL,
    customer_id     UUID NOT NULL,
    company_id      UUID NOT NULL,
    due_date        DATE NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(5) DEFAULT 'INR',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING, PAID, OVERDUE, WAIVED
    installment_no  INT NOT NULL,
    paid_at         TIMESTAMP WITH TIME ZONE,
    transaction_id  UUID REFERENCES payment_transactions(id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_billing_policy   ON billing_schedule(policy_id);
CREATE INDEX idx_billing_due      ON billing_schedule(due_date) WHERE status = 'PENDING';
CREATE INDEX idx_billing_overdue  ON billing_schedule(status) WHERE status = 'OVERDUE';

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ language 'plpgsql';

CREATE TRIGGER trg_payment_updated_at
    BEFORE UPDATE ON payment_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
