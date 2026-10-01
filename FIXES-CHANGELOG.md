# P0 Fix Pass — Changelog

Applied against the findings in `INSURANCE-PLATFORM-AUDIT-REPORT.md`. 32
files touched, 2 new migrations (V23, V24). Nothing was deleted — legacy
flows were isolated behind flags, not removed, per the audit's explicit
instruction not to delete anything automatically.

## 1. Full tenant-impersonation fix (worse than originally reported)

Tracing the fix for §7.1 surfaced something more severe than the audit
described: 11 of 13 controllers extracted `userId`/`companyId`/`branchId`
from raw, client-settable HTTP headers (`X-User-Id`, `X-Company-Id`,
`X-Branch-Id`) — a leftover from the pre-monolith gateway architecture,
where the gateway set these headers *after* verifying the JWT. With no
gateway, any caller could set `X-Company-Id: <any-uuid>` directly and
every one of these controllers trusted it — full tenant impersonation, not
just a missing ownership check.

**Fixed:** all 11 controllers (Customer, Policy, PolicyIngestion,
Document, Extraction, ExtractionTemplate, Agent, Payment, Claims, Product,
Analytics) now read this context from JWT-verified request attributes
(set only by the real `JwtAuthFilter`), the same pattern `AuthController`
already used correctly. Verified zero remaining instances.

## 2. Record-level tenant scoping (§7.1, as originally reported)

Added real `companyId` scoping to every `getById`/update/delete/workflow
method in `CustomerService`, `PolicyService`, `DocumentService` (including
the presigned-download-URL path), and `ClaimService` — 25+ methods total.
Mismatches return 404, not 403, so ID-probing can't distinguish "doesn't
exist" from "exists, not yours." Two legitimate internal call paths
(notification resolving a customer's contact from an event; risk-fraud
looking up a policy mid-evaluation) got a separate, clearly-labeled
`getByIdInternal()` that intentionally skips the check, since those are
system-triggered, not reachable from an external HTTP request.

## 3. Renewal-reminder duplicate-send bug (§4.7)

Replaced the flat 30-day-window scheduler (which re-sent a reminder every
day for up to 30 days per policy) with staged, deduplicated milestones
(60/45/30/15/7/1 days out by default, configurable via
`policy.renewal-reminder.milestone-days`). New `PolicyRenewalReminder`
table with a `(policy_id, milestone_days)` unique constraint is the actual
dedup guarantee — protects against double-sends even under concurrent
scheduler runs, not just a same-day check.

## 4. Optimistic locking (§6 / §38)

Added `@Version` to the seven entities the audit named as highest-risk for
lost-update races: `Customer`, `Policy`, `Claim`, `ExtractionResult`,
`Document`, `AgentCommission`, `PaymentTransaction`. New global
`OptimisticLockExceptionHandler` returns a clean 409 with a "someone else
changed this, please refresh" message instead of an unhandled 500.

## 5. Legacy self-issuance/underwriting flow (§8) — isolated, not removed

`PolicyController`'s four legacy endpoints (`POST /policies`,
`POST /{id}/issue`, `GET /underwriting-queue`, `POST /{id}/underwriting-
decision`) are now gated behind `policy.legacy-issuance.enabled`,
defaulting to **false**. With the default, all four return 403 with an
explanation. Code and data model untouched — flip the flag if you decide
to KEEP this flow; delete the four endpoints and their `PolicyService`
methods if you decide to REMOVE it. See `PolicyController`'s class-level
javadoc.

## 6. Payment gateway fake-success paths (§4.10) — this was worse than reported too

Three separate fail-open bypasses found and fixed, only the first of which
the audit had caught:

- `simulateOrCallRazorpay`: used to return a **fabricated** order ID when
  unconfigured, which flowed into the API response as if real. Now throws
  a clear 503 instead.
- `verifyRazorpaySignature`: used to `return true` for **any** signature
  (or none) whenever the gateway secret wasn't configured — a forgeable
  payment-confirmation hole, not just a cosmetic fake-success issue. Any
  caller could have marked any pending transaction SUCCESS and billing
  paid. Now fails closed (`return false`).
- `verifyWebhookSignature`: same bug, on the **public, unauthenticated**
  webhook endpoint — meant anyone on the internet could have POSTed a
  fabricated webhook payload and had it accepted as genuine when the
  webhook secret wasn't configured. Now fails closed.

The plain manual-payment-recording path (amount/date/mode/reference,
independent of Razorpay) was already fine and is untouched.

## Still open

- No tests added — the audit's largest single production blocker remains.
- Compilation still unverified (no Maven Central access in this
  environment) — same caveat as the original codebase and the earlier
  monolith conversion.
- P1/P2/P3 feature gaps from the audit (leads/CRM, task management,
  service requests, commission reconciliation, etc.) are unchanged — this
  pass was P0-only.
