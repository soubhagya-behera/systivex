# Systivex Project Status

Last verified: **2026-10-06** (Asia/Kolkata). This document must be updated
whenever the implemented state changes. Planned items are not claimed as done.

## Current phase

**Phase 3B-1 — Prometheus + Grafana metrics visualization.** Phases
0–3A below are kept as history.

## What is complete (Phase 3B-1, 2026-10-06)

- [x] Prometheus (`prom/prometheus:v3.5.0`) in the existing
      `observability/docker-compose.yml`: scrapes only the OTel Collector's
      `:8889` exposition (single metrics path — never the Java services
      directly, no duplicate collection). Scrape config in the tracked
      `observability/prometheus.yml`, including a `server_port` →
      `service` relabeling (the collector stamps `service_name="systivex"`
      on everything, so per-service identity comes from the receiver-kept
      port label).
- [x] Grafana (`grafana/grafana:12.1.0`) in the same Compose file, UI on
      :3000: Prometheus datasource + one `Systivex Metrics` dashboard fully
      provisioned from `observability/grafana/` (fresh `up -d` reproduces
      both, no browser clicks, no secrets, no database files in Git).
- [x] Dashboard (9 panels, low-cardinality labels only): services reporting,
      HTTP request rate, requests by outcome, average latency (sum/count —
      OTel histograms arrive without buckets on this path, so no p95),
      JVM heap, process CPU, checkout success/failure, inventory
      reservation success/rejected, payment authorization
      success/declined.
- [x] Live verified 2026-10-06: collector + Prometheus + Grafana healthy;
      Prometheus target `otel-collector:8889` up; gateway/order/payment/
      inventory series present (control plane verified directly on a
      temporary :8090 — :8080 is held by another project's
      PolicyImpactEngineApplication on this machine); Grafana datasource
      health OK and dashboard loads with data; success checkout moved
      `checkout_success_total` 0→1, rejection moved `checkout_failure`
      0→1, payment decline moved `payment_authorization_declined_total`
      0→1 and `checkout_failure` 1→2. No Java changes; 88 tests still green.
- [ ] STILL DEFERRED: Loki, Tempo, unified logs/traces UI, System Twin
      telemetry ingestion, any agent use of telemetry.

## What is complete (Phase 3A, 2026-09-30)

- [x] Boot-managed instrumentation in all five deployables (control plane +
      gateway/order/payment/inventory): `spring-boot-starter-opentelemetry`
      + `micrometer-registry-prometheus`, no hand-pinned versions, no second
      instrumentation library.
- [x] Distributed tracing verified end to end: one `POST /api/v1/checkout`
      produces a single trace ID across gateway → order → inventory +
      payment in the collector log (server + client HTTP spans per hop).
      Trace context crosses every RestClient hop via observation-registry
      builders; no hand-made trace IDs anywhere.
- [x] Three custom business spans only (`checkout`, `inventory.reservation`,
      `payment.authorization`); no sensitive attributes on any span.
- [x] Metrics: `/actuator/prometheus` on all five (HTTP count/duration/
      status, JVM/runtime, availability) plus six tag-free business counters
      (`checkout.success/failure`, `inventory.reservation.success/rejected`,
      `payment.authorization.success/declined`) — no customer/order/product/
      trace labels. Metrics stay pull-based; OTLP metric/log export off.
- [x] Correlated console logging (`[service,traceId,spanId]`): outcome + IDs
      only, no payloads or secrets; log lines link back to collector traces.
- [x] Local collection path (`observability/docker-compose.yml`,
      `observability/otel-collector.yml`, contrib 0.140.0): OTLP traces →
      debug log, Prometheus scrape of all services → :8889. Java services
      are not containerized; no dashboards, log store, or trace store.
- [x] Failure telemetry verified live: inventory rejection (422), payment
      decline (422), and inventory outage (order 503 → gateway 502) all emit
      spans, move the failure counters, and keep health green elsewhere.
- [x] Sensitive-data audit: no passwords/credentials/tokens/secrets in logs,
      labels, or span attributes. Closure fix: recorded observation errors
      are sanitized per deployable (`ObservationErrorSanitizer`, type name +
      HTTP status only — no bodies, no business messages, no cause chain),
      covered by `TelemetryPrivacyTest`, verified failing without the fix
      and passing with it.
