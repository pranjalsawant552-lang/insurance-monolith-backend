# Monolith Conversion Notes

This document explains every non-trivial decision made while converting the
12-microservice Insurance Platform backend into this single Spring Boot
application, and restates the gap audit performed on the original codebase
before conversion. Read this before deploying, and definitely before
assuming anything here compiles untouched — **this was converted at the
source level without Maven Central access in the conversion environment,
so it has not been compiled.** The original 12-service codebase carried the
same caveat (its own README says it was "never compiled or run"); that risk
is carried forward here, not introduced by this conversion. Budget time for
a first `mvn clean compile` pass to catch anything a source-level review
missed.

---

## 1. What changed, at a glance

| | Before | After |
|---|---|---|
| Deployable units | 12 services + Eureka + API Gateway = 14 | 1 |
| Databases | 12 separate Postgres databases | 1 database, 1 schema |
| Inter-service calls | HTTP via RestTemplate (6 call sites) | direct Java method calls |
| Cross-module async events | Kafka, 7 topics | in-process Spring `ApplicationEvent`s |
| Ports | 12 app ports + gateway (8080) + Eureka (8761) | 1 (8080) |
| Security | Gateway verifies JWT → sets trusted headers → 11 services trust the headers | one JWT-verifying filter, shared by every module |
| Swagger | 12 separate UIs behind 12 ports | 1, at `/api/v1/swagger-ui.html` |

---

## 2. Source merge

All 12 services' `src/main/java` merged into one tree with **zero package
collisions** — every service already used a distinct base package
(`com.insurance.agent`, `com.insurance.claims`, etc.), so this was a
mechanical copy, not a rename exercise. The 12 per-service
`@SpringBootApplication` classes were deleted and replaced by one
`com.insurance.monolith.InsuranceMonolithApplication`, component-scanning
`com.insurance` as a whole.

Two services — `product-service` and `risk-fraud-service` — were already
flagged in the original README as a "legacy" pre-pivot path being
superseded by the newer PDF-ingestion flow. They were merged in as-is
(same as everything else); no attempt was made to remove or consolidate
them further, since that's a product decision, not a mechanical one.

---

## 3. Inter-service calls → direct method calls

Six classes did HTTP calls between services via `RestTemplate`:

| Caller | Callee | File |
|---|---|---|
| claims | risk-fraud | `claims/client/RiskFraudServiceClient.java` |
| policy | risk-fraud | `policy/client/RiskFraudServiceClient.java` |
| policy | product | `policy/client/ProductServiceClient.java` |
| notification | customer | `notification/client/CustomerServiceClient.java` |
| risk-fraud | claims | `risk/client/ClaimsServiceClient.java` |
| risk-fraud | policy | `risk/client/PolicyServiceClient.java` |

All six were rewritten to call the target module's own `@Service` bean
directly. Each original class's **soft-fail behavior was preserved
deliberately** — e.g. "an unreachable risk engine routes to manual
underwriting review, never auto-approves" still catches `RuntimeException`
and falls back the same way, even though an in-process call can now only
fail on a genuine bug, not a network/availability problem. The four
`RestTemplateConfig.java` beans that configured those HTTP clients were
deleted as dead code.

---

## 4. Kafka → in-process Spring events

7 Kafka topics, 17 publisher call sites, 11 listener methods, all
converted to a single shared mechanism:
`com.insurance.monolith.events.DomainEvent` (a record wrapping
topic/key/payload) published via `com.insurance.monolith.events
.DomainEventBus`, which every former `KafkaTemplate`-based publisher now
calls instead. Every former `@KafkaListener(topics = "...")` method became
`@Async @EventListener(condition = "#domainEvent.topic() == '...'")`.

