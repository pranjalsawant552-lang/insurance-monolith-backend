-- MONOLITH MERGE: originally payment-service/V2__idempotency_and_refunds.sql
-- ============================================================
-- Payment Service - Phase 6: idempotency keys + refunds
--
-- Idempotency: the CRITICAL gap flagged in the original audit (§4.8, §9) —
-- none of create-order/manual/claim-settlement/confirm-disbursement/refund
-- could previously survive a client retry safely.
-- ============================================================

CREATE TABLE payment_idempotency_records (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key   VARCHAR(200) NOT NULL,
    endpoint          VARCHAR(100) NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    response_status   INT NOT NULL,
    response_body     TEXT NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (idempotency_key, endpoint)
);

CREATE INDEX idx_payment_idempotency_expires_at ON payment_idempotency_records(expires_at);

-- ------------------------------------------------------------
-- Refunds: link a REFUND-type transaction back to what it refunds.
-- ------------------------------------------------------------
ALTER TABLE payment_transactions
    ADD COLUMN original_transaction_id UUID REFERENCES payment_transactions(id),
    ADD COLUMN refund_reason VARCHAR(500);

CREATE INDEX idx_payment_original_txn ON payment_transactions(original_transaction_id);