- [x] 85 tests green: control plane 31 (29 unchanged + 2 observability),
      gateway 8 (6 + 2), order 18 (15 + 2 observability + 1 privacy),
      payment 12 (11 + 1),
      inventory 16 (15 + 1). New tests assert metric families and W3C
      traceparent propagation without a collector; no exporter-format
      assertions.
- [x] Control-plane regression: all 29 Phase 1 tests green, behavior
      unchanged; control-plane observability is covered by 2 integration
      tests (real spans, health UP).
- [ ] PENDING LIVE VERIFICATION (phase paused 2026-09-30, implementation
      and automated tests complete): control-plane live startup, live
      health endpoint, and live `/actuator/prometheus` were not observed —
      the required credential is unavailable in the verification session.
      Target-service live verification (health, metrics, checkout,
      rejection, decline, outage, distributed trace) is complete from the
      prior pass; the final post-redaction five-service live trace run is
      still to be collected. Nothing is claimed verified beyond what the
      verification tables record. Verdict: NOT READY TO COMMIT.
- [ ] RE-CHECK 2026-10-01 (still blocked, nothing newly marked verified):
      `SYSTIVEX_DB_PASSWORD`, `ORDER_DB_PASSWORD`, `PAYMENT_DB_PASSWORD`,
      and `INVENTORY_DB_PASSWORD` are all absent from the verification
      process environment (presence-checked by name only, no values shown).
      Actually observed: control-plane jar exits during Flyway startup
      (PostgreSQL 28P01 authentication failure for the control-plane user,
      `/actuator/health` unreachable on :8080); order-service jar exits
      during Flyway init (server requested SCRAM authentication with no
      password provided, :8082 unreachable). Gateway-service alone starts
      (`GET :8081/actuator/health` UP, `GET :8081/actuator/prometheus` 200)
      and the local OTel Collector starts (`:13133` health 200, OTLP
      :4317/:4318 listening, scrape warnings only for the stopped
      services) — but no distributed checkout, failure, outage, privacy,
      metrics, or logging live verification was possible without the four
      database credentials. Automated suites re-run green on 2026-10-01:
      control plane 31, gateway 8, order 18, payment 12, inventory 16
      (total 85); all five `package -DskipTests` builds succeed.
      IMPLEMENTED = Phase 3A code/tests/collector config as listed above.
      VERIFIED = automated tests + builds + gateway-solo health/metrics +
      collector health only. DEFERRED = full five-service live trace run
      and all live failure/privacy/metrics/logging checks, pending the
       four missing credentials. Verdict remains: NOT READY TO COMMIT.
- [ ] RE-CHECK 2026-10-01 (live run, still blocked — control plane newly
      verified live): all four credential variables are now PRESENT in the
      verification process environment (presence-checked by name only, no
      values shown, nothing written to disk). Local-only fix (git-ignored,
      not committed): `backend/src/main/resources/application.properties`
      carried a duplicated key prefix on the password line, so the packaged
      jar ignored the environment; fixed to
      `spring.datasource.password=${SYSTIVEX_DB_PASSWORD:}` and rebuilt the
      control-plane jar. Actually observed live: control-plane starts
      (Flyway validates 1 migration, schema v1 up to date; Hibernate
      validates, EntityManagerFactory initialized; Tomcat :8080;
      `GET /actuator/health` UP; `GET /actuator/prometheus` 200 with HTTP +
      JVM families). Gateway-service starts (`GET :8081/actuator/health` UP,
      `/actuator/prometheus` 200) and the local OTel Collector starts
      (`:13133` healthy, `:8889/metrics` 200). Order/payment/inventory jars
      still exit during Flyway startup: PostgreSQL 28P01 password
      authentication failure for `order_app`/`payment_app`/`inventory_app`
      against `order_db`/`payment_db`/`inventory_db` (all three databases
      exist; direct `psql` auth with the current environment values fails
      while `systivex_app` succeeds) — so :8082/:8083/:8084 stay
      unreachable. Blocked live: five-service health set, checkout through
      gateway, distributed trace, business spans, failure paths
      (rejection/decline/outage), live privacy check
      (CUSTOMER-TEST/PRODUCT-TEST/AMOUNT-TEST), live metrics/logging
      correlation. Automated suites re-run green on 2026-10-01 (actual
      count): control plane 31, gateway 8, order 18 (incl.
      TelemetryPrivacyTest), payment 12, inventory 16 (total 85, 0
      failures); all five `package -DskipTests` builds succeed. All
      services and the collector stopped after the run; nothing staged,
      committed, or pushed. IMPLEMENTED = Phase 3A code/tests/collector
      config as listed above. VERIFIED = automated tests (85) + builds +
      control-plane live startup/health/metrics + gateway-solo
      health/metrics + collector health/metrics only. DEFERRED = full
      five-service live trace run and all live
      checkout/failure/outage/privacy/metrics/logging checks, pending
      working `ORDER_DB_PASSWORD`, `PAYMENT_DB_PASSWORD`, and
       `INVENTORY_DB_PASSWORD` values. Verdict remains: NOT READY TO COMMIT.