**This was a deliberate choice, not the only option** — you asked me to
recommend one, and I recommended in-process events over keeping Kafka
because a monolith has no other reason to run a message broker, and it
removes an entire piece of infrastructure to operate. The trade-off,
stated plainly: **this is now in-memory pub/sub, not a durable log.** If
the JVM crashes between a publish and a listener finishing, that event is
gone — no replay, no consumer-group offset to resume from, unlike Kafka.
For most of the 7 flows here (notifications, agent/analytics read models)
that's an acceptable trade for a simpler deployment. For the audit trail
specifically (`audit.entity-changed`) it's a more meaningful change: a
crash mid-request could now silently drop an audit log entry that Kafka
would have preserved. If audit durability across a crash matters for your
compliance requirements, replace `AuditEventBus`'s in-process publish with
`@TransactionalEventListener(phase = AFTER_COMMIT)` writing straight to
the `audit_log_entries` table in the *same transaction* as the business
write, rather than any pub/sub mechanism at all — that's strictly stronger
than either the old Kafka design or the current in-process one, and is
the recommended fast-follow if you act on only one suggestion in this doc.

---

## 5. Database: fully merged into one schema

Per your choice, all 12 databases became one (`insurance_db`), one schema
(`public`). Two real collisions surfaced and were fixed:

- **`insurance_products` / `product_riders`**: policy-service had its own
  local, never-populated copy of these tables (its own V2 migration
  comment confirmed "zero rows are written to them by any service code").
  These were dropped entirely from the merged migration set;
  `PolicyRider`'s JPA `@ManyToOne` to the local `ProductRider` entity was
  changed to a plain `riderId` UUID column pointing at product-service's
  *real* `ProductRider.id` — the same no-local-FK pattern already used for
  `customerId`/`agentId` on `Policy`. The two dead entity classes
  (`policy.entity.InsuranceProduct`, `policy.entity.ProductRider`) were
  deleted; verified nothing else in the codebase referenced them.
- **`idempotency_records`**: claims-service and payment-service each had
  their own identically-named, identically-shaped idempotency-cache table.
  Renamed to `claims_idempotency_records` / `payment_idempotency_records`
  (table name, `@Table` annotation, and the Java interface/class names
  themselves, since the interface names collided too — see §6).

All 22 Flyway migrations were renumbered into one sequence
(`V1`–`V22`, prefixed by original module for traceability) in dependency-
safe order. There are no cross-service DB foreign keys anywhere in the
original design (every cross-service reference is a bare UUID column, by
design, per multiple migration comments), so migration *order* between
unrelated modules doesn't matter — order was chosen to put auth first
(referenced conceptually by everyone) and otherwise follows the original
build order.

