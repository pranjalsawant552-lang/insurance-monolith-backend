-- MONOLITH MERGE: originally document-service/V1__initial_schema.sql
-- ============================================================
-- Document Service Schema
-- ============================================================

CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_number VARCHAR(30) NOT NULL UNIQUE,

    -- Owner reference (cross-service)
    owner_type      VARCHAR(30) NOT NULL,   -- POLICY, CLAIM, CUSTOMER, KYC
    owner_id        UUID NOT NULL,
    company_id      UUID NOT NULL,

    -- File metadata
    document_type   VARCHAR(100) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,  -- S3/MinIO object key
    storage_bucket  VARCHAR(100) NOT NULL DEFAULT 'insurance-documents',
    file_size_bytes BIGINT NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    checksum_md5    VARCHAR(64),

    -- OCR & verification
    ocr_status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING, PROCESSING, COMPLETED, FAILED, SKIPPED
    ocr_text        TEXT,
    ocr_data        JSONB,
    ocr_confidence  NUMERIC(5,2),

    is_verified     BOOLEAN NOT NULL DEFAULT FALSE,
    verified_by     UUID,
    verified_at     TIMESTAMP WITH TIME ZONE,
    rejection_note  TEXT,

    -- Access control
    is_public       BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_by     UUID NOT NULL,

    -- Soft delete
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    deleted_by      UUID,

    -- Audit
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_owner  ON documents(owner_id, owner_type) WHERE is_deleted = FALSE;
CREATE INDEX idx_documents_company ON documents(company_id);
CREATE INDEX idx_documents_ocr    ON documents(ocr_status) WHERE ocr_status IN ('PENDING','PROCESSING');
CREATE INDEX idx_documents_number ON documents(document_number);

-- Pre-signed URL cache (avoid regenerating every request)
CREATE TABLE document_access_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    token           VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by      UUID
);

CREATE INDEX idx_access_token
ON document_access_tokens(token);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ language 'plpgsql';

CREATE TRIGGER trg_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
