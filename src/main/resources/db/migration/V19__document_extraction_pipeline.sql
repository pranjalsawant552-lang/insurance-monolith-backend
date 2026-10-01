-- MONOLITH MERGE: originally document-service/V2__extraction_pipeline.sql
-- ============================================================
-- Document Service - Phase 8: real PDF/OCR extraction pipeline
--
-- Context: the previous OcrService never opened the uploaded file — it
-- returned hardcoded placeholder text and a fixed confidence score
-- regardless of content. Found while starting this phase. This migration
-- adds the tables the real pipeline (PdfTextExtractionService,
-- OcrEngineService, FieldExtractionService) actually needs.
-- ============================================================

ALTER TABLE documents ADD COLUMN issuer_code VARCHAR(50);

CREATE TABLE extraction_templates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type       VARCHAR(50) NOT NULL,
    issuer_code         VARCHAR(50) NOT NULL,
    issuer_name         VARCHAR(150),
    field_rules         JSONB NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (document_type, issuer_code)
);

CREATE TABLE extraction_results (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         UUID NOT NULL REFERENCES documents(id),
    owner_type          VARCHAR(50) NOT NULL,
    owner_id            UUID NOT NULL,
    company_id          UUID NOT NULL,
    document_type       VARCHAR(50) NOT NULL,
    template_id         UUID REFERENCES extraction_templates(id),
    issuer_code         VARCHAR(50),
    extraction_method   VARCHAR(20) NOT NULL,
    extracted_fields    JSONB,
    field_confidence    JSONB,
    overall_confidence  INT,
    verified_fields     JSONB,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    raw_text_snapshot   TEXT,
    reviewed_by         UUID,
    reviewed_at         TIMESTAMP WITH TIME ZONE,
    review_notes        VARCHAR(500),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_extraction_status ON extraction_results(status);
CREATE INDEX idx_extraction_document ON extraction_results(document_id);

-- ------------------------------------------------------------
-- Seed: a small set of starter templates so the pipeline works out of the
-- box for the most common document types even before insurer-specific
-- templates are built. GOVT_IN templates for KYC documents are reasonably
-- universal (Aadhaar/PAN formats are standardized nationally). The
-- POLICY_SCHEDULE GENERIC template is intentionally loose — a best-effort
-- fallback until real templates are built from actual sample PDFs of the
-- 2-3 insurers the client uses most (see the Phase 8 changelog).
-- ------------------------------------------------------------

INSERT INTO extraction_templates (document_type, issuer_code, issuer_name, field_rules, active) VALUES
('AADHAAR', 'GOVT_IN', 'Government of India', '[
    {"fieldName":"aadhaarNumber","regexPattern":"([2-9]{1}[0-9]{3}\\\\s?[0-9]{4}\\\\s?[0-9]{4})","required":true,"description":"12-digit Aadhaar number, space-separated in groups of 4"},
    {"fieldName":"name","regexPattern":"(?:Name|नाम)[:\\\\s]+([A-Za-z\\\\s]{3,60})","required":true,"description":"Cardholder full name"},
    {"fieldName":"dateOfBirth","regexPattern":"(?:DOB|Date of Birth)[:\\\\s]+(\\\\d{2}[/-]\\\\d{2}[/-]\\\\d{4})","required":false,"description":"Date of birth"},
    {"fieldName":"gender","regexPattern":"(Male|Female|Transgender)","required":false,"description":"Gender as printed on the card"}
]'::jsonb, true),

('PAN', 'GOVT_IN', 'Income Tax Department', '[
    {"fieldName":"panNumber","regexPattern":"([A-Z]{5}[0-9]{4}[A-Z]{1})","required":true,"description":"10-character PAN"},
    {"fieldName":"name","regexPattern":"(?:Name)[:\\\\s]+([A-Za-z\\\\s]{3,60})","required":true,"description":"Cardholder full name"},
    {"fieldName":"fatherName","regexPattern":"(?:Father''s Name)[:\\\\s]+([A-Za-z\\\\s]{3,60})","required":false,"description":"Father''s name as printed"},
    {"fieldName":"dateOfBirth","regexPattern":"(?:Date of Birth)[:\\\\s]+(\\\\d{2}[/-]\\\\d{2}[/-]\\\\d{4})","required":false,"description":"Date of birth"}
]'::jsonb, true),

('POLICY_SCHEDULE', 'GENERIC', 'Generic (any insurer — best-effort fallback)', '[
    {"fieldName":"policyNumber","regexPattern":"(?:Policy No\\\\.?|Policy Number)[:\\\\s]+([A-Za-z0-9/-]{5,30})","required":true,"description":"Policy number"},
    {"fieldName":"sumAssured","regexPattern":"(?:Sum Assured|Sum Insured)[:\\\\s]+(?:Rs\\\\.?|INR)?\\\\s?([0-9,]{4,15})","required":true,"description":"Sum assured/insured amount"},
    {"fieldName":"premiumAmount","regexPattern":"(?:Premium(?: Amount)?)[:\\\\s]+(?:Rs\\\\.?|INR)?\\\\s?([0-9,]{2,12})","required":true,"description":"Premium amount"},
    {"fieldName":"commencementDate","regexPattern":"(?:Policy Start Date|Commencement Date|Date of Commencement)[:\\\\s]+(\\\\d{2}[/-][A-Za-z0-9]{2,3}[/-]\\\\d{4}|\\\\d{2}[/-]\\\\d{2}[/-]\\\\d{4})","required":true,"description":"Policy commencement/start date"},
    {"fieldName":"expiryDate","regexPattern":"(?:Policy End Date|Expiry Date|Date of Expiry)[:\\\\s]+(\\\\d{2}[/-][A-Za-z0-9]{2,3}[/-]\\\\d{4}|\\\\d{2}[/-]\\\\d{2}[/-]\\\\d{4})","required":true,"description":"Policy expiry date"},
    {"fieldName":"nomineeName","regexPattern":"(?:Nominee(?: Name)?)[:\\\\s]+([A-Za-z\\\\s]{3,60})","required":false,"description":"Nominee full name"}
]'::jsonb, true);