`analytics-service` was the one service with **no Flyway migrations at
all** and `ddl-auto: create-drop` — safe in its own isolated (empty of
entities) context, but would have been actively dangerous if inherited by
the whole merged schema (`create-drop` running against a schema with
everyone else's data). Verified analytics-service has zero `@Entity`
classes — it only reads events/other modules' data — so this was a
no-op fix: global `ddl-auto: validate` now applies everywhere, correctly.

---

## 6. Spring bean name collisions (the part that would have silently crashed on boot)

This is the least visible but most important mechanical risk in any
"merge N Spring Boot apps into one" conversion, so it gets its own section.
Each of the 12 services used to be its own `ApplicationContext` — nothing
stopped two services from having a class with the identical simple name,
because Spring never saw both at once. Merged into one context, Spring's
default bean naming (decapitalized simple class name, un-qualified by
package) means two `@Component`-annotated classes both named `X` in
different packages **fail application startup** with
`ConflictingBeanDefinitionException`.

A full scan (every `@Component`/`@Service`/`@Repository`/`@Configuration`/
`@RestController`/`@RestControllerAdvice` class, plus every Spring Data
repository interface, plus every MapStruct `@Mapper`) found and fixed:

- **`OpenApiConfig` × 12** — one per service, each declaring its own
  `@SecurityScheme(name = "bearerAuth")`. Kept exactly one (rewritten to
  describe the whole platform); deleted the other 11 — not just a name
  fix, they were genuinely redundant now that one Springdoc instance
  serves everyone.
- **`AuditEventPublisher` × 9** — one per module, each publishing to the
  shared `audit.entity-changed` topic. Each given an explicit
  `@Component("<module>AuditEventPublisher")` name; no code elsewhere had
  to change since consumers reference these by type (fully-qualified,
  same package), never by bean name.
- **`GlobalExceptionHandler` × 11** — same fix, explicit
  `@RestControllerAdvice("<module>GlobalExceptionHandler")` names.
- **`IdempotencyRecordRepository` × 2`, `IdempotencyService` × 2,
  `CacheConfig` × 2, `EncryptedStringConverter` × 2, `PolicyEventConsumer`
  × 2, `RiskFraudServiceClient` × 2, `AuthEventConsumer` × 2** — same
  explicit-name fix, except the two `IdempotencyRecordRepository`
  interfaces, which were renamed outright (`ClaimsIdempotencyRecordRepository`
  / `PaymentIdempotencyRecordRepository`) since Spring Data repository
  interfaces don't take an explicit bean name the same way component
  classes do.

None of these fixes changed behavior — every fix is either an explicit
bean name (zero effect on type-based `@RequiredArgsConstructor` injection,
which is used everywhere in this codebase) or, for the two repository
interfaces, a rename plus updating their two call sites. Verified with a
final scan: zero remaining collisions across stereotype classes, JPA
repositories, and MapStruct mappers.

**If you add new modules to this monolith later, re-run this check.** A
quick one: any two files anywhere under `src/main/java` with the same
filename that both carry a Spring stereotype annotation are a collision
waiting to happen.

---

## 7. Security: the fix that mattered most

Read this section even if you skip everything else.

The original architecture split authentication in two: **api-gateway**
verified the caller's JWT once at the edge, then set trusted
`X-User-Id` / `X-User-Role` / `X-User-Permissions` / `X-Company-Id` /
`X-Branch-Id` headers on the request it forwarded downstream. Each of the
**11 non-auth services** then had its own `HeaderAuthFilter` that
**blindly trusted those headers** and populated Spring Security's context
from them — correct only under the assumption that the gateway had
already stripped any client-supplied `X-User-*` headers, and that services
were never reachable except through the gateway.

There is no gateway in this monolith. If those 11 `HeaderAuthFilter`
classes had been carried over unchanged, **a caller hitting this app
directly could set `X-User-Role: SUPER_ADMIN` on a raw HTTP request and
every `@PreAuthorize` check in the codebase would have trusted it** — a
full authentication bypass, not a cosmetic gap.

Fixed by deleting all 11 `HeaderAuthFilter` classes and all 12 per-module
`SecurityConfig` classes, and replacing them with:

- **One shared JWT-verifying filter** (auth-service's own
  `JwtAuthFilter`/`JwtUtil`, which does real signature/expiry
  verification — not header-trusting), now applied globally in front of
  every module's controllers.
- **One unified `com.insurance.monolith.config.SecurityConfig`**, whose
  public-endpoint allowlist is the union of all 12 services' former
  allowlists (auth's login/register/etc. flow, payment's webhook
  receiver, product's public catalog/pricing-preview endpoints, plus the
  health/swagger paths every service repeated).
- **CORS, security headers (CSP/HSTS/frame-options/referrer-policy), and
  the `/admin/**` and `/actuator/**` role restrictions** — all carried
  over from auth-service's own `SecurityConfig`, since it was the only
  one of the 12 with real browser-facing concerns (the other 11 were only
  ever called server-to-server through the gateway before).
- The gateway's `SecurityHeadersFilter` and `RequestLoggingFilter` (both
  WebFlux-only `GlobalFilter`s, incompatible with this MVC monolith) were
  **ported to servlet `OncePerRequestFilter` equivalents** rather than
  dropped, since they were still meaningful.

One side note on that last filter: giving every request a shared
`X-Request-Id`/MDC `traceId` that now threads through every module's log
lines **partially closes** a gap the original README flagged ("no
correlation-ID tracing across services — debugging a 4-service request
means grepping 4 log streams by hand"). It doesn't fully close it — see
§9 — but a monolith gets this one nearly for free, since it's one log
stream to begin with now.

The old gateway's `RoleAllowlistFilter` (an extra edge-level check
specifically for audit-service and analytics-service, rejecting an
"obviously wrong role" before it reached the service's own
`@PreAuthorize`) was **not** ported — those two services' own controller-
level `@PreAuthorize` checks are the substantive protection and are
unchanged; the allowlist filter was described in its own original comment
as "a first line of defense... never instead of" that check, so its
absence is a defense-in-depth reduction, not a new hole. Worth adding back
as a simple `@PreAuthorize`-adjacent check on those two modules if you
want to fully restore that layer.

---

## 8. `pom.xml` / `application.yml` / config consolidation

- **`pom.xml`**: union of all 12 services' dependencies, deduplicated.
  Removed: `spring-cloud-starter-netflix-eureka-client`,
  `spring-cloud-starter-loadbalancer`, the `spring-cloud-dependencies` BOM,
  and `spring-kafka` — nothing in a single-JVM app needs any of them
  anymore.
- **`application.yml`**: single file, single port (8080), single
  datasource. Every setting is traceable to exactly one of the 12
  original files (see the file's own inline comments for the mapping).
  The one real conflict — auth's cache wanted a 15-minute TTL, product's
  wanted 1 hour, and Spring's `spring.cache.redis.time-to-live` can only
  express one global value — was resolved with an explicit
  `RedisCacheManagerBuilderCustomizer` bean (`CacheConfig`) setting
  per-cache-name TTLs instead of relying on the single YAML property.
- **Connection pool size**: set to 20 (the highest any single original
  service requested) as a starting point, explicitly called out in
  `application.yml`'s comment as a floor chosen without load testing, not
  a properly-sized number for combined traffic — verify under real load
  before trusting it in production.

---

## 9. Gap audit (performed on the original 12-service codebase, before conversion)

This was assessed against the original code and its own README/changelogs
before any conversion work began. None of it was created or fixed by the
monolith conversion — it's carried over unchanged, since converting the
architecture doesn't fix product/feature gaps.

- **No real-time push mechanism anywhere** — no WebSocket, no SSE, zero
  matches in the whole codebase. "Real-time" meant Kafka async events
  consumed seconds later, not live UI updates; that's now in-process
  events (§4), same latency characteristics, still not a push mechanism
  to a browser. Concretely: document-review-queue alerts still require
  polling `GET /extractions?status=PENDING_REVIEW`; the underlying
  blocker per the original README is that auth-service has no "list users
  by role" endpoint yet, not the notification wiring itself.
- No bulk document upload.
- Analytics/reporting endpoints aren't branch-scoped — a Branch Manager
  sees company-wide numbers.
- No document versioning or malware/virus scanning on uploads.
- No e-signature or generated policy PDF for the legacy self-issuance path.
- Swagger UIs weren't aggregated before this conversion (**this one is now
  fixed as a side effect of merging** — one UI at `/api/v1/swagger-ui.html`
  covering every module).
- **No test suite, no CI/CD** — flagged in the original README as the
  single largest remaining gap. Still true here; nothing in this
  conversion added tests.
- No structured logging/alerting, no documented backup/DR runbook.
- No customer-facing portal (staff/admin-only platform).
- Two services (`product-service`, `risk-fraud-service`) are legacy,
  partially superseded by the newer PDF-ingestion flow — see §2.
- **Never compiled or run** — true of the original codebase per its own
  README, and true of this converted version too (see the top of this
  document).

---

## 10. What to do before deploying this

1. `mvn clean compile` and fix whatever a source-level conversion missed —
   there was no Maven Central access available while doing this
   conversion, so nothing here has actually been built.
2. Load-test before trusting the connection-pool-size default (§8).
3. Decide whether the audit trail needs the transactional-outbox upgrade
   described in §4.
4. Consider restoring the audit/analytics role-allowlist defense-in-depth
   layer described in §7.
5. Everything in §9 is unchanged product/feature work, independent of this
   conversion — prioritize it the same way you would have before.
