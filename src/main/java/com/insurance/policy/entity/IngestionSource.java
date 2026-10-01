package com.insurance.policy.entity;

/**
 * How this Policy record came to exist in the platform. Added in the
 * PDF-ingestion pivot (Phase 9) — the platform no longer prices or issues
 * policies itself, so "how did this row get here" is now a meaningful,
 * queryable distinction rather than always "we issued it".
 */
public enum IngestionSource {
    /** Created automatically from a document-service ExtractionResult that a human
     *  reviewer verified — the normal, expected path going forward. */
    PDF_INGESTION,

    /** An ops/agent typed the policy details in directly — used when the PDF is
     *  unavailable, unreadable, or automatic ingestion failed and was fixed by hand
     *  via POST /policies/ingestion-failures/{id}/resolve. */
    MANUAL,

    /** Legacy pre-pivot record: this platform calculated the premium and issued the
     *  policy itself. Kept only so historical policies remain queryable — no new
     *  rows should be created this way. */
    SELF_ISSUED
}
