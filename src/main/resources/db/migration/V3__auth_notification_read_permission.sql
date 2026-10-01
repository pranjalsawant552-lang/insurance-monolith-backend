-- MONOLITH MERGE: originally auth-service/V3__notification_read_permission.sql
-- ============================================================
-- Auth Service - Phase 7: NOTIFICATION_READ permission
--
-- notification-service's new GET /notifications endpoint needs a permission
-- to gate on. New V3 file, not an edit to V2 (Phase 1's RBAC seed) — V2 was
-- already delivered in an earlier zip; editing an already-shipped migration
-- breaks Flyway's checksum validation for anyone who applied it (see the
-- Phase 4 changelog for the first time this rule was established).
--
-- NOTE: SUPER_ADMIN and ADMIN's "grant everything" seed logic in V2 ran as a
-- one-time INSERT ... CROSS JOIN at that migration's execution time — it does
-- NOT automatically pick up permissions added later. Every new permission
-- from here on must be explicitly granted to the roles that should have it,
-- same as this migration does below.
-- ============================================================

INSERT INTO permissions (code, module, description) VALUES
    ('NOTIFICATION_READ', 'NOTIFICATION', 'View sent-notification history and delivery status');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'AUDITOR') AND p.code = 'NOTIFICATION_READ';