- [ ] RE-CHECK 2026-10-01 (live run, five-service verification complete,
      new privacy blocker found): all four credential variables are PRESENT
      in the verification process environment (presence-checked by name only,
      no values shown, nothing written to disk). Actually observed live with
      all five jars running plus the local OTel Collector: inventory :8084
      (HikariPool connected, Flyway validated 2 migrations, schema up to
      date, Hibernate initialized, UP), payment :8083 (1 migration, UP),
      order :8082 (1 migration, UP), control-plane :8080 (UP), gateway :8081
      (UP), collector (`:13133` healthy, `:8889/metrics` re-exposing scrapes).
      `/actuator/prometheus` 200 on all five with HTTP families; business
      counters observed live (`checkout.success=1`,
      `inventory.reservation.success=1`, `payment.authorization.success=1`).
      Successful checkout CONFIRMED through the gateway with one trace ID
      across gateway/order/inventory/payment (server + client spans per hop
      plus `checkout`, `inventory.reservation`, `payment.authorization`);
      correlated `[service,traceId,spanId]` outcome lines in
      order/inventory/payment logs match the collector trace. Inventory
      rejection 422 `INVENTORY_REJECTED`, payment decline 422
      `PAYMENT_DECLINED`, and inventory outage (order 503
      `DOWNSTREAM_UNAVAILABLE` → gateway 502) all verified live with failure
      counters moved (`checkout.failure=4`, rejected/declined = 1 each),
      partial error traces exported, and health green on the survivors.
      LIVE PRIVACY TEST FAILED: synthetic `CUSTOMER-TEST-*` /
      `PRODUCT-TEST-001` / distinctive-amount values are absent from service
      logs and from span `exception` tags (`SanitizedObservationError`), but
      ARE present in exported error span attributes — `exception.message`,
      `exception.stacktrace`, and span Status messages — on RestClient client
      spans (gateway/order) and business spans (`checkout`,
      `payment.authorization`, `inventory.reservation`). Mechanism (read-only
      bytecode check, no code changed): Boot registers tracing handlers in a
      group composite ahead of ungrouped `ObservationHandler` beans, so the
      tracing handler records the raw exception event before
      `ObservationErrorSanitizer` replaces the final recorded error;
      `TelemetryPrivacyTest` stays green (re-run 1/1) because it asserts only
      the final recorded error, never span events. Useful telemetry survives
      (HTTP status/outcome/uri, trace context, service identity, span
      names). Automated suites re-run green on 2026-10-01 (actual count,
      `mvn test` without `clean` because `clean` cannot delete the
      running-process jars): control plane 31, gateway 8, order 18 (incl.
      `TelemetryPrivacyTest`), payment 12, inventory 16 (total 85, 0
      failures); all five `package -DskipTests` builds succeed after
       shutdown. All services and the collector stopped after the run; nothing
       staged, committed, or pushed. IMPLEMENTED = Phase 3A code/tests/
       collector config as listed above. VERIFIED = automated tests (85) +
       builds + full five-service live run (health/metrics/checkout/trace/
       rejection/decline/outage/logging) as recorded here. BLOCKED = live
       telemetry privacy (raw business values in exported error span events
       despite the sanitizer). Verdict remains: NOT READY TO COMMIT.
