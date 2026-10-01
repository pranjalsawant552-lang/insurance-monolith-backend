-- MONOLITH MERGE: originally auth-service/V2__rbac_roles_permissions.sql
-- ============================================================
-- Auth Service - Dynamic RBAC: roles, permissions, role_permissions
-- Lets an admin change what a role can do without a code deploy.
-- One Role row is seeded per existing UserRole enum value (system
-- roles — code/name fixed, cannot be deleted); permissions are
-- granted/revoked per role via role_permissions.
-- ============================================================

CREATE TABLE roles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(50)  NOT NULL UNIQUE,
    name              VARCHAR(100) NOT NULL,
    description       VARCHAR(255),
    is_system_role    BOOLEAN NOT NULL DEFAULT TRUE,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by        UUID
);

CREATE TABLE permissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(100) NOT NULL UNIQUE,
    module              VARCHAR(50)  NOT NULL,
    description         VARCHAR(255) NOT NULL,
    is_system_permission BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_permissions_module ON permissions(module);

CREATE TABLE role_permissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    granted_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    granted_by      UUID,
    UNIQUE (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);

CREATE TRIGGER update_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ------------------------------------------------------------
-- Seed: one system role per UserRole enum value
-- ------------------------------------------------------------
INSERT INTO roles (code, name, description, is_system_role) VALUES
    ('SUPER_ADMIN',      'Super Admin',        'Full unrestricted access across all branches and modules', TRUE),
    ('ADMIN',            'Admin',              'Company-wide administrative access', TRUE),
    ('BRANCH_MANAGER',   'Branch Manager',     'Branch-scoped administrative access', TRUE),
    ('UNDERWRITER',      'Underwriter',        'Reviews and approves policy applications and KYC', TRUE),
    ('CLAIMS_OFFICER',   'Claims Officer',     'Manages the claims workflow end to end', TRUE),
    ('FINANCE_ACCOUNTS', 'Finance / Accounts', 'Manages payments, disbursements and commission payouts', TRUE),
    ('AGENT',            'Agent',              'Sells and services policies for their own customer book', TRUE),
    ('BROKER',           'Broker',             'Sells and services policies across multiple insurers/products', TRUE),
    ('CUSTOMER',         'Customer',           'Self-service access to their own policies, claims and payments', TRUE),
    ('AUDITOR',          'Auditor',            'Read-only access across all modules plus the audit trail', TRUE);

-- ------------------------------------------------------------
-- Seed: permission catalog
-- ------------------------------------------------------------
INSERT INTO permissions (code, module, description) VALUES
    ('CUSTOMER_READ',          'CUSTOMER',  'View customer profiles'),
    ('CUSTOMER_WRITE',         'CUSTOMER',  'Create/update customer profiles'),
    ('CUSTOMER_DELETE',        'CUSTOMER',  'Soft-delete a customer'),
    ('CUSTOMER_KYC_APPROVE',   'CUSTOMER',  'Approve or reject KYC submissions'),

    ('PRODUCT_READ',           'PRODUCT',   'View insurance products and riders'),
    ('PRODUCT_WRITE',          'PRODUCT',   'Create/update/activate/deactivate products'),

    ('POLICY_READ',            'POLICY',    'View policies'),
    ('POLICY_WRITE',           'POLICY',    'Create/update policies'),
    ('POLICY_ISSUE',           'POLICY',    'Issue a policy (move to ACTIVE)'),
    ('POLICY_CANCEL',          'POLICY',    'Cancel a policy'),
    ('POLICY_UNDERWRITE',      'POLICY',    'Perform underwriting review/decision on a policy application'),

    ('CLAIM_READ',             'CLAIM',     'View claims'),
    ('CLAIM_WRITE',            'CLAIM',     'Submit/update a claim'),
    ('CLAIM_ASSIGN',           'CLAIM',     'Assign a claim to an officer or surveyor'),
    ('CLAIM_APPROVE',          'CLAIM',     'Approve or reject a claim'),
    ('CLAIM_SETTLE',           'CLAIM',     'Settle/disburse a claim'),
    ('CLAIM_FLAG_FRAUD',       'CLAIM',     'Flag or score a claim for fraud review'),

    ('PAYMENT_READ',           'PAYMENT',   'View payment transactions'),
    ('PAYMENT_WRITE',          'PAYMENT',   'Create/record payment transactions'),
    ('PAYMENT_DISBURSE',       'PAYMENT',   'Confirm/execute a disbursement (claim settlement, refund)'),
    ('PAYMENT_REFUND',         'PAYMENT',   'Issue a refund'),

    ('AGENT_READ',             'AGENT',     'View agent/broker profiles and commissions'),
    ('AGENT_WRITE',            'AGENT',     'Onboard/update agent/broker profiles'),
    ('AGENT_COMMISSION_APPROVE','AGENT',    'Approve or mark commission payouts as paid'),

    ('DOCUMENT_READ',          'DOCUMENT',  'View/download documents'),
    ('DOCUMENT_WRITE',         'DOCUMENT',  'Upload documents'),
    ('DOCUMENT_VERIFY',        'DOCUMENT',  'Verify or reject an uploaded document'),
    ('DOCUMENT_DELETE',        'DOCUMENT',  'Soft-delete a document'),

    ('ANALYTICS_READ',         'ANALYTICS', 'View dashboards and analytics'),
    ('REPORT_EXPORT',          'ANALYTICS', 'Export/download reports'),

    ('ADMIN_USER_MANAGE',      'ADMIN',     'Create/lock/unlock user accounts'),
    ('ADMIN_ROLE_MANAGE',      'ADMIN',     'Edit roles and role-permission grants'),
    ('AUDIT_LOG_READ',         'ADMIN',     'Read the audit trail');

