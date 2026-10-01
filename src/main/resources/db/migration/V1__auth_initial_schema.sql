-- MONOLITH MERGE: originally auth-service/V1__initial_schema.sql
-- ============================================================
-- Auth Service - Initial Schema
-- ============================================================

-- Users table
CREATE TABLE users (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   VARCHAR(255) NOT NULL UNIQUE,
    mobile_number           VARCHAR(20)  NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    role                    VARCHAR(50)  NOT NULL DEFAULT 'CUSTOMER',
    branch_id               UUID,
    company_id              UUID,

    -- Verification
    email_verified          BOOLEAN NOT NULL DEFAULT FALSE,
    mobile_verified         BOOLEAN NOT NULL DEFAULT FALSE,
    is_active               BOOLEAN NOT NULL DEFAULT FALSE,

    -- Account lockout
    failed_login_attempts   INT NOT NULL DEFAULT 0,
    locked_at               TIMESTAMP WITH TIME ZONE,
    locked_until            TIMESTAMP WITH TIME ZONE,
    lock_reason             VARCHAR(255),

    -- MFA
    totp_secret             VARCHAR(255),
    totp_enabled            BOOLEAN NOT NULL DEFAULT FALSE,

    -- OAuth
    oauth_provider          VARCHAR(50),
    oauth_provider_id       VARCHAR(255),

    -- Password history (last 5, stored as JSON array of hashes)
    password_history        TEXT DEFAULT '[]',

    -- Soft delete
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP WITH TIME ZONE,
    deleted_by              UUID,

    -- Audit
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by              UUID,
    last_login_at           TIMESTAMP WITH TIME ZONE,
    last_login_ip           VARCHAR(45),
    password_changed_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email) WHERE is_deleted = FALSE;
CREATE INDEX idx_users_mobile ON users(mobile_number) WHERE is_deleted = FALSE;
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_company ON users(company_id);

-- OTP records
CREATE TABLE otp_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    identifier      VARCHAR(255) NOT NULL,   -- email or mobile number
    otp_code        VARCHAR(10) NOT NULL,
    otp_type        VARCHAR(50) NOT NULL,    -- EMAIL_VERIFY, MOBILE_VERIFY, LOGIN_OTP, UNLOCK, FORGOT_PASSWORD, DEVICE_TRUST
    is_used         BOOLEAN NOT NULL DEFAULT FALSE,
    attempts        INT NOT NULL DEFAULT 0,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    used_at         TIMESTAMP WITH TIME ZONE,
    ip_address      VARCHAR(45)
);

CREATE INDEX idx_otp_identifier_type ON otp_records(identifier, otp_type) WHERE is_used = FALSE;
CREATE INDEX idx_otp_expires ON otp_records(expires_at);

-- Refresh tokens
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    device_id       UUID,
    is_revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMP WITH TIME ZONE,
    revoke_reason   VARCHAR(100),
    ip_address      VARCHAR(45),
    user_agent      TEXT
);

CREATE INDEX idx_refresh_token_hash ON refresh_tokens(token_hash) WHERE is_revoked = FALSE;
CREATE INDEX idx_refresh_token_user ON refresh_tokens(user_id);

-- Trusted devices
CREATE TABLE trusted_devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_fingerprint VARCHAR(255) NOT NULL,
    device_name     VARCHAR(255),
    device_type     VARCHAR(50),
    browser         VARCHAR(100),
    os              VARCHAR(100),
    ip_address      VARCHAR(45),
    is_trusted      BOOLEAN NOT NULL DEFAULT FALSE,
    trusted_at      TIMESTAMP WITH TIME ZONE,
    last_used_at    TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_device_user_fp ON trusted_devices(user_id, device_fingerprint);

-- Password reset tokens
CREATE TABLE password_reset_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    is_used         BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    used_at         TIMESTAMP WITH TIME ZONE,
    ip_address      VARCHAR(45)
);

CREATE INDEX idx_pwd_reset_token ON password_reset_tokens(token_hash) WHERE is_used = FALSE;

-- Audit log for auth events
CREATE TABLE auth_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID,
    event_type      VARCHAR(100) NOT NULL,  -- LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, REGISTER, etc.
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    device_id       UUID,
    details         JSONB DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user_id ON auth_audit_log(user_id);
CREATE INDEX idx_audit_event_type ON auth_audit_log(event_type);
CREATE INDEX idx_audit_created_at ON auth_audit_log(created_at DESC);

-- API Keys (for inter-service and third-party)
CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    key_hash        VARCHAR(255) NOT NULL UNIQUE,
    key_prefix      VARCHAR(20) NOT NULL,   -- shown to user for identification
    owner_id        UUID REFERENCES users(id),
    service_name    VARCHAR(100),
    scopes          TEXT[] DEFAULT '{}',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at      TIMESTAMP WITH TIME ZONE,
    last_used_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by      UUID,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_api_key_hash ON api_keys(key_hash) WHERE is_active = TRUE AND is_deleted = FALSE;

-- Updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