- [x] RE-CHECK 2026-10-06 (telemetry privacy fix verified live, Phase 3A
      closure): the late sanitizer is replaced by a per-deployable
      `SanitizerObservationHandlerGroup` ordered before Boot's tracing
      handler group (mechanism proven against Boot 4.1.1 / Micrometer
      1.17.1 / micrometer-tracing 1.7.1 bytecode; see DECISIONS.md D19
      correction). Automated suites green, 0 failures: control plane 31,
      gateway 8, order 21 (incl. span-data `TelemetryPrivacyTest` +
      negative-direction `TelemetryPrivacyUnsanitizedTest`), payment 12,
      inventory 16 (total 88); all five `package -DskipTests` builds
      succeed. Live, all five jars + local OTel Collector: health UP on
      :8080/:8081/:8082/:8083/:8084; gateway checkout 201 CONFIRMED;
      inventory rejection 422 `INVENTORY_REJECTED`; payment decline 422
      `PAYMENT_DECLINED`; inventory outage → order 503 → gateway 502 with
      health green on survivors and inventory recovered after restart;
      one distributed trace across gateway/order/inventory/payment plus
      `checkout`, `inventory.reservation`, `payment.authorization` spans;
      collector-log trace IDs match service log `[traceId,spanId]` lines.
      LIVE PRIVACY TEST PASSES: synthetic `CUSTOMER-TEST-XXXX` /
      `PRODUCT-TEST-001` / `AMOUNT-TEST-999` appear in HTTP 422 response
      bodies (contract preserved) but have ZERO hits in the full collector
      span log; exported `exception` events carry only
      `SanitizedObservationError` with type name + HTTP status
      (`... [HTTP 422/503] (response body withheld from telemetry)` /
      `<Type> (detail withheld from telemetry)`). HTTP behavior,
      controller behavior, and logs unchanged; metrics correct
      (`checkout.success=2/failure=3`, decline/counter families present,
      :8889 serving). Environment note: this host reaps console
      `java.exe` processes, so live services ran under `javaw.exe` (same
      JVM, file logging) — verification-only detail, no code impact. All
      services and the collector stopped after the run; nothing staged,
      committed, or pushed. IMPLEMENTED = Phase 3A code/tests/collector
      config as listed above, with the group-ordered sanitizer in all five
      deployables. VERIFIED = automated tests (88) + builds + full
      five-service live run (health/metrics/success checkout/distributed
      trace/rejection/decline/outage/logging) + live span-data privacy
      (zero token hits, sanitized markers present). Verdict: READY TO
      COMMIT (Phase 3A only; Phase 3B not started).

## What is complete (Phase 2B, 2026-09-30)

## What is complete (Phase 2B, 2026-09-30)

- [x] Three service-owned PostgreSQL databases, created by the tracked
      `backend/target-services/create-target-databases.sql` (no passwords in
      the repo): `order_db`/`order_app`, `payment_db`/`payment_app`,
      `inventory_db`/`inventory_app`. Control-plane `systivex` untouched.
- [x] Per-service persistence stack (Boot-managed): Spring Data JPA,
      PostgreSQL driver, Flyway + PostgreSQL module; `ddl-auto=validate`
      everywhere; each service migrates only its own history.
- [x] `orders` table (PENDING → CONFIRMED/FAILED, UUID keys, CHECK
      constraints); checkout persists PENDING first, CONFIRMED only when both
      downstream calls succeed, FAILED otherwise — failed attempts recorded,
      never dropped or falsely confirmed.
- [x] `payments` table (AUTHORIZED/DECLINED, both outcomes persisted);
      deterministic 5000.00 decline rule kept, still no provider integration.
- [x] `inventory_items` + `inventory_reservations` (no FK between them, so
      unknown-product attempts are recordable); reserve locks the stock row
      (pessimistic write + `@Version`), decrements only on success, records
      REJECTED attempts; `V2` seeds dev stock `SKU-1001 × 10`.
- [x] Documented non-atomicity across the three databases (no distributed
      transactions, no cross-DB writes) — see `ARCHITECTURE.md` §4, `DECISIONS.md` D17.
- [x] 47 service tests green (Testcontainers PostgreSQL, no local DB touched):
      order 15, payment 11, inventory 15 (incl. concurrent-reserve locking),
      gateway 6. Full 4-process run with real databases demonstrated success
      + inventory rejection + payment decline paths.
- [x] Controlled failures preserved: no SQL/JDBC text, stack traces, or
      credentials on the wire; storage outage surfaces as 503 per service.