-- ------------------------------------------------------------
-- Seed: role -> permission grants
-- ------------------------------------------------------------

-- SUPER_ADMIN: everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

-- ADMIN: everything except ADMIN_ROLE_MANAGE (RBAC editing stays Super-Admin-only)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND p.code <> 'ADMIN_ROLE_MANAGE';

-- BRANCH_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'BRANCH_MANAGER' AND p.code IN (
    'CUSTOMER_READ','CUSTOMER_WRITE','CUSTOMER_KYC_APPROVE',
    'POLICY_READ','POLICY_WRITE','POLICY_ISSUE','POLICY_CANCEL',
    'PRODUCT_READ',
    'CLAIM_READ','CLAIM_WRITE','CLAIM_ASSIGN',
    'PAYMENT_READ',
    'AGENT_READ','AGENT_WRITE',
    'DOCUMENT_READ','DOCUMENT_WRITE','DOCUMENT_VERIFY',
    'ANALYTICS_READ','REPORT_EXPORT'
);

-- UNDERWRITER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'UNDERWRITER' AND p.code IN (
    'CUSTOMER_READ','CUSTOMER_KYC_APPROVE',
    'POLICY_READ','POLICY_WRITE','POLICY_ISSUE','POLICY_UNDERWRITE',
    'PRODUCT_READ',
    'DOCUMENT_READ','DOCUMENT_VERIFY',
    'ANALYTICS_READ'
);

-- CLAIMS_OFFICER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'CLAIMS_OFFICER' AND p.code IN (
    'CUSTOMER_READ',
    'POLICY_READ',
    'CLAIM_READ','CLAIM_WRITE','CLAIM_ASSIGN','CLAIM_APPROVE','CLAIM_SETTLE','CLAIM_FLAG_FRAUD',
    'DOCUMENT_READ','DOCUMENT_WRITE','DOCUMENT_VERIFY',
    'PAYMENT_READ'
);

-- FINANCE_ACCOUNTS
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'FINANCE_ACCOUNTS' AND p.code IN (
    'PAYMENT_READ','PAYMENT_WRITE','PAYMENT_DISBURSE','PAYMENT_REFUND',
    'AGENT_READ','AGENT_COMMISSION_APPROVE',
    'ANALYTICS_READ','REPORT_EXPORT',
    'CUSTOMER_READ','POLICY_READ'
);

-- AGENT
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'AGENT' AND p.code IN (
    'CUSTOMER_READ','CUSTOMER_WRITE',
    'POLICY_READ','POLICY_WRITE',
    'PRODUCT_READ',
    'CLAIM_READ',
    'PAYMENT_READ',
    'AGENT_READ',
    'DOCUMENT_READ','DOCUMENT_WRITE'
);

-- BROKER (same shape as AGENT)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'BROKER' AND p.code IN (
    'CUSTOMER_READ','CUSTOMER_WRITE',
    'POLICY_READ','POLICY_WRITE',
    'PRODUCT_READ',
    'CLAIM_READ',
    'PAYMENT_READ',
    'AGENT_READ',
    'DOCUMENT_READ','DOCUMENT_WRITE'
);

-- CUSTOMER (self-service; service-layer still restricts to the caller's own records)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'CUSTOMER' AND p.code IN (
    'CUSTOMER_READ',
    'POLICY_READ',
    'PRODUCT_READ',
    'CLAIM_READ','CLAIM_WRITE',
    'PAYMENT_READ','PAYMENT_WRITE',
    'DOCUMENT_READ','DOCUMENT_WRITE'
);

-- AUDITOR: read-only everywhere, plus the audit log itself
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'AUDITOR' AND p.code IN (
    'CUSTOMER_READ','POLICY_READ','PRODUCT_READ','CLAIM_READ','PAYMENT_READ',
    'AGENT_READ','DOCUMENT_READ','ANALYTICS_READ','REPORT_EXPORT','AUDIT_LOG_READ'
);