## What is complete (Phase 2A, 2026-09-29)

## What is complete (Phase 2A, 2026-09-29)

- [x] Four standalone target services under `backend/target-services/`:
      gateway (8081), order (8082), payment (8083), inventory (8084).
- [x] Each on Java 21 / Spring Boot 4.1.1 / Maven, own wrapper + app class +
      Actuator health; control plane untouched on 8080.
- [x] Real HTTP checkout flow: gateway → order → inventory + payment
      (RestClient, config-driven URLs), verified across live processes.
- [x] Explicit per-service DTO contracts; deterministic stateless
      approve/reject rules (documented as temporary until Phase 2B).
- [x] Controlled failures: 400 validation, 422 business rejection, 503 order
      level, 502 gateway level — no stack traces or internals on the wire.
- [x] 23 service tests green (real-HTTP boundaries, loopback stubs for
      downstream only); full 4-process run demonstrated success + inventory
      rejection + inventory outage.

## What is complete (Phase 1, 2026-09-29)

- [x] PostgreSQL 18 wired as primary persistence (`systivex` db, `systivex_app` user).
- [x] Spring Data JPA + Hibernate; `ddl-auto=validate` (no schema auto-generation).
- [x] Flyway owns the schema; `V1__create_system_twin.sql` migrates at startup.
- [x] Twin persistence model: `SystemEntity` (7 types, JSONB metadata, UUID keys)
      and directed `SystemRelationship` (8 types) with FK/unique/check constraints.
- [x] `TwinService` + two repositories; DTO-based REST CRUD under `/api/v1/twin`.
- [x] Stable error responses: 400 validation/malformed, 404 unknown id, 409 conflicts.
- [x] 29 tests green (unit + Testcontainers migration/repository/API integration).
- [x] Local run verified against PostgreSQL: Flyway V1 applied, health `UP`,
      entity/relationship REST cycle exercised.
- [x] Credentials stay out of Git: live `application.properties` ignored, password
      via `SYSTIVEX_DB_PASSWORD`, only the safe example template tracked.

## What is complete (Phase 0, 2026-09-28)

- [x] Repository root created (`Systivex/`).
- [x] Backend generated via Spring Initializr into `backend/`.
- [x] Backend pinned to **Java 21** (`<java.version>21</java.version>`, `release 21` verified).
- [x] Backend pinned to **Spring Boot 4.1.1** (parent + banner verified).
- [x] Maven build via Wrapper (3.9.16, `wrapperVersion 3.3.4`) verified.
- [x] Dependencies limited to the generated set: `webmvc`, `validation`, `actuator` (+ test starters).
- [x] Application starts (Tomcat 11.0.24 on `:8080`, startup ~1.4 s).
- [x] Actuator health endpoint verified: `GET /actuator/health` → `{"status":"UP"}`.
- [x] Test suite verified: `./mvnw test` → 1 test (`contextLoads`), 0 failures.
- [x] Build verified: `./mvnw package` → `systivex-0.0.1-SNAPSHOT.jar` (~23.6 MB).
- [x] Root documentation created: `README.md`, `ARCHITECTURE.md`, `DEVELOPMENT.md`,
      `PROJECT_STATUS.md` (this file), `DECISIONS.md`, `THREAT_MODEL.md`.
- [x] Root `.gitignore` created (Java/Maven, IDE, OS, secrets).
- [x] Workspace-local JDK 21 environment: `.vscode/settings.json` (local-only,
      git-ignored) pins the Java extension and new integrated terminals to
      `C:\Program Files\Java\jdk-21.0.10`; portable template tracked at
      `.vscode/settings.json.example`. Global `JAVA_HOME`/PATH untouched.

## What is intentionally NOT implemented

These are **deferred by decision**, not missing by accident. Do not report them as regressions.

- Richer twin ingestion (code/arch/runtime connectors), Git integration,
  System Twin telemetry ingestion of any kind.
- Loki/Tempo exploration UIs, automated anomaly
  detection, any agent use of telemetry, observability-driven decisions.
- Agent orchestration, tool mediation, simulation, evidence model.
- Policy / approval / controlled-execution / verification logic.
- Filtering, pagination, graph traversal, bulk import on the twin API.
- Authentication/authorization on the API.
- Redis configuration or usage.
- Spring AI / Ollama integration, model configuration.
- React (JavaScript, no TypeScript) frontend; no `frontend/` directory.
- Docker packaging of the Java services or any container orchestration
  (Compose exists only for the telemetry collector, `observability/`).
- Cross-service database access, shared schemas, distributed transactions
  (rejected by decision D17, not missing).
- Message brokers; Kubernetes; WebSockets;
  vector databases; MCP; multi-agent architecture; cloud infrastructure.
- Placeholder or speculative backend packages beyond `twin`.

## Verification record (2026-09-30, Phase 3A)

| Check | Command | Result |
|---|---|---|
| Tests (control plane) | `./mvnw clean test` from `backend/` (Docker running) | 31 green (29 existing + 2 observability), 0 failures |
| Tests (gateway) | `./mvnw.cmd clean test` in gateway-service dir | 8 green (6 existing + 2 observability) |
| Tests (order) | same in order-service dir (Docker running) | 17 green (15 existing + 2 observability) |
| Tests (payment) | same in payment-service dir (Docker running) | 12 green (11 existing + 1 observability) |
| Tests (inventory) | same in inventory-service dir (Docker running) | 16 green (15 existing + 1 observability) |
| Build (×5) | `package -DskipTests` (control plane + 4 services) | 5 jars |
| Collector | `docker compose up -d` in `observability/` | OTLP :4317/:4318, :8889 metrics, health :13133 |
| Health (×4) | `GET localhost:808{1..4}/actuator/health` (all running) | `{"status":"UP"}` on all four |
| Metrics (×4+1) | `GET :808{1..4}/actuator/prometheus`, `:8889/metrics` | HTTP/JVM families everywhere; business counters; collector re-exposes scraped metrics |
| E2E success | `POST localhost:8081/api/v1/checkout` (all running, real DBs) | `CONFIRMED`; `checkout.success=1`; one trace ID across gateway/order/inventory/payment in collector log (10 spans: server+client per hop + 3 business spans) |
| E2E inventory rejection | same, quantity 99 | 422 `FAILED`/`INVENTORY_REJECTED`; `checkout.failure`, `inventory.reservation.rejected` incremented; error spans exported |
| E2E payment decline | same, amount 6000.00 | 422 `FAILED`/`PAYMENT_DECLINED`; `payment.authorization.declined` incremented; error spans exported |
| E2E outage | same, inventory process stopped | order 503 `DOWNSTREAM_UNAVAILABLE`, gateway 502 generic; `checkout.failure` incremented; partial trace (gateway+order spans) still exported |
| Log correlation | service stdout | `INFO [order-service,<traceId>,<spanId>]` lines match collector trace IDs |
| Sensitive-data audit | grep over logs/configs + collector span attributes | no passwords/credentials/tokens/secrets; limitation: client-error `exception.message` echoes 4xx bodies (see THREAT_MODEL §3a) |

## Verification record (2026-09-30, Phase 2B)

| Check | Command | Result |
|---|---|---|
| Tests (order) | `./mvnw.cmd clean test` in order-service dir (Docker running) | 15 green (6 boundary + 1 outage + 5 repository + 3 migration) |
| Tests (payment) | same in payment-service dir | 11 green (5 boundary + 3 repository + 3 migration) |
| Tests (inventory) | same in inventory-service dir | 15 green (6 boundary + 4 repository + 4 migration + 1 concurrent locking) |
| Tests (gateway) | same in gateway-service dir | 6 green, unchanged |
| Tests (control plane) | `./mvnw clean test` from `backend/` | 29 green, 0 failures — untouched |
| Build (×4) | `./mvnw.cmd -q package -DskipTests` | 4 jars |
| Health (×4) | `GET localhost:808{1..4}/actuator/health` (all running) | `{"status":"UP"}` on all four |
| E2E success | `POST localhost:8081/api/v1/checkout` (all running, real DBs) | `CONFIRMED`; order CONFIRMED in order_db, stock decreased + reservation in inventory_db, authorization in payment_db |
| E2E inventory failure | same, quantity 99 | 422 `FAILED` / `INVENTORY_REJECTED`; order FAILED in order_db, stock unchanged |
| E2E payment failure | same, amount 6000.00 | 422 `FAILED` / `PAYMENT_DECLINED`; order FAILED, DECLINED row persisted |

## Verification record (2026-09-29, Phase 2A)

| Check | Command | Result |
|---|---|---|
| Tests (×4) | `./mvnw.cmd clean test` in each service dir | inventory 5, payment 5, order 7, gateway 6 — all green, no Docker needed |
| Build (×4) | `./mvnw.cmd -q package -DskipTests` | 4 jars (~23.6 MB each) |
| Health (×4) | `GET localhost:808{1..4}/actuator/health` (all running) | `{"status":"UP"}` on all four |
| E2E success | `POST localhost:8081/api/v1/checkout` (all running) | `CONFIRMED` with reservationId + authorizationId |
| E2E biz failure | same, quantity 99 | 422 `FAILED` / `INVENTORY_REJECTED` end to end |
| E2E outage | same, inventory stopped | order 503 `DOWNSTREAM_UNAVAILABLE`, gateway 502 generic |

## Verification record (2026-09-29, Phase 1)

| Check | Command | Result |
|---|---|---|
| Tests | `./mvnw clean test` (from `backend/`, Docker running) | `Tests run: 29, Failures: 0, Errors: 0` — BUILD SUCCESS (~25 s) |
| Build | `./mvnw package -DskipTests` | `systivex-0.0.1-SNAPSHOT.jar` (~55.9 MB) |
| Migrate (local PG 18) | boot with `SYSTIVEX_DB_PASSWORD` set | `Successfully applied 1 migration ... now at version v1` |
| JPA validation | same boot | `ddl-auto=validate` passes, no schema changes |
| Health | `GET /actuator/health` | `{"status":"UP"}` (+ liveness/readiness groups) |
| Twin REST | POST/GET/list entity + relationship, bad-ref, bad-body | 201/200/200, 404 on unknown id, 400 on invalid body |

Phase 0 record (2026-09-28) is retained below for history.

| Check | Command | Result |
|---|---|---|
| JDK | `java -version` with `JAVA_HOME=jdk-21.0.10` | `21.0.10 LTS` |
| Tests | `./mvnw test` (from `backend/`) | `Tests run: 1, Failures: 0, Errors: 0` — BUILD SUCCESS |
| Build | `./mvnw -q package -DskipTests` | `systivex-0.0.1-SNAPSHOT.jar` produced |
| Startup | `./mvnw spring-boot:run` | `Started SystivexApplication`, Tomcat `:8080` |
| Health | `GET /actuator/health` | `{"status":"UP"}` (+ liveness/readiness groups) |
| Actuator index | `GET /actuator` | Only `health` link exposed (expected default) |

## Problem discovered → resolved (workspace-local JDK 21)

- **Default JDK on PATH is 17, not 21.** `JAVA_HOME` pointed at an OpenJDK 17
  installation and `mvn -version` reported Java 17. The project requires Java 21.
- **Resolution (2026-09-28, no global change):** `.vscode/settings.json` sets
  `java.jdt.ls.java.home` + `java.configuration.runtimes` (JavaSE-21 default) and
  `terminal.integrated.env.windows` (`JAVA_HOME` + JDK 21 `bin` prepended to the
  inherited PATH). Verified in a fresh equivalent shell: `java 21.0.10`,
  `JAVA_HOME=C:\Program Files\Java\jdk-21.0.10`, Maven `Java version: 21.0.10`,
  `.\mvnw.cmd clean test` → 1 test, 0 failures, BUILD SUCCESS. No code, dependency,
  or Maven-config change was needed; Java 17 projects are unaffected.
- **Git decision:** `settings.json` is intentionally **untracked** (absolute local
  JDK path would break portability); only `.vscode/settings.json.example` is
  tracked. Root `.gitignore` uses `.vscode/*` + `!.vscode/settings.json.example`.

## Immediate next milestone (proposed, not started)

**Milestone 2C — Twin observation of the target environment:**

1. Repository connector reading the target services into twin rows (first
   actual observation; still no agents or execution).
2. Authentication on any API before the surface leaves the local machine.

Out of scope: agents, simulation, approvals UI, brokers, K8s,
vector search, frontend beyond API consumption readiness. Cross-database
atomicity for checkout stays out unless a future decision with a concrete
reconciliation design asks for it.
