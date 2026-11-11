# Systivex Architectural Decisions

Record of binding decisions taken at project foundation (2026-09-28) and
extended in Phase 1 and Phase 2A (2026-09-29), Phase 2B and Phase 3A (2026-09-30),
Phase 3B-1/3B-2 (2026-10-06/07), Phase 4 (2026-10-09), Phase 5 (2026-10-10),
Phase 6 (2026-10-13), Phase 7 (2026-10-14), Phases 8/9 (2026-10-15),
Phase 10 (2026-10-15), Phase 11 (2026-10-15/16), Phase 12 S0/S1
(2026-10-16/17), Phase 13 S0 (2026-10-17), and Phase 13 S1 authorized
(2026-10-18).
Status labels: **decided** (binding now) / **planned** (intent, not yet implemented).

## D1. Java 21 — decided

- **Decision:** Java 21 as the language baseline (`<java.version>21</java.version>`, `release 21`).
- **Rationale:** LTS, virtual threads, modern language features; matches Spring Boot 4.x requirements.
- **State:** Implemented and verified (`./mvnw test` compiles with `release 21` on JDK 21.0.10).

## D2. Spring Boot (4.1.1) — decided

- **Decision:** Spring Boot 4.1.1 via `spring-boot-starter-parent` for the control plane.
- **Rationale:** Convention-based wiring, production-ready Actuator surface, mature test support.
- **State:** Implemented and verified (banner `v4.1.1`, Tomcat 11.0.24).

## D3. Maven (Wrapper) — decided

- **Decision:** Maven with the checked-in Wrapper (3.9.16, `wrapperVersion 3.3.4`); no Gradle.
- **Rationale:** Reproducible builds without a preinstalled Maven; standard Spring layout.
- **State:** Implemented and verified (`./mvnw test | package | spring-boot:run`).

## D4. Modular monolith for the Systivex control plane — decided

- **Decision:** Systivex itself is a **modular monolith**: one Spring Boot deployment
  with strict internal module boundaries (`twin`, `intelligence`, `policy`,
  `execution`, `verification`, `observation`, `api` as target modules).
- **Rationale:** Single deployable is operable at this scale; explicit module APIs
  preserve the option to extract services later without paying distribution costs now.
- **State:** Planned structure; **no module packages exist yet** (no placeholders by decision).

## D5. Microservices reserved for the target environment — decided

- **Decision:** Microservices are the shape of the **future target software environment**
  (systems under observation/management), never of the Systivex control plane at this stage.
- **Rationale:** Keeps a hard control-plane vs. target-environment separation;
  avoids distributed-systems overhead before there is anything to manage.
- **State:** Deferred; no target services exist.

## D6. PostgreSQL as primary persistence — decided (activated 2026-09-29)

- **Decision:** PostgreSQL is the primary system of record (twin state first;
  proposals, policy decisions, execution/verification records later).
- **Rationale:** Relational integrity and auditability suit safety-critical records.
- **State:** Implemented in Phase 1: local PostgreSQL 18, `systivex` database,
  `systivex_app` owner. Previously deferred; activated when the twin model
  needed persistence, per the original intent.

## D7. Redis only when justified — decided

- **Decision:** No Redis until a concrete, measured need (e.g. rate limiting, ephemeral
  coordination, cache with invalidation story) is recorded.
- **Rationale:** Avoids a second state system and its consistency/ops burden prematurely.
- **State:** Deferred; no Redis dependency or configuration exists.

## D8. Spring AI + Ollama later — planned

- **Decision:** Agent reasoning will use Spring AI against local Ollama models first
  (cloud models only if a later decision permits).
- **Rationale:** Keeps inference local and cost-predictable during development;
  Spring AI gives provider portability behind the control-plane boundary.
- **State:** Deferred; no Spring AI dependency, no model configuration exists.

## D9. REST + SSE initially — decided

- **Decision:** Synchronous surface is REST; streaming progress (analysis/simulation)
  is Server-Sent Events. No WebSockets at this stage.
- **Rationale:** REST covers CRUD/authorization flows; SSE covers one-way progress
  without the lifecycle/state costs of bidirectional sockets.
- **State:** Partially implemented; twin entity/relationship CRUD exists under
  `/api/v1/twin`. Proposals/approvals/records and SSE are still planned.

## D10. No premature infrastructure — decided

- **Decision:** Do not introduce Kafka, RabbitMQ, Kubernetes, WebSockets, vector
  databases, MCP, multi-agent architecture, or cloud infrastructure at this stage.
  Likewise no Docker Compose, security architecture, or frontend scaffolding yet.
- **Rationale:** Each adds ops surface, failure modes, and security scope without a
  consumer. Infrastructure follows demonstrated need and a recorded decision.
- **State:** Enforced in this phase; no Kafka/RabbitMQ/K8s/WebSockets/vector DB/
  MCP/multi-agent/cloud. `pom.xml` holds only the Phase 1 persistence and web
  set (see `DEVELOPMENT.md` §2 for the list).

## D11. Flyway owns the schema; Hibernate only validates — decided (2026-09-29)

- **Decision:** All schema change goes through versioned Flyway migrations under
  `db/migration/`; `spring.jpa.hibernate.ddl-auto=validate` everywhere, including
  local runs. `update`/`create`/`create-drop` are banned outside throwaway
  scratch work that never touches a shared database.
- **Rationale:** The migration history is the reviewable, replayable record of
  what the schema is; validation keeps the JPA mapping honest without giving
  the ORM write access to the schema.
- **State:** Implemented: `V1__create_system_twin.sql`, validated at every
  startup and in tests.

## D12. Twin persistence model v1 — decided (2026-09-29)

- **Decision:** Two tables (`system_entity`, `system_relationship`), UUID keys,
  closed enum type sets (7 entity, 8 relationship), JSONB metadata through
  Hibernate's native JSON mapping (no extra JSON library), FK constraints with
  `ON DELETE CASCADE`, unique `external_ref` and `(source, target, type)`
  triples, no-self-reference check.
- **Rationale:** Covers what Phase 1 needs to prove (persist, link, constrain)
  with the fewest moving parts; enums stay closed until a connector demands more.
- **State:** Implemented with repository, service, DTO REST layer, and
  constraint tests.

## D13. Testcontainers for database tests, one container per run — decided (2026-09-29)

- **Decision:** Integration tests run against throwaway PostgreSQL 18 containers
  (single shared container per JVM via `@DynamicPropertySource`); the
  developer's local database is never used by tests. Testcontainers stays a
  test-scope-only dependency.
- **Rationale:** CI-reproducible without local setup; one container avoids the
  churn of per-class containers. Local runs still target the developer's own
  PostgreSQL with `SYSTIVEX_DB_PASSWORD` from the environment.
- **State:** Implemented: 21 of 29 tests run against the container.

## D14. Boot 4 dependency notes — decided (2026-09-29)

- **Decision:** Use `spring-boot-starter-flyway` (raw `flyway-core` alone gets no
  auto-configuration in Boot 4); Testcontainers modules by their 2.x names
  (`testcontainers-postgresql`, version via an imported `testcontainers-bom`
  pinned to the release Boot 4.1.1 manages); `spring-boot-starter-restclient-test`
  for `TestRestTemplate` in API tests.
- **Rationale:** Boot 4 split several starters/modules; following the new
  coordinates keeps versions managed instead of hand-pinned.
- **State:** Implemented in `pom.xml` (see the BOM comment there).

## D15. Target microservices live apart from the control plane — decided (2026-09-29)

- **Decision:** The observed system (`backend/target-services/`: gateway, order,
  payment, inventory) is built as separate Spring Boot deployables, while
  Systivex itself stays a modular monolith. The two sides share no code, no
  database, and no packages — only HTTP contracts documented in
  `ARCHITECTURE.md` §4.
- **Rationale:** The control-plane/target-environment split is the whole point
  of the architecture: the decider must not be the thing being changed. Separate
  deployables make the future observation boundary real instead of notional,
  without paying for brokers, service mesh, or orchestration before anything
  needs them.
- **State:** Implemented: 4 services, fixed dev ports 8081–8084, verified
  end to end. Control-plane code untouched.

## D16. Plain RestClient edge, deterministic stateless targets — decided (2026-09-29)

- **Decision:** Service-to-service calls use plain `RestClient` with
  configuration-driven URLs; the "gateway" is an explicit forwarding controller,
  not a gateway framework. Inventory/payment answer from fixed demo rules
  (reject > 5 units, decline > 5000.00) with no database; failures surface as
  422/503/502 with sanitized bodies and basic timeouts, no retry or
  circuit-breaker libraries.
- **Rationale:** A framework gateway and resilience machinery would hide the
  very HTTP behavior Phase 2A exists to demonstrate. Deterministic rules make
  tests and demos repeatable; persistence arrives per-service in Phase 2B.
- **State:** Superseded in Phase 2B for the persistence part (D17): the demo
  quantity rule is replaced by real stock, but the payment amount rule stays
  and the RestClient/no-retry/no-gateway-framework shape is unchanged.

## D17. Service-owned databases, no cross-service data access — decided (2026-09-30)

- **Decision:** Each target service owns exactly one PostgreSQL database
  (`order_db`/`order_app`, `payment_db`/`payment_app`,
  `inventory_db`/`inventory_app`), migrated by that service's own Flyway
  history and validated by its own JPA mappings (`ddl-auto=validate`). The
  control-plane database (`systivex`/`systivex_app`) is untouched and shares
  nothing with them. No service reads or writes another service's tables, no
  shared schemas, no cross-database foreign keys — not even for "read-only"
  convenience. If order-service needs payment or inventory facts, it calls
  those services over HTTP and treats the response as untrusted input
  (validated DTOs, same as before).
- **Rationale:** A shared database would quietly recouple services that HTTP
  boundaries keep apart: one team's migration could break another service,
  and "just this one join" becomes distributed logic with no contract. The
  database boundary makes the service boundary real — each schema can evolve
  with its owner, and every cross-service read stays visible as an HTTP call
  with explicit failure modes. Separate application users (each owning only
  its database) enforce this at the PostgreSQL level, not just by convention.
- **State:** Implemented: `orders` in order_db; `payments` in payment_db;
  `inventory_items` + `inventory_reservations` in inventory_db (no FK between
  the two, so rejected attempts for unknown products are still recordable).
  Known limitation accepted, not solved: checkout is not atomic across the
  three databases — a FAILED order can coexist with a standing reservation.
  Faking atomicity (cross-DB writes, a transaction coordinator) was rejected;
  the honest PENDING → CONFIRMED/FAILED record is the foundation a future
  reconciliation/simulation story can build on.

## D18. Telemetry before twin ingestion — decided (2026-09-30)

- **Decision:** Instrument the target environment for metrics/traces/logs
  (Phase 3A) before building any System Twin runtime ingestion of that
  telemetry.
- **Rationale:** Ingestion built against uninstrumented services would define
  its data contracts against guesses. Real, running telemetry — with known
  span names, metric names, and correlation behavior verified end to end —
  gives the future ingestion work an observed contract to consume instead of
  a speculative one. The control plane still consumes nothing; the two sides
  of the pipeline are built and verified independently.
- **State:** Implemented: the four target services plus the control plane emit
  OTLP traces and Prometheus metrics; collection is verified locally. No twin
  ingestion exists.

## D19. Minimal Boot-managed observability, pull-based metrics — decided (2026-09-30)

- **Decision:** Two dependencies per service, both version-managed by the
  Boot 4.1.1 BOM: `spring-boot-starter-opentelemetry` (tracing + W3C context
  propagation) and `micrometer-registry-prometheus` (scrape endpoint). No
  hand-pinned OTel/Micrometer versions, no second instrumentation library.
  Metrics stay pull-based (`/actuator/prometheus`); OTLP metric export and
  OTLP log export are disabled. Logs stay on the console with a trace/span
  correlation pattern. Sampling is 1.0 — acceptable only because all traffic
  is local development volume.
- **Rationale:** Managed versions remove a whole class of span-duplication and
  version-skew faults. Pull-based metrics need no per-service export
  configuration and fail visibly (empty scrape) rather than silently (dropped
  push). A single local OpenTelemetry Collector (contrib, pinned image) is
  the only infrastructure: OTLP receiver for traces, Prometheus receiver
  re-scraping the services, debug exporter to its own log for trace
  verification, Prometheus exporter on :8889. No dashboards, log store, or
  trace store — visualization is Phase 3B.
- **State:** Implemented. Three custom business spans only (`checkout`,
  `inventory.reservation`, `payment.authorization`); everything else is
  automatic HTTP instrumentation. Six tag-free business counters
  (`checkout.success/failure`, `inventory.reservation.success/rejected`,
  `payment.authorization.success/declined`) — outcome in the name, no
  customer/order/product/trace labels. RestClient builders carry the
  observation registry explicitly so trace context crosses each HTTP hop.
- **Telemetry-error redaction (closure fix, 2026-09-30):** automatic
  client-error spans copied downstream 4xx response bodies into
  `exception.message`, and custom business observations recorded
  service-layer messages — both can carry customer/product/amount data.
  Verified root cause: the OTel bridge calls `Span.recordException`
  immediately in the tracing handler's `onError`, so a stop-time
  `ObservationFilter` would run too late. Fix: one stateless
  `ObservationErrorSanitizer` (`ObservationHandler<Observation.Context>`,
  `@Order(HIGHEST_PRECEDENCE)`, picked up by Boot's ordered handler
  registration) per deployable replaces the recorded error with a
  payload-free equivalent carrying only the original exception type name
  and, for `HttpStatusCodeException`, the HTTP status. No cause chain (a
  cause would reintroduce the payload via the stacktrace attribute).
  Thrown exceptions, API responses, and logs are untouched. Side effect,
  accepted: timer `error` tags report the sanitizer type on error
  observations instead of the original type (still low-cardinality).
   Regression test `TelemetryPrivacyTest` (order-service) fails without the
   sanitizer and passes with it — verified both directions.
- **Telemetry-error redaction, correction (2026-10-06):** the 2026-09-30
  `@Order(HIGHEST_PRECEDENCE)` fix was insufficient and live verification
  proved it: Boot 4.1.1 registers tracing/meter handlers through its
  `TracingAndMeterObservationHandlerGroup` composite AHEAD of ungrouped
  `ObservationHandler` beans, so the tracing handler's `onError` recorded
  the raw exception (`Span.recordException` → `exception.message` /
  `exception.stacktrace` / status description) BEFORE the late sanitizer
  replaced the final recorded error. Verified against the actual jars
  (Boot 4.1.1 `ObservationHandlerGroups.register` sorts groups and registers
  group composites before ungrouped handlers; Micrometer 1.17.1
  `SimpleObservation.notifyOnError` notifies handlers sequentially in
  registration order; Micrometer-tracing 1.7.1 `TracingObservationHandler`
  records `context.getError()` at notification time). Fix, per deployable:
  the sanitizer now belongs to its own `SanitizerObservationHandlerGroup`
  ordered before the tracing group, so its `onError` replaces the error
  first and the tracing handler records only the sanitized replacement.
  Joining the tracing group itself was rejected: group members dispatch
  first-match, which would suppress the real tracing handler. Preserved:
  HTTP status, original exception type name, observation/span names, trace
  context. Removed: response bodies, business messages, cause chain.
  `TelemetryPrivacyTest` now asserts on in-memory RECORDED SPAN DATA
  (event attributes + status description), and
  `TelemetryPrivacyUnsanitizedTest` proves the same payload leaks with the
  sanitizer removed — verified failing-without / passing-with on 2026-10-06,
  plus a live collector-log check with zero synthetic-token hits.

## D20. Prometheus + Grafana visualization on the existing metrics path — decided (2026-10-06)

- **Decision:** Extend the existing `observability/docker-compose.yml`
  (no second Compose file) with pinned `prom/prometheus:v3.5.0` (:9090)
  and `grafana/grafana:12.1.0` (:3000). Prometheus scrapes ONLY the OTel
  Collector's `:8889` exposition — the collector stays the sole scraper
  of the Java services, so no duplicate collection path exists. Grafana
  gets its Prometheus datasource and the single `Systivex Metrics`
  dashboard purely through file provisioning (`observability/grafana/`);
  local Grafana defaults, no secrets in the repo, no database files in Git.
  Per-service identity uses a `server_port` → `service` relabel in
  `prometheus.yml`, because the collector stamps `service_name="systivex"`
  on every scraped app metric. OTel histograms arrive without buckets on
  this path, so the latency panel is a sum/count average, not a p95 —
  recorded here so nobody "fixes" it back to a quantile that returns no
  data. No Java application changes; no Loki/Tempo, no twin ingestion, no
  agent use (all still deferred).
- **Rationale:** Visualization must consume the observed contract, not
  redefine it: one more scrape hop would double-load the services and
  split the metric identity scheme. Provisioning (not browser clicks)
  keeps the dashboard reproducible from a fresh checkout.
- **State:** Implemented and verified live 2026-10-06 (checkout + failure
  scenarios move the dashboard metrics; control plane verified on a
  temporary :8090 because :8080 is held by another project on this
  machine).

## D21. Loki log exploration over the existing OTLP path — decided (2026-10-07)

- **Decision:** Make application logs searchable in Grafana via Loki
  (`grafana/loki:3.5.0`, single binary, filesystem storage, no auth — local
  development only), fed by the existing OTel Collector. No second Compose
  file, no Tempo, no new dashboard: Grafana Explore is the log UI for this
  phase. Application logging stays exactly as Phase 3A defined it (console
  pattern `[service,traceId,spanId]`, outcome + internal IDs only); OTLP
  export is an additional sink, not a replacement.
- **Rationale:** Logs are the third signal of the already-observed contract
  (spans, counters, correlated lines). Reusing the OTLP path keeps one
  collection story instead of adding a parallel shipper per service.
- **The missing bridge (verified, not assumed):** setting Boot's
  `management.logging.export.otlp.*` properties alone ships nothing — Boot
  wires the SDK and the OTLP log exporter but installs no Logback bridge, so
  no record ever reaches the exporter. Each deployable therefore gets the
  smallest working addition: `opentelemetry-logback-appender-1.0` (version
  from an imported `opentelemetry-instrumentation-bom-alpha`,
  `2.28.1-alpha` — that line compiles against `opentelemetry-api 1.62.0`,
  exactly what Boot 4.1.1 manages, so no SDK skew) plus a
  `logback-spring.xml` (Boot base config + one OTEL appender; console
  untouched) and a small installer bean calling
  `OpenTelemetryAppender.install(openTelemetry)` on ready. Only the
  trace/span MDC keys are copied as attributes; nothing else about the log
  statements changes, and the sanitizer is untouched.
- **Collector:** one `logs` pipeline appended to the existing
  `observability/otel-collector.yml` (`otlp` receiver → `batch` →
  `otlphttp/loki`: `http://loki:3100/otlp` — the exporter appends
  `/v1/logs` itself, so the full path must NOT be configured or Loki
  answers 404). Traces/metrics pipelines unchanged. The contrib `loki`
  exporter was evaluated first and rejected: it no longer ships in
  collector 0.140.x (removed upstream after deprecation) in favor of
  OTLP export to Loki's native endpoint.
- **Label policy (binding):** only `service.name` becomes a Loki index
  label. Trace/span IDs, `service.instance.id` (fresh UUID per Boot start),
  SDK/process/host resource detail, and scope fields travel as structured
  metadata — searchable in LogQL (`| traceId="<hex>"`) but never stream
  labels, so one trace or restart can never fan out into per-trace streams.
  Customer/order/product/amount values never leave the JVM in log form, so
  they cannot become labels either. Verified against the running Loki via
  the `/labels` index endpoint (carries `service_name` only) plus
  positive/negative metadata-filter queries.
- **State:** Implemented; `OtlpLoggingExportTest` (export wired, checkout
  survives a missing collector) plus `OtlpLogDeliveryTest` (a checkout
  record actually arrives at a stub OTLP receiver with MDC trace context —
  the regression test the missing bridge would have failed). Live
  verification pending service restarts (see `PROJECT_STATUS.md`).

## D22. Read-only repository connector + localhost-only sync trigger — decided (2026-10-09)

- **Decision:** The first twin ingestion is a read-only repository
  connector (`observation` package, control plane only): a scanner reads
  the target-service tree — service directories, tracked
  `application.example.properties` (names, ports, datasource DB/owner,
  downstream URLs), controller `@RequestMapping`/`@XMapping` annotations,
  Flyway `CREATE TABLE` statements — and maps them onto the EXISTING
  closed enums (4 `SERVICE`, 3 `DATABASE`, 4 `TABLE`, 4 `API`; `CALLS` /
  `DEPENDS_ON` / `CONTAINS` / `WRITES` / `EXPOSES`). No enum expansion was
  needed; none is permitted without a documented incompatibility. Stable
  `target-*` external refs; ports/paths/ownership/provenance
  (`managedBy: repository-connector`, `phase4-v1`) in JSONB metadata.
  Sync is explicit (`POST /api/v1/twin/sync`), idempotent, and one
  transaction: discovery validates fully before any write; connector-owned
  rows are created/refreshed; manual rows are never touched; identity
  collisions abort with 409 and roll back; bad roots fail 400 with no
  writes. No watchers, schedulers, Git remotes, or telemetry ingestion.
- **Rationale:** The twin had schema but no content; the repository is the
  most deterministic source of truth available (configs, routes, and
  migrations are already reviewed facts), while telemetry ingestion would
  define consumption contracts against a noisier signal. An explicit
  trigger keeps observation reviewable and replayable — a twin that
  silently rewrites itself cannot be the authoritative record T3 demands.
- **Localhost boundary (binding):** this phase adds no authentication
  system, so the new mutating trigger must not serve the network.
  Verified starting position: no `spring-security`, no `server.address` —
  default bind is all interfaces, and the existing twin POST endpoints are
  already open. Boundary, two layers, no header trust: (1)
  `server.address=127.0.0.1` in the tracked example config (fresh copies
  bind loopback; existing live copies must add it — see `DEVELOPMENT.md`
  §10); (2) `LoopbackGuard` in the sync path refuses any
  non-loopback `getRemoteAddr()` peer with 403 (`X-Forwarded-For` ignored
  by design; missing peer denied). Covered by `LoopbackGuardTest` plus an
  HTTP 403 test. Full API authentication stays deferred (Milestone 2C
  item 2, still open).
- **What the scanner never touches:** live `application.properties`,
  process environment, and the repository itself (reads only; scanned
  files are never executed). Only placeholder defaults (the part after
  `:` in `${VAR:default}`) are consumed, so datasource passwords and
  local secrets cannot flow into twin metadata. Nothing is logged except
  counts/names/refs — all already-tracked facts.
- **State:** Implemented and tested (20 new tests, 51 green total, build
  succeeds). Local live boot pending the control-plane credential
  (see `PROJECT_STATUS.md`).

## D23. Deterministic read-only change-impact traversal — decided (2026-10-10)

- **Decision:** The first `intelligence` module is a read-only deterministic
  BFS over the persisted twin graph (`intelligence` package, control plane
  only): `GET /api/v1/twin/impact` resolves 1–10 starting entities (UUID or
  `external_ref`, sorted; blank/oversized → 400 before any graph work,
  unknown → 404) and returns impacted SERVICE/API/DATABASE/TABLE entities
  with ordered evidence paths. Every step records FORWARD vs. REVERSE
  against the stored direction (`CALLS`/`DEPENDS_ON`/`CONTAINS`/`WRITES`/
  `EXPOSES` per D22) plus a per-step sentence: reverse steps explain
  dependence ("may affect"), forward steps list downstream dependencies as
  reachability, never confirmed breakage. Types without documented domain
  meaning (including manual rows such as `READS`) are included as
  structural reachability with a generic sentence — no invented semantics.
  One `@Transactional(readOnly=true)` snapshot (single `findAll` with the
  existing `@EntityGraph`, so no N+1); two additive fetch-joined finders
  (`findBySourceId`/`findByTargetId`) cover adjacency queries and are
  repository-tested. Caps (500 nodes / 2000 paths) truncate explicitly with
  a reason; per-path revisit guard always terminates revisits and reports
  `cycleEncountered` only for genuine directed cycles (one consistent
  traversal direction around the loop — the acyclic Phase 4 graph reports
  false; bidirectional backtracks and undirected-only loops such as
  diamonds or service—table—database triangles terminate silently). `snapshotObservedAt` is an observation timestamp, not
  a graph revision — no `createdAt`-max revision claim, no migration. No
  new dependencies, no risk/policy/execution/telemetry content.
- **Rationale:** The twin has had content but no reasoning since Phase 4;
  impact traversal is the smallest useful consumer: it exercises the real
  stored directions in both directions, establishes the evidence-path
  pattern later simulation/risk/policy work must reuse, and stays inside
  the existing trust model (read-only GET alongside the existing open twin
  GETs — no new mutating surface, hence no new auth infrastructure; the
  loopback bind note below still applies).
- **Effective localhost boundary (verified, not assumed):** the tracked
  example config carries `server.address=127.0.0.1`, but the live
  git-ignored `application.properties` does NOT inherit tracked files —
  verified 2026-10-10 (22-line live file, no `server.address` line; file
  contents otherwise uninspected, file never edited). Effective bind is
  therefore the Spring default (all interfaces) unless the operator sets
  `SERVER_ADDRESS=127.0.0.1` or adds the bind to the live file. Previous
  live verification used the environment override for exactly this reason.
  No claim is made that loopback is active by default; `DEVELOPMENT.md`
  §10–§11 documents the required runtime configuration. Broad
  authentication (Milestone 2C item 2) stays open and still gates any
  non-local exposure.
- **State:** Implemented and tested (37 new tests, 88 green total, build
  succeeds). Live verification pending (see `PROJECT_STATUS.md`).

## D24. Pull-based twin telemetry ingestion with strict allowlist — decided (2026-10-13)

- **Decision:** The second twin ingestion is a pull-based telemetry
  connector (`telemetry` package, control plane only): an explicit
  `POST /api/v1/twin/observations` trigger (default body `{}`, no
  caller-supplied URLs) pulls Actuator health and Prometheus exposition
  from `127.0.0.1` ports already recorded in twin metadata, validates
  every payload through dependency-free deterministic parsers, and
  annotates only the matched connector-managed `target-service:*`
  entities under a namespaced `runtime` metadata key
  (`managedBy: telemetry-connector`, `phase6-v1`). No schedulers,
  watchers, bulk log import, raw trace storage, new entity types, new
  migrations, or new dependencies.
- **Allowlist (pinned to the instrumented contract, verified against the
  target-service sources):** service health status (`UP`/`DOWN`/
  `OUT_OF_SERVICE`/`UNKNOWN`) plus exactly six tag-free business counter
  families — `checkout.success/failure` (order),
  `inventory.reservation.success/rejected` (inventory),
  `payment.authorization.success/declined` (payment); gateway-service has
  no business counters (health only). Absent families are reported as
  absent with a limitation — never inferred as zero. Unknown
  business-flavoured families, any labels on allowlisted counters,
  unbounded payloads, malformed values, and privacy-sensitive content
  (credentials, tokens, CUST-/SKU- identifiers) fail the whole run with
  400/409 and full rollback before any write.
- **Availability vs. validity:** unreachable services are missing
  evidence, not malformed evidence — recorded per-service as unavailable
  with limitations (a run where nothing was reachable returns zero
  updates plus explicit limitations, never a success claim). Health is
  never inferred from missing telemetry and outcomes never from unrelated
  counters. Trace/log presence is deliberately not collected: no existing
  source offers a privacy-safe presence signal without bulk import, so
  the receipt carries that limitation instead of fabricated evidence.
- **Idempotency/timestamp contract:** `observedAt` always advances and
  stored timestamps advance with it (staleness can never be hidden);
  `factsUpdated` counts services whose health/counters differed while
  `factsUnchanged` counts identical-fact re-observations. A repeat poll
  with identical counters reports zero updates and still proves a fresh
  observation.
- **Composition:** Phase 4 sync preserves the `runtime` key across
  re-syncs (excluded from its same-check, so idempotency counts hold);
  telemetry never writes relationships, so Phase 5 impact shape and
  reachability are unchanged. Same localhost boundary as D22
  (`LoopbackGuard`, 403 off loopback, no new auth). Broad
  authentication stays open and still gates any non-local exposure.
- **State:** Implemented and tested (41 new tests, dependency-free
  parsers). Live verification pending (see `PROJECT_STATUS.md`).

## D25. Deterministic ordinal change-risk assessment — decided (2026-10-14)

- **Decision:** The second `intelligence` module is a read-only
  deterministic risk classification over one Phase 5 impact traversal
  plus Phase 6 runtime metadata (`intelligence` package only):
  `GET /api/v1/twin/risk` reuses the impact service for traversal and
  validation (same `from` 1–10, `maxDepth` 1–10 default 6,
  `includeTypes`, 400/404 conventions) and classifies the impacted set in
  one `@Transactional(readOnly=true)` unit. No BFS duplication, no new
  dependencies, no migration, no writes.
- **Taxonomy `risk-v1` (documented in `RiskTaxonomy`, no numeric scores
  anywhere):** depth 1 starts `MEDIUM`, depth 2+ starts `LOW`; five or
  more independent evidence paths raise one step (calibrated so routine
  triangle/diamond alternatives on the Phase 4 graph do not inflate);
  reported `DOWN`/`OUT_OF_SERVICE` sets `HIGH`; cumulative failure
  families (`.failure`/`.rejected`/`.declined` above zero) raise one
  step as lifetime evidence, never rates or incident proof; zero and
  success counters never lower. A `SERVICE` without `runtime` metadata
  is `UNKNOWN`; metadata older than 30 minutes is stale (boundary-tested:
  exactly 30m is fresh) — stale severe signals are retained while flagged
  `stale` + `uncertain`, so missing or stale evidence never reads safer.
  Only services carry runtime, so only services receive
  health/counter/freshness reasoning; tables/databases/APIs are assessed
  structurally, stated in the response note.
- **Rationale:** Impact paths say what is reachable; runtime facts say
  what was last observed. The classification joins them with the smallest
  explainable rule set (ordinal levels, sorted reasons, explicit flags)
  so a later policy story has a reviewable input instead of a black-box
  score. Same trust posture as the impact GET (read-only, local-only,
  no new auth); the result is advisory and never authorization (T4/T5).
- **State:** Implemented and tested (36 new tests: 22 pure rule-unit +
  14 HTTP/API integration tests against the real 15/18 graph). Live
  verification pending (see `PROJECT_STATUS.md`).

## D26. Deterministic change simulation over a hypothetical graph — decided (2026-10-15)

- **Decision:** The third `intelligence` module is a read-only
  deterministic change simulation (`POST /api/v1/twin/simulations`):
  the request carries analysis roots (`from` 1–10 UUID/external-ref,
  `maxDepth` 1–10 default 6, `includeTypes` default
  SERVICE/API/DATABASE/TABLE — validated with the exact impact-query
  semantics via the shared `parseInputs`) plus a change proposal of
  `REMOVE_ENTITY`, `ADD_RELATIONSHIP`, and `REMOVE_RELATIONSHIP`
  operations. `REMOVE_ENTITY` deletes the entity and all incident
  relationships in the hypothetical graph only. The service compares
  baseline versus hypothetical impact and risk using identical roots and
  settings, reusing the existing traversal (`analyzeResolved`) and risk
  classification (`assessResolved`) — no algorithm duplication — and
  returns deterministic deltas, explicit truncation/uncertainty
  limitations, and `simulated: true` with the unmistakable
  `SIMULATED — NOT EXECUTED` advisory. Nothing is persisted: no
  proposals, entities, relationships, or runtime metadata are written
  (one `@Transactional(readOnly=true)` unit).
- **Validation order (fixed, first violation wins per phase):**
  root/maxDepth/includeTypes (400/404, impact semantics) →
  operation shape, 400 (`type`/`entity`/`source`/`target`/
  `relationshipType` presence) → identifier resolution, 404 →
  proposal conflicts, 409, in listed order: removing an analysis root;
  duplicate exact operations (canonical UUID comparison, so UUID and
  external-reference spellings of the same endpoint collide);
  contradictory add-plus-remove of one edge; adding an existing edge or
  removing a non-existent one; any relationship operation incident to an
  entity removed by the same proposal. Unknown operation types fail 400
  at JSON binding through the existing handler.
- **Structure:** pure units (`SimulationProposalValidator`,
  `HypotheticalGraph`, `SimulationDiff` — no repository, no Spring,
  unit-tested without a database) orchestrated by a thin
  `SimulationService`; every new file stays under 400 lines. Runtime
  telemetry is reused unchanged and labelled as such — no runtime
  behaviour is simulated.
- **Rationale:** Impact says what is reachable and risk says how the last
  observation reads; simulation answers the next question — what would
  reachability and risk look like if a proposed change were applied —
  without touching the twin. Canonical (UUID-level) comparison closes
  the alias hole where the same endpoint spelled two ways would slip
  past duplicate/contradiction checks. The fixed validation order keeps
  400/404/409 semantics reviewable and testable. Same trust posture as
  the impact/risk GETs (read-only, local-only, no new auth); the result
  is advisory and never authorization (T4/T5).
- **State:** Implemented and tested (34 pure unit tests + controller
  slice tests green without Docker; full HTTP integration suite added
  for Docker environments). Live verification pending (see
  `PROJECT_STATUS.md`).

## D27. Advisory policy evaluation over server-computed simulation — decided (2026-10-15)

- **Decision:** The first `policy` module is a read-only deterministic
  advisory evaluation (`POST /api/v1/twin/policy-evaluations`) over the
  existing simulation pipeline. The request body is the exact Phase 8
  proposal wire format (the shared `SimulationRequest` record — same
  fields, same validation, no alternate names): caller-supplied risk or
  delta content is never accepted. `PolicyService` re-runs the proposal
  through `SimulationService` (identical 400/404/409 semantics, identical
  roots/settings) and classifies the server-computed hypothetical result
  with the pure, immutable `policy-v1` ruleset — no traversal,
  classification, or validation duplication.
- **Taxonomy `policy-v1` (documented in `PolicyTaxonomy`, fixed order,
  every fired rule reported, final precedence `DENY > REVIEW >
  ALLOW`):** `HIGH_RISK_REMAINS` (any HIGH in after-scope → advisory
  DENY); `RESULT_TRUNCATED` (either scope truncated → advisory DENY,
  fail-closed on partial evidence); `RISK_INCREASED` (newly impacted
  risks appear, or a level moves upward with `LOW < MEDIUM < HIGH`
  including any arrival at HIGH; UNKNOWN moves not reaching HIGH are
  uncertainty, not increase → REVIEW); `UNKNOWN_RISK_PRESENT` (any
  UNKNOWN in after-scope → REVIEW, missing evidence is not safety);
  `EVIDENCE_UNCERTAIN` (any uncertain/stale after-scope evidence →
  REVIEW). An ALLOW outcome is still advisory and never an authorization
  grant; missing evidence, exceptions, invalid proposals, and incomplete
  analysis never silently produce ALLOW. The taxonomy version travels in
  the response.
- **Response:** advisory `decision`, `policyVersion`, unmistakable
  `ADVISORY POLICY EVALUATION — NOT AN AUTHORIZATION; NOT EXECUTED`
  wording, sorted fired rules with stable codes, a bounded simulation
  summary (graph counts, delta, limitations — not full scopes), and a
  note stating nothing was authorized, approved, executed, or persisted.
- **Rationale:** Risk says how the last observation reads and simulation
  says what would change; policy answers whether the hypothetical
  outcome is affirmable under explicit, reviewable rules — without
  touching the twin and without granting anything. Recomputing
  server-side closes the untrusted-evidence hole; immutable code rules
  (no persistence, no config surface) keep the evaluated policy exactly
  the shipped policy. Same trust posture as the intelligence endpoints
  (read-only, local-only, no new auth); advisory and never authorization
  (T4/T5), with T6 approval and T7 audit records still deferred.
- **State:** Implemented and tested (pure rule-unit + controller slice
  green without Docker; HTTP integration suite added for Docker
  environments). Live verification pending (see `PROJECT_STATUS.md`).

## D28. Control-plane JWT authentication and role authorization — decided (2026-10-15)

- **Decision:** The control plane is an OAuth2 resource server with JWT
  bearer tokens only (`spring-boot-starter-security` +
  `spring-boot-starter-oauth2-resource-server`; no Basic auth, no local
  password database, no home-grown tokens, no invented production IdP).
  Configuration lives under `systivex.security.jwt.*` (`enabled`,
  `issuer-uri`, `jwk-set-uri`, `accepted-audience`, `role-claim`,
  `clock-skew-seconds`, `allowed-roles`) with defaults `roles` /
  `systivex` / 60s skew / `TWIN_READ,TWIN_ANALYZE,TWIN_OPERATOR,TWIN_ADMIN`.
- **Fail-closed contract:** the trusted `issuer-uri` (HTTP(S) URL) is
  mandatory whenever JWT security is enabled; `enabled=false` is rejected
  at startup, so no security-off fallback exists. A custom `jwk-set-uri`
  only overrides key discovery — issuer, audience, signature, expiry, and
  `nbf` are always validated (with clock skew). Missing/invalid
  configuration fails fast instead of serving an unauthenticated API.
- **Role mapping (allowlist-only):** only `allowed-roles` values from the
  configured claim become `ROLE_*` authorities; malformed claims grant
  nothing and unknown values never grant access. `TWIN_ADMIN` inherits
  `TWIN_READ`, `TWIN_ANALYZE`, and `TWIN_OPERATOR`. Authenticated without
  the required role is 403; missing/invalid bearer credentials are 401
  with fixed safe messages (never tokens, headers, secrets, or claims).
- **Endpoint policy:** twin/impact/risk GETs need `TWIN_READ`; twin POSTs
  need `TWIN_ADMIN`; simulations/policy-evaluations need `TWIN_ANALYZE`;
  sync and observations need `TWIN_OPERATOR` with the existing
  `LoopbackGuard` still authoritative (a valid operator token never
  bypasses it). `GET /actuator/health` stays anonymous with details hidden
  (`show-details=never`); `/actuator/prometheus` needs `TWIN_READ`
  (monitoring access policy: read role only); all other actuator paths and
  everything else are denied. Stateless sessions, CSRF disabled for this
  token-only API, no CORS rules.
- **Out of scope:** approvals, execution endpoints, audit-ledger
  persistence, migrations, and target-service authentication are
  explicitly not introduced here. Simulation/policy stays read-only and
  advisory — an advisory ALLOW never becomes authorization to execute.
- **Verification:** automated JWT tests use locally generated test signing
  keys only (never production credentials). Live authentication against a
  genuine identity provider is NOT verified in this phase — no trusted
  issuer is configured in this environment — and must not be claimed.
- **Rationale:** Milestone 2C item 2 (broad authentication) has been open
  since Phase 4 with only a loopback bind + guard as a development
  boundary. JWT resource-server validation closes that gap for the control
  plane with the smallest reviewable mechanism, while target-service APIs
  stay local-only and unchanged.
- **State:** Implemented and tested (see `PROJECT_STATUS.md` Phase 10).
  Live IdP verification pending a configured trusted issuer.

## D29. Governed change proposals and tamper-evident audit — decided (2026-10-15)

- **Decision:** Phase 11 turns an investigated hypothetical change into a
  governed, auditable proposal with no execution. A new `proposal` package
  persists immutable proposal revisions and a hash-chained append-only
  audit log (Flyway `V2`, two tables + chain-head row, no new
  dependencies). Lifecycle is `DRAFT → ASSESSED → AWAITING_APPROVAL →
  APPROVED | REJECTED`, with `WITHDRAWN` (proposer cancels),
  `EXPIRED` (TTL lapsed), and `SUPERSEDED` (amendment forks a new
  revision) as terminal states; terminal revisions accept no further
  transitions (409).
- **Roles and separation of duties:** a distinct `TWIN_APPROVER` role is
  added to the `allowed-roles` allowlist. `TWIN_ADMIN` does **not**
  inherit it — a trusted issuer must explicitly grant it, so approval
  power is never ambient. Propose/assess/submit need `TWIN_ANALYZE`;
  approve/reject need `TWIN_APPROVER`; reads and audit verification need
  `TWIN_READ`. Separation of duties is a hard 403 on the `(issuer,
  subject)` identity pair: the approver must differ from the proposer on
  every revision, including for identities holding both roles. Approval
  and rejection both require a written reason (≤1024 chars).
- **Assessment binding and staleness:** assessment recomputes
  simulation + policy server-side from caller operations only (the Phase
  9 trust boundary is unchanged) and stores the canonical operations, a
  SHA-256 `operationsHash`, the bounded assessment summary
  (`policy-v1` decision + fired rules, `risk-v1` taxonomy +
  `snapshotObservedAt`), and a SHA-256 `twinFingerprint`. A policy
  `DENY` is never approvable in v1 — no override, no break-glass path.
  Materially stale assessments are rejected before approval (409):
  structural fingerprint mismatch, risk-level escalation or new
  staleness on recompute, or lapsed TTL (7-day default, 30-day hard
  maximum, lazy expiration only — no scheduler).
- **Revision model (issue A, resolved):** business revisions and JPA
  concurrency are separate fields. Each amendment inserts a new immutable
  `proposal_revision` row (`proposal_key` + 1-based `revision_number`,
  prior row → `SUPERSEDED` in the same transaction); the JPA `@Version`
  column (`jpa_version`) exists only for optimistic locking and is never
  serialized, hashed, or exposed. Every approval/audit event references
  `(proposal_key, revision_number, operationsHash, assessmentHash)`
  exactly. Prior revisions stay inspectable via
  `GET /proposals/{key}/revisions[/{n}]`.
- **Snapshot consistency (issue B, resolved):** assessment loads the
  twin entity/edge lists once (read-only `REPEATABLE_READ`) and computes
  simulation, policy, risk, and fingerprint over those exact in-memory
  lists, so fingerprint and assessment are coherent by construction. The
  fingerprint is content-derived — sorted entity id/type/name/refs/
  environment plus structural metadata **minus** the `runtime` subtree,
  plus sorted relationship keys — never `updated_at`. Content hashing
  (not writer instrumentation) is what covers every write path: CRUD
  inserts, sync structural updates, and relationship inserts all change
  content, while telemetry polls touch only `metadata.runtime` (and
  `updated_at`, which `entities.save` advances on every run) and therefore
  never invalidate an assessment. Risk freshness is rechecked separately
  at approval: recompute risk over current rows for the stored
  roots/settings and reject on any level increase, new `UNKNOWN`, new
  staleness, or runtime evidence older than the 30-minute taxonomy
  threshold. Residual accepted risks: change-then-revert (ABA) content
  equality, and a millisecond check-then-act window inside the approval
  transaction (row-locked, human-scale staleness dominates).
- **Audit integrity:** `audit_event` rows carry actor, action, outcome,
  correlation ID, timestamp, `(proposal_key, revision_number, hashes)`,
  and a SHA-256 `event_hash` chained via `prev_hash` against a
  `SELECT … FOR UPDATE`-guarded `audit_chain_head` row. Every lifecycle
  mutation and its audit event commit in one transaction (audit failure
  rolls back the mutation). There is no audit deletion and no automatic
  repair. The chain is tamper-**evident**, not tamper-proof: a database
  superuser can rewrite records, and the `verify` endpoint exists to
  detect exactly that (`OK` vs `BROKEN_AT_SEQ`).
- **Out of scope:** execution endpoints, controlled dispatch, approver
  quorum > 1, DENY override, break-glass, schedulers, brokers, workflow
  engines, WORM/offline export (runbook-only), and target-service
  authentication are explicitly not introduced here. `APPROVED` is a
  terminal record — acting on it stays a human step outside the system.
- **Verification:** migration-compat, transition-matrix, concurrency
  (parallel approves → exactly one `APPROVED`), role×endpoint matrix,
  SoD, expiry with injectable clock, chain-tamper detection, and
  transaction-rollback tests (see `PROJECT_STATUS.md` Phase 11). No live
  IdP or live Prometheus verification is claimed.
- **Rationale:** closes the governance gap between advisory investigation
  (Phases 5–9) and any future controlled execution, using the smallest
  reviewable persistence + cryptography increment while keeping the
  LLM permanently out of authorization and execution paths.
- **State:** Slices S1–S5 implemented with green tests (see
  `PROJECT_STATUS.md` Phase 11); expiry handling (S6) pending —
  nothing in S6 may be claimed as built until its slice lands.

## D30. Controlled-execution design (no implementation) — decided (2026-10-16)

- **Decision:** Phase 11 (S1–S6) ends at governed authorization: an
  `APPROVED` revision is a terminal permission record, and acting on it
  stays a human step outside the system (D29, unchanged). This decision
  records the binding constraints any future controlled-execution work
  must satisfy. It authorizes no code, no endpoints, no migration, no
  dependencies, and no target-service behavior change — nothing in it
  may be claimed as built until an implementation slice lands with
  green tests.
- **A. What execution means (verified against the actual code):**
  the simulation operations (`REMOVE_ENTITY`, `ADD_RELATIONSHIP`,
  `REMOVE_RELATIONSHIP` over UUID/external-ref identifiers, applied to
  the hypothetical graph only) are reachability-model edits, not
  actuator commands. The target services expose business APIs only
  (`POST /api/v1/checkout`, `/api/v1/orders`,
  `POST /internal/v1/inventory/reserve`,
  `POST /internal/v1/payments/authorize`) — none creates, deletes, or
  rewires services, databases, tables, APIs, or
  `CALLS`/`DEPENDS_ON`/`CONTAINS`/`WRITES`/`EXPOSES` relationships, so
  no simulation operation has a meaningful or safe real-world actuator
  equivalent today. The only executable class a first implementation
  may consider is twin-record changes through the existing twin CRUD
  semantics (entity/relationship rows, `TWIN_ADMIN`-owned); anything
  touching target services stays simulation-only until a per-action
  actuator contract exists. Before any execution code, an explicit
  operation-to-actuator mapping table is required (operation type →
  actuator, parameters, idempotency-key source), each entry allowlisted
  like the Phase 6 telemetry families; unmapped operations refuse,
  never improvise. Whether twin-record-only mutation constitutes
  meaningful controlled execution is explicitly unresolved: it changes
  the model without changing the world, so the implementation slice
  must justify what outcome telemetry would prove for it — otherwise
  execution has no meaningful first target and stays simulation-only.
- **B. Authorization and separation of duties:** a dedicated
  `TWIN_EXECUTOR` role, explicitly granted by the trusted issuer and
  never inherited from `TWIN_ADMIN`, `TWIN_APPROVER`, or any other
  role (same pattern as S5). The executor identity must differ from
  both the proposer and the approver, comparing `(issuer, subject)`
  together; holding several roles never exempts an identity from
  either comparison. Human approval stays independent of execution
  authority: `TWIN_APPROVER` can never dispatch, `TWIN_EXECUTOR`
  can never approve. Endpoint paths, default-deny preservation, and
  fixed error bodies follow the S5 conventions when the
  implementation slice arrives.
- **C. Trigger and lifecycle (proposed, not built):** the first
  implementation must use an explicit human-triggered execution
  request carrying a caller-supplied idempotency key; no scheduler
  and no autonomous execution (D29's no-scheduler rule stands).
  Proposed duplicate/concurrent/retry semantics: identical idempotency
  keys return the recorded outcome without re-dispatch; concurrent
  requests serialize on the revision's optimistic lock with exactly
  one terminal outcome; retries are allowed only from `FAILED` or
  `UNVERIFIED` after full revalidation, with a bounded attempt count;
  partial failure marks the revision `FAILED` unless every operation
  verifies; uncertain outcomes mark `UNVERIFIED`, never success. A
  previously approved proposal whose fingerprint, risk, policy, or
  evidence changed since approval must be refused and re-approved —
  never silently reassessed and executed in one step. If implementing
  this requires `APPROVED` to gain an outgoing transition, that
  changes D29's terminal-state list and must be recorded as an
  explicit supersession with rationale — it is proposed here, not
  decided.
- **D. Policy and safety gate:** immediately before any dispatch, the
  implementation must revalidate approval status, hash integrity,
  policy (recomputed server-side; `DENY` remains non-overridable),
  structural fingerprint, risk/evidence freshness, and TTL — the S5
  recheck, repeated at dispatch time, not cached from approval.
  Explicitly excluded: break-glass, quorum expansion, unrestricted
  shell/SQL, arbitrary URL dispatch, and LLM-directed actuator
  access (see `THREAT_MODEL.md` T1/T4 and §4). Least-privilege
  target-service authentication (per-action, short-lived
  credentials, never logged or embedded in audit bodies),
  short timeouts, idempotency keys, blast-radius limits (single
  revision scope, no fan-out beyond the assessed impact), and
  fail-closed containment are requirements on the future
  implementation, not claims about today.
- **E. Audit and outcome proof:** execution needs its own audited
  lifecycle (proposed: request, authorization, dispatch, result,
  failure, verification — exact action set to be fixed by the
  implementation slice), each event carrying actor, key/revision,
  hashes, and reason in the existing chain model, committed
  atomically with its state change; retries append new events and
  never rewrite history. Schema changes, if any, arrive through a
  future Flyway migration under the existing validate-only regime —
  no migration is created here. The evidence contract is per
  operation: intended change linked to observable outcome. Current
  telemetry (health plus six aggregate lifetime counter families
  with `observedAt` freshness) may serve only where it genuinely
  fits; aggregate counters cannot prove a graph edit, and any case
  without an operation-specific verifier must return `UNKNOWN` or
  `UNVERIFIED`, never success. Local state and its audit event share
  one database transaction, but external dispatch is never part of it:
  the design requires persisting a durable execution intent first,
  dispatching, then appending result and verification events keyed by
  the idempotency key. A crash between intent and result reconciles
  through re-observation (bounded retries, then `UNVERIFIED`);
  timeouts and missing verification are `UNVERIFIED`, never success.
  A transactional outbox may be considered by the implementation slice
  only if the dispatch channel justifies it — it is not mandated here.
  The chain stays tamper-evident
  (verify-to-detect, never repair, never tamper-proof).
- **Assumptions:** one trusted issuer; human operators behind every
  trigger; local-machine boundary until the deferred authentication
  story lands (`ARCHITECTURE.md` end). Unresolved environmental
  prerequisites (not code): a configured trusted issuer, a
  target-service authentication design, secret management, the
  per-action actuator contracts, and operation-specific telemetry
  verifiers.
- **Rejected alternatives:** autonomous/scheduled execution
  (violates human-trigger and D29); generic shell/SQL/URL dispatch
  (violates least privilege and T4); LLM-directed actuation
  (violates T1/T4); reusing `TWIN_ADMIN`/`TWIN_APPROVER` for
  dispatch (violates least privilege and approval independence);
  break-glass and `DENY` override (violate D29 and the anti-goals);
  approver quorum above one (out of scope).
- **Rationale:** preserves the governing principle with the last two
  clauses still human-gated — the model proposes, the system
  verifies, policy authorizes, and only an explicitly authorized
  human-triggered tool executes, with telemetry proving the outcome.
  Recording the constraints before any code keeps execution from
  arriving as an unreviewed side effect of a "natural next step".
- **State:** Designed and recorded (see `PROJECT_STATUS.md`
  Phase 12 S0). Implementation pending — nothing in this decision
  may be claimed as built until its slice lands with green tests.
  The retry/recovery wording in §C is corrected by D31 (2026-10-17),
  which replaces the bounded-retry-after-revalidation proposal with an
  explicit uncertain-outcome rule; every other §C constraint stands.

## D31. Phase 12 S1 — Execution feasibility gate (documentation/design only) — decided (2026-10-17)

- **Decision:** Phase 12 S1 is a documentation/design milestone, not an
  implementation. Systivex remains **simulation/approval-only**: D29 is
  preserved unchanged and `APPROVED` remains the terminal permission
  record. The three simulation operations (`REMOVE_ENTITY`,
  `ADD_RELATIONSHIP`, `REMOVE_RELATIONSHIP`) stay unmapped and refused
  for external execution. This milestone adds no execution endpoint, no
  migration, no `TWIN_EXECUTOR` role wiring, no actuator, and no
  target-service behaviour change, and it does **not** supersede D29.
  Any future change to the terminal `APPROVED` state requires its own
  explicit, dated decision first.
- **Why no current operation qualifies (verified against the actual
  code):** every simulation operation is a hypothetical-graph edit
  (`intelligence/SimulationOperation.java`) applied to in-memory working
  copies only. `REMOVE_ENTITY` and `REMOVE_RELATIONSHIP` have no
  actuator at all — twin CRUD is create/get/list only, with no delete and
  no update (`twin/api/TwinController.java`). `ADD_RELATIONSHIP` has a
  write path (`POST /api/v1/twin/relationships`) but it changes
  Systivex's own model, not the target environment, and its only
  "outcome" is a self-referential row read — it cannot demonstrate a
  world change. The four target services expose business POSTs
  (checkout / orders / reserve / authorize) plus a read-only Actuator
  `health,prometheus` surface; none creates, deletes, or rewires
  services, databases, tables, APIs, or graph edges. No operation
  therefore maps to a meaningful, independently verifiable
  target-environment change.
- **Operation-to-actuator mapping (design table only — no runtime
  allowlist exists and none was added):**

  | Simulation operation | Candidate actuator | Real-world effect | Verdict |
  |---|---|---|---|
  | `REMOVE_ENTITY` | none | none (model only) | refused |
  | `ADD_RELATIONSHIP` | `POST /api/v1/twin/relationships` (existing) | none (model only) | refused for execution; twin-model write only |
  | `REMOVE_RELATIONSHIP` | none | none (model only) | refused |

  The table is recorded in documentation because no real, approved
  actuator was identified. An actuator is never invented to make the
  milestone produce runtime code.
- **Feasibility gate (required before any execution implementation is
  approved):**

  1. a specific, meaningful operation with an explicit typed request and
     an allowlisted actuator;
  2. a real target API or actuator contract with documented
     authentication and least privilege;
  3. an operation-specific, independent verifier that can prove the
     intended postcondition;
  4. safe duplicate handling and crash/timeout/partial-effect recovery;
  5. D30-compliant identity separation (`TWIN_EXECUTOR` distinct from
     proposer and approver), human-triggered authorization, dispatch-time
     policy/fingerprint/risk/expiry revalidation, and a non-overridable
     `DENY`;
  6. a durable intent/result/audit design that never claims external
     atomicity and never treats missing evidence as success;
  7. an explicit decision approving any necessary change to D29.

  If no operation satisfies the gate, execution remains unimplemented —
  stated plainly rather than filled with speculative scaffolding (no
  unused `OperationToActuator` refusals, no consumerless execution state
  machine).
- **Corrected retry/recovery contract (binding on any future
  implementation; replaces the retry wording proposed in D30 §C):**
  outcomes are explicitly distinguished as (a) an **authoritative
  rejection** with evidence that no effect occurred; (b) a **confirmed
  partial effect**; and (c) an **uncertain outcome** (e.g. a timeout
  after dispatch, or a crash before the result is recorded). An
  operation whose prior effect is uncertain is **never automatically
  redispatched**. A retry is permitted only when no prior effect is
  authoritatively established, or when a documented downstream
  idempotency contract safely deduplicates the same logical operation; a
  bounded retry count alone is not a safety guarantee. Partial effects
  require a separately approved recovery or compensation contract —
  retrying or compensating is never assumed safe. If the outcome cannot
  be resolved it stays `UNVERIFIED` and requires reconciliation. Local
  transaction/audit atomicity (the state change and its audit event
  share one database transaction) is kept explicitly separate from
  external side effects (dispatch is never part of that transaction).
- **Decisions still requiring approval:** whether to pursue any execution
  work at all by identifying a qualifying operation; the concrete
  operation-to-actuator mapping once such an actuator exists; the
  actuator's authentication and least-privilege design; the
  recovery/compensation contract for partial effects; any schema change
  for durable intent/result/verification records; and any supersession
  of D29's terminal-state list.
- **Rejected alternatives:** implementing a model-only twin mutation and
  labelling it execution (it changes the model, not the world — D30
  leaves this unresolved and this decision resolves it as
  not-execution); speculative `OperationToActuator`/state-machine
  scaffolding with no consumer; inventing a target actuator to justify
  runtime code; superseding D29 without a separate decision; and
  carrying D30's bounded-retry-after-revalidation wording forward
  unchanged (unsafe for uncertain outcomes).
- **State:** Recorded and decided (documentation/design only; no code,
  migration, dependency, endpoint, role, or target-service change). See
  `PROJECT_STATUS.md` Phase 12 S1. Execution remains unimplemented —
  nothing in this decision may be claimed as built.

## D32. Apache-2.0 licensing and open-source contributor readiness — decided (2026-10-17)

- **Decision:** Systivex is released under the **Apache License, Version 2.0** (root
  `LICENSE`, unmodified standard text; copyright notice 2026 Soubhagya Kumar Behera).
  Phase 13 S0 adds the contributor-facing material needed for external participation:
  `README.md` accuracy/navigation cleanup, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`
  (Contributor Covenant 2.1), `SECURITY.md`, GitHub issue/PR templates under `.github/`,
  and a backend GitHub Actions workflow that runs the existing verification command.
- **Rationale:** Apache-2.0 is permissive, well understood, and includes an explicit
  patent grant — appropriate for a platform intended to be read, run, and extended by
  others. Contributor readiness is documentation and automation only: it lowers the cost
  of a first contribution without changing what the system does.
- **CI scope:** the workflow (`.github/workflows/backend-ci.yml`) runs on pull requests and
  pushes to `main`, uses the repository's Maven wrapper on Java 21, and executes
  `./mvnw -B clean test` in `backend/` — the same suite used locally (Testcontainers
  PostgreSQL via the runner's Docker daemon). It grants only `contents: read`, sets a
  timeout, and caches Maven dependencies. No frontend job exists because no frontend
  exists.
- **Boundaries:** no business logic, database schema, authentication behavior,
  authorization rules, policy enforcement, or security controls are changed; no
  application dependencies or infrastructure are added for contributor readiness; no
  existing test is modified to pass; the frontend is **not** started. D29–D31 are
  unchanged: controlled execution remains unimplemented and gated, `APPROVED` stays
  terminal, and no actuator or executor role is introduced.
- **Licensing note:** dependencies retain their own licenses (managed transitively via
  Spring Boot); the repository contains no vendored or separately licensed third-party
  source beyond the standard Apache-licensed Maven wrapper. This record is a licensing
  choice, not legal advice.
- **State:** Recorded and decided (documentation, community files, and CI only). See
  `PROJECT_STATUS.md` Phase 13 S0. Nothing here is runtime behavior.

## D33. Phase 13 S1 — Read-only operator console (decided) — 2026-10-18

- **Decision:** Phase 13 S1 authorizes a **scoped, read-only operator
  console** — a **React + Vite + JavaScript** frontend (**TypeScript is not
  authorized**). S1 is limited to the **read-only System Twin explorer**:
  entity/relationship browsing, entity details where supported, a dependency
  graph, and accessible loading/empty/error states. S1 may call **only
  existing `TWIN_READ` GET endpoints** (`/api/v1/twin/entities[/{id}]` and
  `/api/v1/twin/relationships[/{id}]`; impact/risk and proposal/audit reads
  belong to later slices). It adds **no** sync, observation, twin-write, or
  proposal-lifecycle mutation capability. This decision authorizes **no
  runtime code**: no frontend files exist, and this record is the checkpoint
  that permits the first UI slice.
- **Frontend convention:** React with Vite in **JavaScript — not TypeScript**
  — consistent with `CONTRIBUTING.md`. Introducing TypeScript requires its own
  future decision.
- **Authentication (mandatory, unchanged):** the control plane remains an
  OAuth2 JWT resource server (D28). The proposed **manually supplied bearer
  token** is a **local-development-only bridge**: held **in memory only**,
  cleared on reload, and **never logged, rendered, placed in a URL, or
  persisted** in `localStorage`, `sessionStorage`, `IndexedDB`, cookies, or
  any other storage. There is **no bundled identity provider, no fake login,
  no invented issuer, no placeholder production credentials, and no
  security-off mode** (`enabled=false` is still rejected at startup). The
  trusted issuer and client details remain **missing**, so **live
  authentication verification stays blocked** until legitimate configuration
  is supplied; the gap is documented, not fabricated.
- **Serving and CORS:** S1 uses the **Vite development proxy** (same-origin
  during development) and adds **no backend CORS rules**. Serving the console
  in a shared or production environment is **explicitly deferred** to a
  separately reviewed decision (the backend has `anyRequest().denyAll()` and
  no CORS today — `SecurityConfig`). The UI is **local-development-only**
  until the serving, identity, deployment, and security requirements for a
  shared environment are reviewed.
- **Scope boundaries:** S1 introduces **no new backend endpoints, no database
  migration, no backend dependencies, no executor role, and no actuator**.
  D29–D31 remain unchanged: controlled execution stays unimplemented and
  gated, `APPROVED` stays terminal, and **no operation is executed**.
  Simulation and policy evaluation (later slices) remain **hypothetical,
  non-persistent, and advisory**.
- **Slice gating:** subsequent UI slices — impact/risk, simulation/policy,
  and governance/audit — each require their **own scope and verification
  checkpoint** before implementation; nothing beyond S1 is authorized here.
- **Rationale:** the backend already exposes deterministic, JWT-guarded read
  analysis and governed records, but no human surface. A bounded read-only
  console makes that state inspectable without adding mutation, execution, or
  new trust boundaries. Starting with the System Twin explorer exercises the
  full stack (auth handling, API client, routing, graph rendering,
  loading/empty/error states) against the safest `TWIN_READ` GETs and surfaces
  the CORS/serving and authentication questions before higher-risk views.
- **Rejected alternatives:** TypeScript; adding backend CORS rules or
  same-origin static hosting in S1 (an unreviewed security-surface change); a
  bundled or fake identity provider, or invented issuer/client configuration;
  persisting tokens in any browser storage; authentication-off or placeholder
  credentials; including sync/observation/twin-write/proposal-mutation
  actions; promising every view in the first release; and any execution or
  `TWIN_EXECUTOR` work (D29–D31 unchanged).
- **Dependencies / blockers:** a legitimate trusted issuer and registered
  client details (issuer discovery URL, client id, redirect URIs) are required
  for a real browser login (OIDC Authorization Code + PKCE) and are **not
  available** — the manual in-memory token bridge is the interim local-only
  path. When code lands, a frontend CI job and `README` / `ARCHITECTURE` /
  `CONTRIBUTING` / `PROJECT_STATUS` updates are the documentation
  obligations. `CONTRIBUTING.md` previously stated that no decision
  authorized a frontend; this decision supersedes that statement.
- **Verification expectations:** S1 lands only with green checks for the API
  client (role handling: 401/403 with the fixed safe messages), accessible
  states (loading/empty/error), and a manual smoke test against a locally
  running control plane using a developer-supplied token; **no live
  identity-provider verification is claimed**. Token-handling behavior (in
  memory, no storage, no logging, never in URLs) is reviewed against
  `THREAT_MODEL.md`.
- **State:** Recorded and decided (documentation/decision only; **no frontend
  files exist**). See `PROJECT_STATUS.md` Phase 13 S1. Nothing here is runtime
  behavior until a slice lands with verification.

## D34. Local-development Keycloak issuer for D28 verification — decided (2026-10-26)

- **Status:** Decided and adopted 2026-10-26. Installation, realm creation, and
  configuration are authorized only within the local-development scope below.
- **Decision:** Authorize a standalone, operator-installed Keycloak 26.8.x
  instance as a **local-development-only** OIDC issuer for verifying the
  existing D28 JWT resource-server design. It binds strictly to
  `127.0.0.1:8180`, uses the dev profile and local dev-file storage, and uses
  issuer `http://127.0.0.1:8180/realms/systivex-dev`. It is external to the
  repository: no Docker/Compose, no bundled IdP, no application dependency, no
  repo-hosted realm export, and no change to `SecurityConfig`.
- **Compatibility with D28/D33:** D28 remains the authentication authority:
  issuer, audience `systivex`, signature, expiry, `nbf`, and allowlist-only role
  mapping stay enforced; there is no security-off mode. D33's manual in-memory
  bearer-token bridge and no-application-login rule remain. Keycloak is not
  bundled, is not a fake IdP, and is not a production identity decision. This
  record narrowly supplements D33's current "no identity provider" state for
  local verification only.
- **Realm/client configuration:** realm `systivex-dev`; realm roles
  `TWIN_READ`, `TWIN_ANALYZE`, `TWIN_OPERATOR`, `TWIN_ADMIN`, `TWIN_APPROVER`;
  one public client with no client secret, Direct Access Grants disabled, and
  Device Authorization Grant or Authorization Code + PKCE enabled. An Audience
  mapper adds `systivex`; a User Realm Role mapper emits the top-level `roles`
  array. `TWIN_ADMIN` inheritance remains backend-owned; `TWIN_APPROVER` is
  granted explicitly. Dev users are separate, disposable identities.
- **Configuration and secrets:** the backend receives only
  `SYSTIVEX_ISSUER_URI` via the environment; `SYSTIVEX_JWK_SET_URI` stays unset
  so real discovery is exercised. No passwords, client secrets, private keys,
  tokens, or realm exports are committed, logged, placed in chat, or stored in
  the repository. No default `admin/admin` account is used; temporary bootstrap
  admins are removed. Dev credentials live in an OS credential store or password
  manager.
- **Verification:** fetch discovery/JWKS and confirm the exact issuer; test a
  valid `TWIN_READ` token (200), missing/malformed token (401), insufficient
  role (403), expired token beyond the 60-second skew (401), tampered/wrong-key
  signature (401), wrong issuer via a second realm (401), and missing audience
  (401). Confirm `TWIN_ADMIN` inheritance and explicit `TWIN_APPROVER`
  behavior. Record results as local-dev verification only.
- **Out of scope:** production IdP selection, TLS/proxy deployment, external
  database, HA, backups, key rotation, MFA, identity proofing, account
  lifecycle, frontend login, CORS changes, backend code changes, database
  changes, and any production credential.
- **Rejected alternatives:** Docker/Compose or a bundled IdP; fake tokens or a
  security-off mode; a default admin account; committing realm exports or
  credentials; exposing Keycloak beyond loopback; treating local Keycloak as a
  production decision.
- **State:** Decided and adopted 2026-10-26. Local implementation and
  verification results are recorded in `PROJECT_STATUS.md`; this record does
  not authorize a production identity provider.

## D35. Phase 13 S2 — Impact & risk investigation UI — decided (2026-10-28)

- **Status:** Decided and adopted 2026-10-28. Authorizes implementation only
  within the S2 scope below and remains subject to the implementation
  checkpoints in this record. No S2 code is implemented by this record.
- **Decision:** Authorize only Phase 13 S2: a scoped, read-only impact/risk
  investigation view in the existing React + Vite + JavaScript console. S2
  selects one existing entity already loaded by S1 as the root and calls only
  the existing `GET /api/v1/twin/impact` and `GET /api/v1/twin/risk` endpoints
  with `from=<entity UUID>` and no other query parameters. The verified backend
  defaults apply: `maxDepth=6` and
  `includeTypes=SERVICE,API,DATABASE,TABLE`. S2 does not expose `maxDepth` or
  `includeTypes` controls, does not support multiple roots, and does not add an
  entity-detail CTA.
- **Scope:** Add a live-only "Impact & risk" navigation view; a searchable
  single-root picker over the S1 entity snapshot; explicit "Analyze" action; a
  separate `frontend/src/api/analysis.js`; an additive `BAD_REQUEST` error kind
  for HTTP 400; all-or-nothing loading of both endpoints; and accessible
  loading, empty, validation, authentication-error, retry, and malformed-
  response states. Display impacted entities, depth, `viaStart`, complete
  evidence paths with ordered `FORWARD`/`REVERSE` steps and explanations, risk
  level, reasons, `uncertain`, `stale`, taxonomy version, impact
  `truncated`/`truncationReason`/`cycleEncountered`/`limits`, both advisory
  notes, and separate impact and risk computation timestamps.
- **Non-goals:** No backend endpoints, DTO changes, database migrations, schema
  changes, backend dependencies, CORS changes, hosting changes, security
  weakening, authentication-off mode, or new identity-provider integration. No
  simulations, policy-evaluation POSTs, proposal mutations, sync, observations,
  twin writes, or execution. No token persistence in `localStorage`,
  `sessionStorage`, IndexedDB, cookies, URLs, logs, or rendered text. No
  TypeScript, new frontend dependency, graph renderer, export, URL state,
  synthetic analysis preview, risk-completeness invention, or multi-root/depth/
  type controls.
- **Authentication boundary:** The control plane remains the D28 OAuth2 JWT
  resource server. Both endpoints require `TWIN_READ`. The browser continues to
  use the D33 manually supplied bearer token held only in memory, cleared on
  reload. The token is attached only to local `/api` requests through the
  existing client and Vite proxy; no backend CORS rule is added. Live browser
  OIDC login remains unimplemented and unclaimed. D34's local Keycloak is not a
  dependency of S2; S2 adds no login flow or issuer/client configuration and
  does not require Keycloak to be running.
- **Endpoints and defaults:** Both requests are GET-only and are issued only
  after an explicit Analyze action. `from` is the selected entity UUID. The
  backend validates 1–10 starts, `maxDepth` default `6` with allowed `1..10`,
  and repeatable `includeTypes` defaulting to `SERVICE`, `API`, `DATABASE`, and
  `TABLE`; traversal still passes through types not reported by the default
  filter. Traversal caps are 500 nodes and 2,000 paths. Starts are not counted
  as impacted, and every impacted entity carries at least one evidence path.
- **Error behavior:** Add `ErrorKind.BAD_REQUEST` with a fixed safe HTTP 400
  message; raw response bodies, headers, tokens, and claims are never rendered.
  Preserve safe handling for 401/403/404/5xx/network errors and malformed 2xx
  payloads. HTTP 404 indicates the selected root may no longer exist; 400
  indicates an invalid analysis request. Impact and risk load all-or-nothing:
  if either request fails, no partial result is presented as a complete
  analysis. Retry repeats both requests.
- **Response semantics:** Do not merge the two observation timestamps.
  `ImpactResponse.snapshotObservedAt` and `RiskResponse.snapshotObservedAt` are
  separate analysis computation times produced by separate live calls; label
  them separately as impact and risk computed times. Do not call either a
  snapshot sync time, data freshness time, or "last updated" time. Show
  `entityCount` and `relationshipCount` as counts read by each analysis.
  Surface impact `truncated`, `truncationReason`, `cycleEncountered`, `limits`,
  and `note`, using "no truncation reported" rather than claiming completeness.
  Show risk level, sorted reasons, `uncertain`, `stale`, `taxonomyVersion`, and
  `note`; preserve backend severity order. Explicitly disclose that
  `RiskResponse` has no truncation or cycle fields, so S2 cannot verify risk
  completeness or risk traversal truncation and does not fabricate them.
- **Testing expectations:** API tests assert only the two approved GET paths,
  exact `from` query construction, Bearer headers only on `/api`, no request
  before an explicit action, no token persistence, and safe rejection of
  malformed payloads. View tests cover root selection, all-or-nothing loading,
  impacted entities, evidence paths and directions, risk levels/reasons/flags,
  separate timestamps, impact truncation/cycle/limits, the risk-completeness
  caveat, empty states, 400/401/403/404/5xx/network failures, retry, and preview
  exclusion. Existing S1 tests, `npm test`, `npm run build`, and
  `npm audit --audit-level=high` must pass. Manual smoke testing uses a
  developer-supplied `TWIN_READ` token against the local backend and verifies
  both network calls and the UI fields; no live OIDC verification is claimed.
- **Implementation checkpoints:** D35 must be explicitly adopted before code.
  The existing uncommitted D34 block and untracked root
  `application.properties` must be preserved exactly. Implementation is
  frontend-only, with no backend, database, dependency, security, or serving
  changes. The `BAD_REQUEST` addition must remain additive and must not weaken
  existing safe error handling. The slice is not complete until tests, build,
  audit, manual smoke, and documentation updates are reviewed. No commit or
  push occurs without explicit instruction.
- **Compatibility with D28/D33/D34:** D28 remains the authentication authority.
  D33 authorized only S1 and explicitly required impact/risk to have their own
  scope and verification checkpoint; D35 is that checkpoint and does not
  re-authorize or expand S1. D33's historical statements that no frontend files
  exist are stale after S1 and must not be silently rewritten; S1's implemented
  state is recorded in `PROJECT_STATUS.md`. D34 is local-development Keycloak
  verification only and does not authorize frontend login, CORS, or production
  identity; S2 does not use or alter it. No security boundary is weakened.
- **Rejected alternatives:** Exposing `maxDepth`/`includeTypes` or multiple
  roots in this slice; automatically analyzing on root selection; rendering
  partial results when one endpoint fails; adding synthetic analysis to preview;
  adding the entity-detail CTA now; inventing risk truncation/cycle data;
  changing the backend risk response; adding backend endpoints, migrations,
  CORS, or hosting; persisting tokens; adding TypeScript or a new dependency;
  and adding simulations, policy POSTs, proposal mutations, sync, observations,
  writes, or execution.
- **State:** Decided and adopted 2026-10-28; documentation/decision only. No
  runtime code is implemented by this record. S2 implementation remains subject
  to the checkpoints above and separate review.

## D36. Phase 13 S4 — Read-only Proposal Governance UI — decided and adopted (2026-11-01)

- **Status:** Decided and adopted 2026-11-01. Authorizes only the read-only
  Phase 13 S4 implementation within the scope below. No S4 code is implemented
  by this record.
- **Decision:** Authorize only Phase 13 S4: a scoped, read-only proposal
  governance view in the existing **React + Vite + JavaScript** console. S4
  reads governed proposal records and their per-proposal audit trail and adds no
  mutation, execution, or new trust boundary.
- **Approved endpoints (TWIN_READ GET only):**
  `GET /api/v1/twin/proposals?page=<n>&size=<n>`,
  `GET /api/v1/twin/proposals/{key}`,
  `GET /api/v1/twin/proposals/{key}/revisions`,
  `GET /api/v1/twin/proposals/{key}/revisions/{revisionNumber}`, and
  `GET /api/v1/twin/proposals/{key}/audit`. No other endpoints, methods, or
  query parameters are authorized.
- **Loading contract:** entering S4 loads only the first proposal page
  (`page=0&size=20`). Selecting a proposal loads only its latest revision
  (`GET /proposals/{key}`). Revision history and audit data load only on
  explicit navigation actions; selecting a revision uses
  `GET /revisions/{revisionNumber}`. No proposal detail, history, or audit is
  fetched before selection, and there is no prefetching, polling, or automatic
  refresh.
- **API limitations:** the proposal list supports only `page` and `size`
  (0-based page; default size 20; maximum 100; the backend clamps values).
  Revision history (`/revisions`) and per-proposal audit (`/audit`) are
  **currently unpaginated**. S4 must not invent pagination parameters, filters,
  sorting parameters, or backend endpoints, and must not imply that an
  unpaginated response is paged or complete beyond what the server returns.
- **Proposal list shape:** `ProposalListResponse` contains `items`
  (`ProposalResponse[]`, the latest revision of each proposal), `page` (int),
  `size` (int), and `total` (long).
- **ProposalResponse fields (actual contract):** `proposalKey` (UUID,
  non-null), `revisionNumber` (int, non-null), `status` (string, non-null:
  `DRAFT`, `ASSESSED`, `AWAITING_APPROVAL`, `APPROVED`, `REJECTED`,
  `WITHDRAWN`, `EXPIRED`, `SUPERSEDED`), `title` (string, non-null),
  `description` (string, **nullable**), `operations` (JSON document,
  non-null), `operationsHash` (string, non-null), `assessment` (JSON document,
  **nullable**), `assessmentHash` (string, **nullable**), `policyDecision`
  (string, **nullable**), `policyVersion` (string, **nullable**),
  `riskTaxonomy` (string, **nullable**), `twinFingerprint` (string,
  **nullable**), `snapshotObservedAt` (timestamp, **nullable**),
  `proposerIssuer` (string, non-null), `proposerSubject` (string, non-null),
  `approverIssuer` (string, **nullable**), `approverSubject` (string,
  **nullable**), `reason` (string, **nullable**), `expiresAt` (timestamp,
  **nullable**), `createdAt` (timestamp, non-null), and `updatedAt`
  (timestamp, non-null). The nullable assessment metadata is exactly
  `assessment`, `assessmentHash`, `policyDecision`, `policyVersion`,
  `riskTaxonomy`, `twinFingerprint`, and `snapshotObservedAt`; nullable identity
  and decision fields are `approverIssuer`, `approverSubject`, and `reason`.
  Nullable values are shown explicitly as not reported/set; they are never
  fabricated or inferred.
- **AuditEventResponse fields (actual contract):** `id` (UUID, non-null),
  `seq` (long, non-null), `occurredAt` (timestamp, non-null), `actorIssuer`
  (string, **nullable**), `actorSubject` (string, non-null), `action` (string,
  non-null: `CREATED`, `ASSESSED`, `SUBMITTED`, `APPROVED`, `REJECTED`,
  `WITHDRAWN`, `EXPIRED`, `SUPERSEDED`), `outcome` (string, non-null:
  `SUCCESS`, `FAILURE`), `proposalKey` (UUID, non-null), `revisionNumber`
  (int, non-null), `operationsHash` (string, **nullable**), `assessmentHash`
  (string, **nullable**), `reason` (string, **nullable**), `correlationId`
  (string, **nullable**), `prevHash` (string, non-null), and `eventHash`
  (string, non-null). Nullable audit fields are exactly `actorIssuer`,
  `operationsHash`, `assessmentHash`, `reason`, and `correlationId`.
- **Audit display boundary:** the per-proposal audit list is server-ordered
  evidence display only. S4 must not compute, verify, or claim cryptographic
  chain integrity from `prevHash`/`eventHash`, and must not present the list as
  verified. S3's `GET /api/v1/twin/audit/verify` remains the only chain
  verification view. Hashes are rendered as inert text, never HTML.
- **Presentation:** show exact server strings and timestamps without merging
  distinct values; render `operations` and `assessment` JSON as bounded,
  collapsible, inert text (no `dangerouslySetInnerHTML` or equivalent); render
  untrusted title/description/reason text as text; tolerate unknown
  forward-compatible status/action/outcome strings without crashing. Pagination
  uses only the returned `page`/`size`/`total`; history and audit render the
  returned arrays without client-invented server paging.
- **Live-only:** S4 is available only in live console mode and is excluded from
  the synthetic local preview. Token handling remains the existing D33
  in-memory bearer-token bridge: cleared on reload, never persisted in
  `localStorage`/`sessionStorage`/IndexedDB/cookies/URLs, never logged or
  rendered, and attached only to local `/api` requests through the existing
  client and Vite proxy. Live OIDC login and production hosting remain
  unimplemented and unclaimed.
- **Explicitly prohibited:** proposal creation, withdrawal, assessment,
  submission, amendment, approval, and rejection; execution, executor roles, or
  execution controls; backend source, endpoint, DTO, schema, migration,
  dependency, CORS, hosting, authentication, or database changes; new frontend
  dependencies; TypeScript; token persistence; raw HTML rendering; synthetic
  proposal preview; simulations; policy POSTs; sync; observations; twin writes;
  client-side audit verification; export; URL/query state for proposal data;
  and any prefetching or automatic analysis.
- **APPROVED semantics:** `APPROVED` remains terminal and authorizes no
  execution. S4 displays the state only and cannot advance, execute, or bypass
  the D30/D31 gates.
- **Error behavior:** 400/401/403/404/5xx/network/malformed payloads map to
  fixed safe messages; response bodies, headers, tokens, and claims are never
  echoed. An out-of-range proposal page shows an empty page with previous-page
  navigation; empty history/audit states are explicit.
- **Verification expectations:** API tests assert only the five approved GET
  paths and exact query construction, no mutation verbs, no request before
  selection, no detail fetch before selection, no token persistence, and safe
  malformed-payload rejection. View tests cover first-page loading, selection,
  latest revision, explicit history/audit navigation, revision selection,
  pagination, all listed fields, nullable values, inert JSON/long text, empty
  and error states, and live-only preview exclusion. Existing S1–S3 tests,
  `npm test`, `npm run build`, and `npm audit --audit-level=high` must pass.
  Manual smoke uses a developer-supplied `TWIN_READ` token and claims no live
  OIDC verification.
- **Implementation checkpoints:** D34 and D35 are preserved unchanged in this
  record. Implementation is frontend-only. The slice is complete only after
  tests, build, audit, manual smoke, and documentation updates are reviewed.
  No commit or push occurs without explicit instruction.
- **Compatibility:** D28 remains the authentication authority; D29 governance
  state is read-only here; D33 console conventions and in-memory token handling
  remain; D34 is not a dependency; D35 is unchanged; D30/D31 execution stays
  gated; S3's verifier remains separate.
- **Rejected alternatives:** any governance mutation or approval UI; execution
  or executor roles; new backend read/filter endpoints; invented pagination for
  unpaginated history/audit; unbounded list fetches; client-side audit
  verification; TypeScript; new dependencies; token persistence; raw HTML;
  synthetic proposal preview.
- **State:** Decided and adopted 2026-11-01; documentation/decision only. No S4
  runtime code is implemented by this record.

## D37 — Phase 13 S5: Hypothetical Simulation & Advisory Policy UI

- **Status:** Decided and adopted 2026-11-03.
- **Scope:** A new live-only **Simulation & Policy** frontend view over the
  existing Phase 8 and Phase 9 endpoints, plus additive frontend
  `ErrorKind.CONFLICT` handling for HTTP 409. No other runtime behavior is in
  scope.
- **Purpose:** Let an operator build a hypothetical change proposal from one
  loaded twin root, run a server-side simulation, and independently request an
  advisory policy evaluation. Both results are read-only evidence over
  server-computed data; neither authorizes, approves, or executes anything.
- **Approved endpoints (POST only):**
  `POST /api/v1/twin/simulations` and
  `POST /api/v1/twin/policy-evaluations`. No other endpoints, methods, query
  parameters, or alternate request shapes are authorized. Both requests use
  JSON `Content-Type` and the existing in-memory bearer token through the
  shared API client. The policy endpoint independently re-runs simulation
  server-side; it does not consume a previous simulation response.
- **Request contract:** `SimulationRequest` contains `from` (required list of
  1-10 UUIDs or external references), optional `maxDepth` (default 6, range
  1-10), optional `includeTypes` (default `SERVICE`, `API`, `DATABASE`,
  `TABLE`), and optional `operations`. Canonical camelCase wire fields are
  used. A missing, null, or empty `operations` list is a baseline-only
  request and must be sent as `operations: []`. Only `REMOVE_ENTITY`,
  `ADD_RELATIONSHIP`, and `REMOVE_RELATIONSHIP` are supported. `REMOVE_ENTITY`
  requires `entity`; relationship operations require `source`, `target`, and
  `relationshipType`. Valid relationship types are exactly `CALLS`,
  `DEPENDS_ON`, `READS`, `WRITES`, `DEPLOYED_AS`, `EXPOSES`, `CONTAINS`, and
  `CONFIGURES`.
- **Operation builder limit:** The S5 builder allows at most 10 operations.
  This is a frontend usability limit only, not a backend validation or
  security limit. The UI must say so and must never present the cap as an API
  restriction or as protection.
- **Simulation response contract:** `SimulationResponse` contains `simulated`
  (always true), the exact advisory `SIMULATED — NOT EXECUTED`, `settings`
  (`maxDepth`, `includeTypes`, `rootCount`), `before` and `after` scopes, the
  seven-value `graph` summary, the `delta`, and `limitations`. Each scope
  contains a complete `impact` response and a complete `risk` response.
  Impact fields include starts, impacted entities, evidence paths and steps,
  traversal counts, truncation cause, cycle state, limits, snapshot timestamp,
  twin counts, and note. Risk fields include starts, risk entries, taxonomy
  version, snapshot timestamp, twin counts, and note; each risk entry includes
  id, type, name, external reference, depth, via-start, level, reasons,
  uncertainty, and stale state. The four impact/risk snapshot timestamps are
  distinct values and must never be merged.
- **Graph summary semantics:** `baselineEntityCount`,
  `hypotheticalEntityCount`, `baselineRelationshipCount`,
  `hypotheticalRelationshipCount`, `removedEntityCount`,
  `addedRelationshipCount`, and `removedRelationshipCount` are whole twin
  working-set counts, not impacted-subgraph counts. Removed relationships
  include explicitly removed edges and edges cascaded away with a removed
  entity.
- **Delta semantics:** The delta contains before/after impacted counts,
  impacted added/removed counts and references, before/after risk counts,
  risk added/removed counts, risk-level changes, uncertain before/after
  counts, and `truncationChanged`. `riskLevelChanges` may move up or down;
  they must be displayed as changes, not automatically as increases. The
  contract does not include risk-added or risk-removed reference lists, so
  they must not be invented. S5 renders a complete server response and does
  not recompute or reinterpret it.
- **Policy response contract:** `PolicyEvaluationResponse` contains
  `decision` (`ALLOW`, `REVIEW`, or `DENY`), `policyVersion`, the exact
  advisory `ADVISORY POLICY EVALUATION — NOT AN AUTHORIZATION; NOT EXECUTED`,
  `settings`, `rulesFired` in server order, a bounded `simulation` summary
  containing only `graph`, `delta`, and `limitations`, and `note`. Every fired
  rule shows `code`, `outcome`, and `detail`; an empty list shows an explicit
  "No rules fired" state. The policy response has no before/after scopes,
  server timestamps, `simulated` flag, or risk taxonomy version. S5 must say
  that it is a bounded simulation summary and must not imply otherwise.
- **Independent results:** Simulation and policy have separate action
  buttons, loading state, error state, and retained result. There is no
  combined "Run both" action and no automatic request on view entry, root
  selection, or operation editing. Results are retained independently and
  labeled as separate server computations. No atomicity, shared snapshot, or
  shared execution semantics are implied.
- **State and failure behavior:** Changing the root or operations invalidates
  results tied to the previous request. Stale responses must not overwrite
  newer results. Retry repeats only the action that failed. Explicit states
  cover empty, loading, client validation, HTTP 400/401/403/404/409/5xx,
  network failure, and malformed responses. Malformed responses fail closed
  as `UNEXPECTED`; no partially valid result is rendered. Responses are
  strictly shape-validated while unknown forward-compatible string values of
  the correct type are preserved rather than allowlisted.
- **Error safety:** HTTP 409 maps additively to `ErrorKind.CONFLICT` with a
  fixed safe message; all pre-existing mappings remain unchanged. Error text
  is fixed and safe. Raw response bodies, headers, tokens, claims, and stack
  traces are never echoed, logged, or rendered.
- **Security and roles:** Both endpoints require `TWIN_ANALYZE`.
  `TWIN_READ` alone is insufficient and returns 403; `TWIN_ADMIN` inherits
  `TWIN_ANALYZE`. The UI explains the required role. The bearer token remains
  in React memory only: no `localStorage`, `sessionStorage`, IndexedDB,
  cookies, URL state, logging, or token display. The token is attached only
  to local `/api` requests through the existing client and Vite proxy.
- **Root and validation rules:** The analysis root is one entity from the
  already-loaded S1 snapshot. The builder must prevent removing that selected
  root and must show clear inline validation. Local conflict hints are
  advisory only; the server remains authoritative for identifier resolution,
  proposal conflicts, and all validation.
- **Presentation:** S5 is live-only and excluded from synthetic preview; no
  synthetic simulation or policy data is added. Entering the view sends no
  request. Server advisory strings are shown exactly. Simulation shows
  settings, all seven graph values as baseline/hypothetical twin working-set
  counts, before/after impact and risk scopes, all delta fields, all
  limitations, impact truncation/cycle/limits/evidence-path detail and
  timestamps, and risk level/reasons/uncertainty/stale state/taxonomy/timestamp
  detail without merging the four timestamps. Policy shows decision, policy
  version, every fired rule in server order, empty-rule state, settings,
  advisory, note, graph summary, delta, limitations, and the bounded-summary
  limitation. Untrusted text and JSON are rendered inertly as text; no raw
  HTML injection is permitted.
- **Non-persistence and non-execution:** Simulation and policy are read-only:
  the backend uses read-only transactions and in-memory hypothetical graph
  copies, and S5 neither triggers nor implies execution, approval, proposal
  lifecycle mutation, persistence, or target-system modification. The UI must
  not add execution, actuator, approval, or proposal mutation controls.
- **Explicitly prohibited:** Backend source, endpoint, DTO, schema, migration,
  database, dependency, CORS, authentication, hosting, or role changes; new
  frontend dependencies; TypeScript; token persistence; raw HTML rendering;
  synthetic preview data; automatic simulation/policy requests; a combined
  action; execution/actuator/approval/proposal mutation controls; proposal
  creation or lifecycle changes; and any behavior outside the two approved
  POST endpoints.
- **Testing expectations:** API tests cover exact POST paths, exact request
  JSON for every operation type and baseline-only, no request before explicit
  action, no automatic requests, bearer and JSON headers, no-token behavior,
  all listed HTTP/network/malformed cases, strict response validation,
  forward-compatible strings, and safe error text. View tests cover all
  simulation and policy fields and semantics, independent retained results,
  independent loading/error/retry, stale-response invalidation, the ten
  operation UI limit, and preview exclusion. Integration tests cover
  live-only navigation with no POST on entry. Existing S1-S4 tests remain
  green.
- **Verification expectations:** Frontend `npm test`, `npm run build`, and
  `npm audit --audit-level=high`; backend regression via the existing Maven
  wrapper; `git diff --check`; Docker/Testcontainers integration when Docker
  is available. Live verification uses only an already securely available
  local `TWIN_ANALYZE` identity, with separate baseline simulation and policy
  requests and no fabricated credentials. If credentials or infrastructure
  are unavailable, live verification is recorded as BLOCKED, never as passed.
- **Compatibility:** This decision is compatible with D28-D36 and preserves
  D29-D31 unchanged. D28 remains the authentication authority; D29 governance
  state and APPROVED semantics remain read-only and terminal; D30/D31
  execution gates remain closed; D33 console conventions, D34, and D35 are
  unchanged; D36 proposal presentation is unaffected. No execution or
  approval authority is created here.
- **Implementation checkpoints:** Record this decision before implementation.
  Implementation is frontend-only except for no backend change at all. The
  slice is complete only after the tests, build, audit, regression checks,
  live verification or recorded blocker, and documentation review. No push,
  PR, or merge occurs without explicit instruction.
- **Rejected alternatives:** A combined run-both action; automatic requests;
  synthetic simulation/policy fixtures; a backend operation cap presented as
  security; client-side recomputation of risk or deltas; invented risk
  references; merging timestamps; treating policy as authorization or
  execution; reusing a prior simulation response for policy; backend,
  database, schema, dependency, CORS, authentication, or hosting changes; and
  persistence of proposals, results, or tokens.

## D38 — Phase 14 S0: Evidence Foundation for Agent-Assisted Analysis (design-only, 2026-11-05)

- **Status:** Decided and adopted 2026-11-05. Design-only. No runtime
  implementation, no source code, no tests, no migrations, no configuration, no
  dependency, no endpoint, no persistence, no agent runtime, and no model
  provider are authorized. D1–D37 remain unchanged.

- **Date:** 2026-11-05.

- **Purpose:** Define how future agent-assisted analysis can produce
  explanations grounded in existing deterministic System Twin evidence. The
  intended trust flow is:

  System Twin and runtime observations
  → deterministic impact and risk
  → hypothetical simulation
  → advisory policy evaluation
  → evidence selection and qualification
  → human-readable analysis

  The future agent may explain and synthesize evidence. It cannot establish
  ground truth, authorize changes, approve proposals, or execute operations.

- **A. Conceptual evidence categories.** Future evidence must remain
  categorized distinctly; they must never be collapsed into one undifferentiated
  confidence score:
  1. `OBSERVED_FACT` — existing System Twin entity/relationship records and
     allowlisted runtime observations (health + six allowlisted counters from
     Phase 6).
  2. `DETERMINISTIC_FINDING` — impact paths, risk classifications, and
     policy-evaluation outputs.
  3. `HYPOTHETICAL_RESULT` — simulation before/after scopes, deltas, and
     limitations.
  4. `GOVERNANCE_RECORD` — proposal revisions, stored assessments, operations
     hashes, assessment hashes, policy/risk versions, and twin fingerprints.
  5. `AUDIT_EVIDENCE` — audit events and the independent audit-chain
     verification result.

- **B. Conceptual evidence envelope.** This is documentation only, not a Java
  record, DTO, database entity, or API contract. It contains:
  - Evidence item: packet-local reference; evidence category; original
    source/resource reference; relevant entity or proposal scope;
    source-provided version where available; source timestamps with their
    original semantics; structured evidence content from approved DTO fields;
    source-provided limitations; integrity metadata only when an existing
    source supplies it.
  - Claim: human-readable statement; claim category; one or more evidence
    references; qualification or support status; explicit limitations.
  References are conceptual and packet-local unless a future decision
  explicitly introduces persistence. No durable evidence IDs, global snapshot
  IDs, source hashes, timestamps, confidence scores, or freshness guarantees
  may be invented where the existing APIs do not provide them.

- **C. Timestamp and freshness semantics.** Differences between these must be
  preserved and never merged into one generic `lastUpdated` timestamp:
  - Twin entity `createdAt` / `updatedAt`.
  - Runtime observation `observedAt`.
  - Impact `snapshotObservedAt`.
  - Risk `snapshotObservedAt`.
  - The four separate simulation impact/risk timestamps (before impact/risk
    and after impact/risk).
  - Proposal assessment `snapshotObservedAt`.
  - Proposal/audit record creation and occurrence timestamps.
  Runtime observation receipts are returned per request; no observation-run
  history is persisted. Risk `risk-v1` uses the current runtime freshness rule
  (30 minutes, `RiskTaxonomy.STALE_AFTER`). Its freshness result applies only
  where runtime metadata supports it. `RiskResponse` does not expose
  impact-style truncation/cycle fields, so the future evidence model must not
  fabricate them. The model must distinguish current, stale, missing, uncertain,
  truncated/incomplete, hypothetical, and historical governance evidence, and
  must only classify a state when the source actually supports it.

- **D. Preserve source authority.**
  - Twin records are source data, but their mutable current state is not a
    complete historical record.
  - Impact traversal proves structural reachability, not confirmed breakage.
  - Risk is a deterministic advisory classification under `risk-v1`.
  - Simulation is hypothetical and non-persistent.
  - Policy `ALLOW` is not authorization.
  - `REVIEW` and `DENY` remain advisory policy outcomes.
  - A policy response contains only a bounded simulation summary, not full
    simulation scopes.
  - Proposal assessment is bound to the stored canonical operations, assessment
    hash, and captured twin fingerprint.
  - An ordered proposal audit-event list is not proof of chain integrity. Only
    the separate audit verification endpoint provides that result.
  - Existing hashes retain their original scope and purpose. A hash is not proof
    that the entire current system state is correct.

- **E. Claim qualification rules.** Future agent-generated factual claims must:
  1. Reference one or more evidence items supplied by the trusted evidence
     assembler.
  2. Preserve the original source and scope.
  3. Distinguish observed facts from deterministic conclusions and hypothetical
     outcomes.
  4. Preserve uncertainty, staleness, and truncation.
  5. State when evidence is missing or insufficient.
  6. Avoid claiming causality, confirmed breakage, safety, or completeness beyond
     the underlying contract.
  7. Avoid numeric confidence scores unless a later decision establishes a
     defensible calibration method.
  8. Never invent evidence references or source values.
  If a claim cannot be supported, the future agent must state that the
  available evidence is insufficient.

- **F. Security and trust boundaries.** Preserve D28 JWT authentication and
  current endpoint roles; D29 human governance and separation of duties;
  D30/D31 controlled-execution gates; D33 frontend token handling; D34 local
  Keycloak only; D35 impact/risk scope; D36 read-only proposal view; and D37
  simulation/policy advisory boundary. The future agent must be treated as
  untrusted. No direct database access, arbitrary network access, filesystem
  access, shell access, credential access, actuator access, or unrestricted
  tool execution. Evidence must be assembled from explicit, allowlisted
  server-side sources. No `TWIN_AGENT` role is introduced in S0. No LLM may
  create authorization, change proposal state, or call target services.

- **G. Data protection.** Evidence selection must be allowlist-based. Exclude
  JWTs and authorization headers; passwords, client secrets and private keys;
  database credentials and connection secrets; environment secrets; raw HTTP
  response bodies; unnecessary issuer/subject identity details; unfiltered
  metadata; and sensitive business values not required for the specific
  analysis. Treat entity metadata and user-authored proposal text as untrusted
  data.

- **H. Model/provider boundary.** D8 remains deferred. Do not choose or install
  Spring AI, Ollama, a cloud model provider, or an agent framework in S0. No
  prompts, LLM orchestration, memory, embeddings, vector database, MCP,
  multi-agent architecture, or agent runtime. Provider selection requires a
  later explicit decision.

- **I. Persistence and lifecycle.** The conceptual evidence envelope is
  transient and packet-scoped. S0 adds no tables, no Flyway migrations, no
  persistence, no background jobs, no scheduler, no evidence retention policy,
  and no new REST or SSE endpoints. Any future evidence persistence requires a
  separate decision addressing versioning, retention, integrity, privacy,
  freshness, and deletion.

- **J. Future implementation gating.** Phase 14 S1 is NOT authorized by D38. A
  future S1 proposal may evaluate a deterministic read-only evidence packet
  assembler over existing APIs and must obtain its own scope and decision first.
  Agent-generated narratives, model provider integration, and tool mediation
  remain separately gated.

- **Compatibility:** D38 is compatible with D28–D37 and preserves D29–D31
  unchanged. It creates no execution, approval, proposal lifecycle, or
  authentication authority. D33–D37 remain unchanged.

## D39 � Phase 14 S1: Deterministic Evidence Packet Assembler (decided and adopted 2026-11-05)

- **Status:** Decided and adopted 2026-11-05. Authorizes implementation of the Phase 14 S1 deterministic, read-only evidence packet assembler and its tests. S1 supplies evidence only. It does not generate claims, narratives, authorization, approvals, or execution.
- **Date:** 2026-11-05.
- **Depends on:** D38, merged via PR #22 at commit `562309195c6acac8156f48506513fd31ccac7947`; D28-D37; and the finalized root-level `PHASE14_S1_PROPOSAL.md`.
- **Purpose:** Authorize implementation of the Phase 14 S1 deterministic, read-only evidence packet assembler and its tests. S1 supplies evidence only. It does not generate claims, narratives, authorization, approvals, or execution.

- **A. Authorization scope.** This decision authorizes:
  1. A new backend package `com.soubhagya.systivex.evidence`.
  2. An internal, deterministic, read-only `EvidencePacketAssembler` (or equivalent service).
  3. Immutable packet and evidence-item structures, including packet-local references and qualification metadata.
  4. Explicit source adapters/interfaces within the `evidence` package.
  5. Existing domain services as the sole source of evidence.
  6. Additive read-only service or repository methods only in their owning modules where a safe bounded read is required.
  7. Tests for source provenance, limits, security, determinism, and absence of mutation.
  No cross-module repository access, self-referential HTTP calls, direct target-service access, or new public endpoint is authorized.

- **B. Evidence categories and allowlists.** The assembler must preserve the five D38 categories:
  1. `OBSERVED_FACT`
  2. `DETERMINISTIC_FINDING`
  3. `HYPOTHETICAL_RESULT`
  4. `GOVERNANCE_RECORD`
  5. `AUDIT_EVIDENCE`

  Allowlisted source content:
  - Twin entities: `id`, `type`, `name`, `externalRef`, `environment`, `createdAt`, `updatedAt`.
  - Twin relationships: `id`, `sourceId`, `targetId`, `type`, `createdAt`.
  - Runtime metadata only: `health.available`, `health.status`, `observedAt`, allowlisted counters, and source limitations.
  - Impact and risk: all fields exposed by `ImpactResponse` and `RiskResponse`.
  - Simulation: `simulated`, advisory marker, settings, before/after scopes, graph summary, delta, and limitations.
  - Policy: decision, policy version, advisory marker, settings, fired rules, bounded simulation summary, and note.
  - Proposal governance: selected or latest revision only, using `proposalKey`, `revisionNumber`, `status`, `operations`, `operationsHash`, `assessment`, `assessmentHash`, `policyDecision`, `policyVersion`, `riskTaxonomy`, `twinFingerprint`, `snapshotObservedAt`, `expiresAt`, `createdAt`, and `updatedAt`.
  - Audit evidence: per-proposal audit events limited to `id`, `seq`, `occurredAt`, `action`, `outcome`, `proposalKey`, `revisionNumber`, `operationsHash`, `assessmentHash`, `prevHash`, and `eventHash`.

  Excluded by default:
  - The entire arbitrary metadata map and any non-allowlisted metadata key.
  - JWTs, authorization headers, passwords, client secrets, private keys, database credentials, connection secrets, environment secrets, raw HTTP response bodies, unnecessary issuer/subject identity details, unfiltered metadata, and sensitive business values not required for the specific analysis.
  - Free-form proposal `title`, `description`, `reason`, `proposerIssuer`, `proposerSubject`, `approverIssuer`, `approverSubject`, and `correlationId`.
  - Any field not present in the source DTO.
  - Impact truncation or cycle fields in risk evidence, because `RiskResponse` does not expose them.
  - Before/after scopes, server timestamps, `simulated` flag, and risk taxonomy version in policy evidence, because `PolicyEvaluationResponse` does not provide them.

- **C. Packet structure and references.** The packet is transient and packet-scoped. It has no durable ID and no persistence. It contains:
  1. A packet-local reference, such as `P1`.
  2. An ordered list of evidence items.
  3. Limits applied.
  4. Explicit omissions.
  Each evidence item contains:
  1. A packet-local reference, such as `E1`, `E2`.
  2. One of the five D38 categories.
  3. The exact original source or resource reference.
  4. The relevant entity or proposal scope.
  5. The source-provided version where available.
  6. Source timestamps with their original semantics.
  7. Allowlisted structured content only.
  8. Source-provided limitations.
  9. Qualification metadata.
  D38 claim structures remain reserved. S1 must not populate claims or generate natural-language narratives. Packet-local references are deterministic and must not become durable IDs.

- **D. Request and assembly semantics.** The internal request supports mutually exclusive analysis modes:
  1. `BASELINE` for existing impact and risk analysis.
  2. `SIMULATION` for explicit hypothetical simulation.
  3. `POLICY_EVALUATION` for explicit advisory policy evaluation, independently recomputed by the existing policy service.
  Common inputs reuse existing validation: 1-10 roots, `maxDepth` 1-10 with default 6, and `includeTypes` defaulting to `SERVICE,API,DATABASE,TABLE`. An optional `proposalKey` and optional `revisionNumber` may request proposal evidence. Audit evidence requires an explicit `includeAuditEvidence` flag. Caller-supplied impact, risk, policy decisions, or simulation results are never accepted as authoritative evidence. Simulation and policy evaluation must not be silently executed together; if both are requested, assembly must fail with an explicit conflict or require separate packets. Existing validation semantics must be reused exactly.

- **E. Limits and truncation.** The following limits are authorized for S1:
  1. Maximum 10 analysis roots.
  2. `maxDepth` between 1 and 10, default 6.
  3. Impact traversal limits of 500 nodes and 2,000 paths.
  4. 100 evidence items per source family.
  5. 1,000 evidence items total.
  6. 256 KiB serialized UTF-8 JSON.
  7. 100 per-proposal audit events.

  Nested source limits:
  - Twin entity records: maximum 100 per packet.
  - Twin relationship records: maximum 100 per packet.
  - Impact evidence: one item; maximum 100 impacted entities and maximum 100 complete impact paths. If a path is included, all of its steps must be included. Preserve the server-reported impacted count, path count, truncation state, cycle state, and limits.
  - Risk evidence: one item; maximum 100 risk entries. Preserve risk taxonomy version, uncertainty, stale flags, and advisory note.
  - Policy evidence: one item; maximum 100 fired rules. Preserve decision, policy version, settings, bounded simulation summary, and advisory note.
  - Proposal governance evidence: one item for the explicitly selected or latest revision only. Do not include revision history.
  - Audit evidence: maximum 100 per-proposal audit events per packet, in `seq` ascending order.

  Nested collection limits:
  - Simulation before/after impact results: preserve all impact fields for each included scope. Cap impacted entities at 100 and complete impact paths at 100. Preserve server-reported counts and truncation/cycle state.
  - Simulation before/after risk results: preserve all risk fields. Cap risk entries at 100. Preserve taxonomy version, uncertainty, stale flags, and advisory note.
  - `SimulationDelta.impactedAdded`: maximum 100 strings, each maximum 512 characters. Preserve `impactedAddedCount`.
  - `SimulationDelta.impactedRemoved`: maximum 100 strings, each maximum 512 characters. Preserve `impactedRemovedCount`.
  - `SimulationDelta.riskLevelChanges`: maximum 100 entries. Each `entityRef` maximum 512 characters. Preserve the full before/after levels for every included entry.
  - `RiskEntry.reasons`: maximum 20 reasons per entry. Each reason maximum 1,000 characters. If either cap is exceeded, include the first N reasons or characters and mark the entry `TRUNCATED`.
  - Policy fired-rule details: maximum 100 rules. Each `detail` maximum 1,000 characters. If exceeded, include the first 1,000 characters plus an explicit truncation marker.
  - Simulation and policy limitations: maximum 100 strings. Each limitation maximum 1,000 characters.
  - Structured proposal operations: include only for the selected or latest revision. Maximum 100 operations in the packet. If exceeded, include the first 100 operations in canonical order and set `operationsTruncated = true`. This is packet display truncation only and must never be used to execute a partial simulation.
  - Runtime counters: include only allowlisted counter families. The current allowlist contains six counters, and all allowlisted counters may be included.
  - Dynamic text fields, including notes, advisory text, truncation reasons, limitation strings, and rule details: maximum 1,000 characters each. If a source provides a longer value, include the first 1,000 characters plus an explicit truncation marker and record the limitation.

  Serialized size limit:
  - Maximum serialized packet size: 256 KiB (262,144 bytes) of UTF-8 JSON.
  - The assembler must calculate the serialized size before returning the packet.
  - If nested caps alone would exceed 256 KiB, apply deterministic source caps and mark affected sources `TRUNCATED`.
  - Never byte-truncate JSON. If the packet cannot be represented within 256 KiB after applying all caps, fail assembly with an explicit error rather than emitting invalid JSON.

  Simulation operation limit:
  - S1 defines a maximum of 100 simulation operations for `SimulationRequest`.
  - Requests exceeding 100 operations must be rejected before invoking `SimulationService`.
  - Never truncate an operation list and simulate only the first 100 operations.
  - Never present a partial-operation simulation as representing the complete requested change.
  - Do not invent a canonical operations hash for ordinary simulation requests.
  - Include `operationsHash` only when it comes from an existing persisted proposal record.
  - Proposal-operation display truncation, if any, is separate from simulation execution limits and must not affect simulation execution.

  Truncation reporting:
  - Every truncation must set the source status to `TRUNCATED` and include an explicit limitation.
  - Preserve original server-reported counts wherever the source provides them.
  - If a collection is capped, record both the included count and the omitted count.
  - Missing or unavailable evidence must be recorded as `UNAVAILABLE` or `OMITTED`, never as an empty successful result.

- **F. Transaction and snapshot behavior.** The assembler must be `@Transactional(readOnly = true)` and invoke existing services within that transaction. Nested read-only service methods may join the active transaction. The default isolation level is `READ_COMMITTED`. The packet must not claim a single global snapshot. Source timestamps remain independently generated and retain their original semantics. `REPEATABLE_READ` is not authorized for S1 unless a later decision explicitly changes this boundary.

  Bounded complete graph snapshot:
  - D39 authorizes an additive read-only graph-snapshot capability in the owning twin module. S1 must not call `TwinService.listEntities()` or `listRelationships()` as evidence sources, and must not expose repositories across module boundaries.
  - Recommended implementation: add a read-only twin-module method, for example `TwinService.readGraphSnapshot()`, returning an immutable `TwinGraphSnapshot` containing `entities`, `relationships`, `entityCount`, `relationshipCount`, `entityCountAtLeast`, `relationshipCountAtLeast`, and `truncated`.
  - The method must use deterministic ordering and fetch at most 501 entities and 2,001 relationships.
  - If either source returns 501 entities or 2,001 relationships, set `truncated = true`. The graph snapshot status is `TRUNCATED`.
  - On overflow, do not analyze any partial graph. Report the twin graph source as `UNAVAILABLE` with an explicit limitation stating that complete graph bounds were exceeded.
  - When within limits, expose the complete accepted entity and relationship counts. When overflow is detected, report a lower-bound count such as `entityCountAtLeast=501` or `relationshipCountAtLeast=2001`. Do not run an exact full-table count merely to populate packet metadata unless its cost and contract are explicitly justified.
  - Before analysis, validate that every relationship endpoint in `relationships` exists in `entities`. If the accepted snapshot is internally inconsistent under `READ_COMMITTED`, refuse graph analysis and report the source as `UNAVAILABLE` with an explicit limitation.
  - If both sources are within limits, pass the complete accepted snapshot into `ImpactAnalysisService.analyzeResolved(...)`, `RiskService.assessResolved(...)`, and `SimulationService.simulateResolved(...)`.
  - The packet must record snapshot counts and truncation state. It must not claim that the packet contains the full twin graph when truncation occurred.
  - `READ_COMMITTED` remains in effect. The snapshot and analysis may occur in the same read-only assembler transaction, but the packet must not claim a globally synchronized snapshot.
  - If this bounded snapshot cannot be implemented safely within S1, defer raw twin graph evidence and any derived impact, risk, simulation, or policy evidence that depends on loading the full twin graph. The packet must record the source as `OMITTED` or `UNAVAILABLE` with an explicit limitation.

  Policy-mode bounded execution path:
  - For `POLICY_EVALUATION` mode, D39 authorizes only this sequence:
    1. Obtain the accepted complete bounded graph snapshot.
    2. Validate and resolve roots and operations against that snapshot.
    3. Call `SimulationService.simulateResolved(...)` using the accepted snapshot.
    4. Call `PolicyService.evaluateResolved(...)` using that server-computed simulation.
  - D39 explicitly prohibits calling `PolicyService.evaluate(SimulationRequest)` from the S1 assembler, because that path invokes `SimulationService.simulate(...)`, which performs the unbounded entity and relationship load.
  - Simulation mode must also use the accepted snapshot and call `SimulationService.simulateResolved(...)`.
  - Baseline mode must use snapshot-based root resolution and the existing resolved impact and risk service methods.
  - No caller-supplied impact, risk, simulation, or policy result is trusted.

- **G. Source availability and failure behavior.** The packet uses these proposed packet-level statuses; they are not existing API fields:
  1. `AVAILABLE`: the source returned complete allowlisted evidence.
  2. `UNAVAILABLE`: the source could not be read or required evidence was missing.
  3. `OMITTED`: the source was not requested, is not applicable to the selected mode, or is deliberately excluded by policy.
  4. `TRUNCATED`: the source returned more data than the configured cap, or the source itself reports truncation.
  Required analysis source failure must abort packet assembly and propagate the original exception. Optional missing proposal or audit evidence must be recorded as `OMITTED` or `UNAVAILABLE` with a limitation, and assembly must continue. Partial or truncated source evidence must be included with status `TRUNCATED` and an explicit limitation. Missing evidence must never be converted into an empty successful result or an implicit safe conclusion.

- **H. Security and trust requirements.** The assembler must:
  1. Be read-only.
  2. Have no credentials or token access.
  3. Have no arbitrary network, filesystem, shell, or actuator access.
  4. Never call target services.
  5. Never mutate proposals or audit records.
  6. Never create approval or execution authority.
  7. Treat metadata and proposal text as untrusted data.
  8. Use explicit allowlists for every serialized field.
  No `TWIN_AGENT` role and no new role is authorized. No new endpoint or security matcher is authorized in S1. A future public endpoint requires a separate design and authorization decision. The assembler must not receive JWTs, authorization headers, credentials, secrets, or raw tokens. Caller identity is not required for S1. If a future HTTP boundary exposes evidence packets, authorization must occur at that boundary using trusted principal information.

- **I. Audit evidence boundaries.** Per-proposal audit events must remain distinct from global chain verification. Global audit-chain verification remains deferred from S1 because the current `AuditService.verify()` implementation reads the entire audit chain. Per-proposal audit evidence may be included only behind a bounded read path with a maximum of 100 events and explicit truncation reporting. The repository method must use stable `seq` ordering with limit-plus-one. Partial event retrieval never establishes chain integrity. Only the separate audit verification result can establish chain status.

- **J. Proposal and history boundaries.** Proposal history is excluded initially; only the explicitly selected or latest revision may be included. Full history retrieval must not be used for a latest-revision request. D39 authorizes an additive read-only repository method in the owning proposal module:
  - `Optional<ProposalRevision> findFirstByProposalKeyOrderByRevisionNumberDesc(UUID proposalKey)`
  `ProposalLookup.latestOrThrow(...)` must use this method instead of `findByProposalKeyOrderByRevisionNumberAsc(...)`. `ProposalService.latest(...)` must use the same bounded lookup. Free-form proposal `title`, `description`, `reason`, `proposerIssuer`, `proposerSubject`, `approverIssuer`, `approverSubject`, and `correlationId` are excluded. Structured operation types, approved identifiers, hashes, versions, fingerprints, statuses, and timestamps may be included where justified. Hashes retain their original scope and purpose and are not proof that the entire current system state is correct.

- **K. Explicit non-goals.** D39 does not authorize:
  1. LLM, Spring AI, Ollama, or cloud provider integration.
  2. Agent orchestration or multi-agent architecture.
  3. Prompts, embeddings, memory, vector databases, or MCP.
  4. Evidence persistence, migrations, background jobs, or schedulers.
  5. Target-service calls.
  6. Proposal mutations, approvals, execution, or actuator integration.
  7. Production identity or hosting changes.
  8. Frontend changes.
  9. New REST or SSE endpoints.
  10. New roles or authorization changes.
  11. Claims or natural-language narrative generation.

- **L. Verification expectations.** Implementation is acceptable only if tests cover:
  1. All five evidence categories.
  2. Correct source and resource provenance.
  3. Stable ordering and packet-local references.
  4. Timestamp preservation.
  5. Stale and uncertain runtime evidence.
  6. Impact truncation and cycle limitations.
  7. Hypothetical versus deterministic distinction.
  8. Policy `ALLOW` non-authorization.
  9. Audit list versus chain-verification distinction.
  10. Proposal data minimization.
  11. Arbitrary metadata and user-text exclusion.
  12. Explicit omission of unavailable evidence.
  13. Bounded output and truncation markers.
  14. No database mutation or proposal lifecycle mutation.
  15. No target-service calls.
  16. No credentials, tokens, or sensitive identity leakage.

- **M. Compatibility.** D39 is compatible with D28-D38 and preserves D29-D31 unchanged. It creates no execution, approval, proposal lifecycle, or authentication authority. D33-D37 remain unchanged. It does not modify D38.

- **N. Implementation checkpoints.** Record this decision before implementation. Implementation is frontend-independent and backend-only. The slice is complete only after tests, build, security review, bounded-read review, and documentation review. No push, PR, or merge occurs without explicit instruction.

- **O. Rejected alternatives.** A public endpoint in S1; direct repository access across module boundaries; self-referential HTTP calls; LLM-generated claims in S1; unbounded audit or proposal history; inclusion of free-form proposal text; inclusion of global audit verification without a bounded contract; a claimed global snapshot; `REPEATABLE_READ` isolation in S1; treating the frontend 10-operation limit as a backend contract; analyzing a partial graph; running an exact full-table count merely to populate packet metadata.

## 4. Scope conflicts with the finalized S1 proposal

No scope conflicts were identified.

D39 formalizes the finalized proposal's recommended limits as authorized implementation boundaries: 10 roots, existing impact limits, 100 items per source family, 1,000 total items, 256 KiB serialized output, and 100 per-proposal audit events with explicit truncation. It also preserves the proposal's boundaries around `READ_COMMITTED`, no global snapshot, mutually exclusive simulation/policy modes, deferred global audit verification, selected/latest proposal revision only, exclusion of free-form proposal text, no caller credentials, no endpoint, no new role, and no frontend changes. The corrected graph snapshot and policy-mode wording resolves the remaining unbounded-read inconsistencies by requiring a bounded snapshot, refusing partial graph analysis, and mandating `simulateResolved()` followed by `evaluateResolved()`.

## 5. Final Git status

- Branch: `main`
- HEAD: `562309195c6acac8156f48506513fd31ccac7947`
- Local `main` matches `origin/main`.
- Working tree: `?? PHASE14_S1_PROPOSAL.md`
- `PHASE14_S1_PROPOSAL.md` is untracked and was not modified, staged, committed, pushed, or included in PR #22.
- Root `application.properties` remains missing and was not reconstructed.
- Backend `application.properties` hash remains unchanged.
- No tracked files were modified by this step.
- No D39 text was appended to `DECISIONS.md`.

Stop for review and explicit approval before modifying `DECISIONS.md`.
## D40 — Phase 14 S2: Evidence-Grounded Claim Contract and Deterministic Validator (decided and adopted 2026-11-09)

- **Status:** Decided and adopted 2026-11-09. Authorizes implementation of Phase 14 S2 **Slice 1 only**: the provider-independent claim contract and deterministic claim validator over S1 evidence packets, with corresponding tests. S1 supplies evidence only; Slice 1 supplies validated claim structure only. It does not generate claims, narratives, authorization, approvals, or execution, and it does not authorize any provider, model, or generation capability.
- **Date:** 2026-11-09.
- **Depends on:** D38 (Phase 14 S0 design), D39 (Phase 14 S1 assembler, merged via PR #25), D8, D28–D31.
- **Purpose:** Define the structured claim contract and deterministic validation that any future agent-assisted explanation must satisfy before human review, while keeping the producing component untrusted and authority-less.

- **A. Authorization scope.** This decision authorizes implementation of S2 Slice 1 only:
  1. Provider-independent, immutable claim structures (statement, category, evidence references, qualification, limitations, packet reference, mode).
  2. Deterministic claim schema validation (required fields, types, bounds).
  3. In-process `EvidencePacket` acceptance: the validator accepts only the `EvidencePacket` object produced by `EvidencePacketAssembler` in the same JVM. Caller-deserialized JSON and externally supplied packets are never accepted as evidence; no packet deserialization entry point is authorized.
  4. Packet identity and evidence-reference validation (every reference an `E<n>` present in this packet; unknown, malformed, or foreign-packet references rejected; never remapped by content similarity).
  5. Category consistency (claim category matches every referenced item; mixed-category references require split claims).
  6. Qualification/status propagation per the exhaustive table and precedence in section D.
  7. Limitation inheritance (every referenced item limitation present or subsumed by an at-least-as-strong limitation).
  8. Mode-aware deterministic insufficiency handling per the category-to-source-family matrix and mode applicability matrix in section E.
  9. Deterministic wording screen rejecting causality, confirmed-breakage, safety, completeness, authorization, and execution assertions.
  10. Corresponding unit and adversarial tests (schema, fabricated/foreign references, category laundering, qualification matrix, limitation inheritance, insufficiency, wording fixtures).
  No Spring AI or any provider dependency, no provider configuration, no Ollama integration, no model invocation, no prompt construction, no generated claims or narratives, no public endpoint/API, no frontend, no persistence or migrations, no credentials or caller-identity exposure, no agent runtime or authority, no database/network/filesystem/shell access for an agent, no target-service access or actuator integration, and no mutation, approval, authorization, or execution capability is authorized.

- **B. Slice gating.** Slice 2 (Spring AI + local Ollama adapter) requires separate provider/dependency/configuration authorization. Slice 3 (evidence-grounded generation and evaluation harness) requires Slice 2 authorization and review. A future human-review presentation surface (Slice 4) requires separate authorization. This decision does not silently authorize all three slices.

- **C. D8 relationship.** D8's Spring AI + local Ollama direction is preserved unchanged: it remains the adopted architectural direction with implementation deferred. Adopting this decision does not authorize the Spring AI dependency, any configuration, or any implementation of D8's direction. No D8 amendment is made or implied. A direct hand-rolled Ollama adapter would require an explicit D8 amendment and is not authorized.

- **D. Qualification/status rules (Slice 1 contract).** Qualification and status are distinct fields and never conflated; `UNAVAILABLE` is a status, never a qualification. The 8 qualifications (`CURRENT`, `STALE`, `UNCERTAIN`, `TRUNCATED`, `MISSING`, `HYPOTHETICAL`, `HISTORICAL`, `ADVISORY`) × 4 statuses (`AVAILABLE`, `UNAVAILABLE`, `OMITTED`, `TRUNCATED`) give 32 pairs. Valid pairs (18) are exactly those S1 adapters produce; the other 14 are invalid and must be rejected fail-closed when referenced. `OMITTED` never appears as an item status in S1 (omitted sources are separate omission records), so all `* + OMITTED` pairs are invalid. Contributions: any `UNAVAILABLE` status imposes a uniform `UNCERTAIN` floor (exact outputs: `HISTORICAL + UNAVAILABLE → UNCERTAIN`, `STALE + UNAVAILABLE → UNCERTAIN`, `CURRENT + UNAVAILABLE → UNCERTAIN`); `MISSING + UNAVAILABLE → MISSING`; `TRUNCATED` status dominates except for `MISSING`; `HYPOTHETICAL`/`HISTORICAL`/`ADVISORY`/`CURRENT`/`STALE`/`UNCERTAIN` pass through with `AVAILABLE`, and map to `TRUNCATED` with `TRUNCATED` status. Claim qualification is the least favorable contribution under explicit precedence, worst first: `MISSING` > `TRUNCATED` > `UNCERTAIN` > `STALE` > `HISTORICAL` > `HYPOTHETICAL` > `ADVISORY` > `CURRENT`. Enum declaration order must never be used as precedence. The `HYPOTHETICAL` precedence entry is explicit defense-in-depth only (unreachable while category consistency holds).

- **E. Insufficiency (mode-aware).** Enforcement is deterministic via a fixed category-to-source-family matrix — `OBSERVED_FACT`: `twin.entity`, `twin.relationship`, `twin.runtime`; `DETERMINISTIC_FINDING`: `impact.analysis`, `risk.assessment`, `policy.evaluation`; `HYPOTHETICAL_RESULT`: `change.simulation`; `GOVERNANCE_RECORD`: `proposal.revision`; `AUDIT_EVIDENCE`: `audit.events` — combined with a mode × family applicability matrix verified against `EvidencePacketAssembler` control flow: twin families applicable in all modes; `impact.analysis`/`risk.assessment` applicable in BASELINE only; `change.simulation` applicable in SIMULATION/POLICY_EVALUATION only; `policy.evaluation` applicable in POLICY_EVALUATION only; `proposal.revision`/`audit.events` applicable in all modes when requested. Only `OMITTED`/`UNAVAILABLE` omissions for applicable families force an insufficiency claim (`MISSING`, naming the source and reason); omissions for mode-inapplicable families never block claims. The guarantee is category-level; sentence-level dependency is not inferred and not claimed.

- **F. Security and trust boundaries.** The generation component and provider (future slices) are untrusted explanation components with no authority. Packet content is data, not instructions; delimiters/prompt formatting are not a security boundary — the deterministic validator and mandatory human review are the actual safeguards. Reference existence proves provenance, not support; arbitrary numbers, dates, names, and other values inside statement text are not validated deterministically and require evaluation and human review. Policy `ALLOW` is advisory; `APPROVED` is a stored fact; hypothetical content stays hypothetical; audit lists never imply chain integrity. No `TWIN_AGENT` role. D28 authentication and D29 separation of duties are untouched.

- **G. Future provider requirements (Slice 2, not authorized here).** Any provider endpoint must be loopback-only: `http` scheme only, no userinfo, explicit validated port (default 11434 only if the configuration design adopts it), host restricted to loopback IP literals `127.0.0.1`/`::1`/`0:0:0:0:0:0:1` with hostnames rejected and no DNS resolution, alternate numeric encodings and IPv4-mapped IPv6 rejected, malformed authorities rejected, no redirects, invalid configuration fails closed, no cloud/remote/arbitrary/fallback endpoints. Client endpoint validation is separate from Ollama server bind (`OLLAMA_HOST=127.0.0.1` or equivalent), which must be independently verified before packet data flows. Resource requirements: serialized process-wide single-flight inference with immediate `ANALYSIS_BUSY` rejection and no queue; separate connection (proposed 5 s), read (proposed 30 s), and overall (proposed 35 s) timeouts; zero retries, no remote fallback; output capped at 64 KiB/100 claims enforced during streaming read; parser-level `tools.jackson` constraints (Jackson 3.1.x `StreamReadConstraints`: nesting depth, document length, token count, number/string/name lengths) with `STRICT_DUPLICATE_DETECTION` and `FAIL_ON_UNKNOWN_PROPERTIES`; structural limits (100 claims, 20 references per claim, 1,000 characters per string, 100 limitations per claim, required fields); byte/character/parser limits are provider-independent, token limits are provider-specific and deferred. No current code configures these parser constraints; they are requirements for the separately authorized provider slice.

- **H. Acceptance criteria (Slice 1).** Validator tests cover the full 32-pair table (valid contributions + invalid-pair rejection), the 27-cell mode matrix, insufficiency, limitation inheritance, wording screen, and adversarial fixtures (fabricated/foreign references, category laundering, `ALLOW` misuse, audit→verification, hypothetical-as-executed); no provider/model/HTTP/generation code or new dependency; full S1 suite (505 tests) remains green; `git diff --check` clean; no endpoint, table, migration, or role change.

- **I. Deferred (do not weaken Slice 1).** Model/version selection and pinning; Ollama server-bind deployment verification method; evaluation sample sizes/strata; final calibration of §G numeric bounds; any presentation surface.

- **J. Compatibility.** D40 is compatible with D8 and D28–D39 and preserves D29–D31 unchanged. It creates no execution, approval, proposal lifecycle, authentication, or provider authority. It does not modify D8, D38, or D39.

- **K. Explicit non-goals.** D40 does not authorize: Spring AI or any provider dependency/configuration; Ollama integration or model invocation; prompts or generated claims; agent orchestration, tools, memory, embeddings, vector databases, or MCP; public endpoints, frontend, persistence, or migrations; new roles; claims or natural-language narrative generation; confidence scoring.

## D41 — Phase 14 S2 Slice 2: Local Model Provider Integration (Spring AI + Local Ollama) (decided and adopted 2026-11-09)

- **Status:** APPROVED / ADOPTED 2026-11-09. Authorizes the Phase 14 S2 Slice 2 **design and boundaries** (local model provider integration with Spring AI 2.0.2 and a loopback-local Ollama instance, bounded synchronous inference, plus unit/adapter/integration tests) as specified in `docs/planning/phase-14/PHASE14_S2_SLICE2_PROPOSAL.md` (859 lines, verified at adoption). Like D38, this adoption alone authorizes **no implementation code, dependency, configuration, endpoint, persistence, or execution capability**; Slice 2 implementation requires a separate explicit authorization, at which point the acceptance criteria in section D become implementation-gating. S2 Slice 1 (D40) remains COMPLETE; Slice 3 remains separately gated.
- **Date:** 2026-11-09.
- **Depends on:** D8 (Spring AI + local Ollama direction, deferred until now adopted for Slice 2 design), D38 (S0 design), D39 (S1 assembler), D40 (S2 Slice 1 contract and validator; its section G provider requirements). D41 amends no prior decision.
- **Purpose:** Adopt the reviewed Slice 2 design, with independently verified evidence (proposal, scratch implementation, resolved 2.0.2 JARs, Maven tree/effective POM, runtime probes), so that a future implementation has a precise, evidence-backed contract and test obligations.

- **A. Authorization scope (Slice 2 only).** This decision adopts the Slice 2 design only:
  1. Provider-neutral internal inference boundary; Spring AI integration with local Ollama via the direct `org.springframework.ai:spring-ai-ollama:2.0.2` artifact (never the broad starter).
  2. Minimal Ollama-specific adapter; strict local endpoint configuration and validation (loopback-only, server-bind and cloud-disabled deployment).
  3. Bounded synchronous inference request/response contract (65,536-byte cap, §D G-1).
  4. Connection/read/overall timeout enforcement (5s/30s/35s, JDK `HttpClient` semantics).
  5. No-retry inference (one attempt, §D G-2); direct-only proxy and redirects disabled (§D G-3).
  6. Model/version verification and local model availability (§D G-4).
  7. Unit/adapter/integration tests and operational configuration documentation.
  No prompt templates or EvidencePacket-to-prompt assembly, no generated EvidenceClaim creation, no semantic evaluation, no public endpoint, frontend, persistence, chat memory, RAG, tools/MCP, cloud fallback, streaming inference, agent authority, mutation, approval, authorization, or execution capability is authorized.

- **B. Slice gating.** Slice 2 implementation and Slice 3 (evidence-grounded generation and evaluation harness) each require a separate explicit authorization; Slice 3 additionally requires Slice 2 authorization and review. This decision does not authorize either implementation or Slice 3.

- **C. D8/D40 relationship.** D8's Spring AI + local Ollama direction is adopted for Slice 2 design, remaining a deferred implementation direction until separately authorized; D8 is preserved unchanged in direction. D40 remains unchanged: endpoint and loopback rules are not weakened; the hand-rolled Ollama client prohibition is preserved — Spring AI remains the provider framework and a direct hand-rolled adapter is not authorized.

- **D. Mandatory implementation acceptance criteria.** These are gating for any future Slice 2 implementation:
  - **G-1 (bounded response):** maximum 65,536 response-body bytes; no byte 65,537 may reach the caller or parser; tests executed for chunked unknown-length responses, missing Content-Length, misleading Content-Length, oversized declared Content-Length, and `transferTo()`, retaining exact-boundary, overflow, single/bulk read, `skip`, `readAllBytes`, close, and I/O failure tests.
  - **G-2 (no-retry inference):** the Spring AI `retryTemplate` is explicitly wired (`new RetryTemplate(RetryPolicy.builder().maxRetries(0).build())`, i.e. one total attempt — proven at runtime: `maxRetries(0)` → 1 attempt, `maxRetries(1)` → 2 attempts); zero retries, no backoff, no retry queue; the inference `RetryPolicy` is kept distinct from `ModelManagementOptions` pull behavior; the separate Spring Retry dependency (`org.springframework.retry:spring-retry`, `RetryTemplate.builder().maxAttempts(int)`) must not be introduced.
  - **G-3 (network isolation):** the exact configured direct-only `ProxySelector` instance is supplied through the complete chain (JDK `HttpClient` → `JdkClientHttpRequestFactory` → bounded wrapper → `RestClient.Builder.requestFactory(...)` → `OllamaApi.Builder.restClientBuilder(...)`); `Proxy.NO_PROXY`; `ProxySelector.getDefault()` must never appear; `followRedirects(HttpClient.Redirect.NEVER)` (the JDK method name is `followRedirects(...)`); transport identity verified; proxy-bypass, 302/307 redirect-rejection, and outbound-path tests executed; the shared-loopback/network-namespace deployment requirement (no Docker bridge / `host.docker.internal` as a substitute) is retained.
  - **G-4 (model identity):** installed/running model verification (`/api/tags`, `/api/show` — Spring AI 2.0.2's `OllamaApi` has no `/api/ps` method) does not establish per-request cryptographic identity; no stronger guarantee may be claimed, and this limitation is preserved as an accepted residual.
  - **G-5 (dependency scope):** the verified direct `spring-ai-ollama:2.0.2` approach and the verified minimal dependency boundary are preserved (no ChatClient, chat-memory, tool, embedding, RAG, vector, or MCP artifacts; framework resolved at 7.0.9 under the Boot 4.1.1 BOM); no broad starter dependencies without separate justification and approval.
  - The corrected API details are mandatory: `followRedirects(...)`; the Spring 7 `ClientHttpResponse.getStatusCode(): HttpStatusCode` and `getStatusText()` signatures; `JdkClientHttpRequestFactory.setReadTimeout(...)` for the exchange timeout and `HttpClient.Builder.connectTimeout(...)` for the connect timeout.

- **E. Compatibility.** D41 is compatible with D8 and D28–D40 and preserves them unchanged. It creates no execution, approval, proposal-lifecycle, authentication, or provider authority. It does not modify D8, D38, D39, or D40.

- **F. Explicit non-goals.** D41 does not authorize: Slice 2 implementation code, dependencies, or configuration; Slice 3 forms of generation/evaluation; endpoints, persistence, frontend, or new roles; agent orchestration, tools, memory, embeddings, vector stores, or MCP; cloud fallback; mutation, approval, authorization, or execution capability.


## D42 — Phase 14 S2 Slice 3: Evidence-Grounded Generation and Evaluation Harness (design adoption only)

- **Status:** DECIDED AND ADOPTED 2026-11-11. Design adoption only. No implementation authorization.
- **Date:** 2026-11-11.
- **Depends on:** D38 (Phase 14 S0 evidence foundation), D39 (Phase 14 S1 deterministic evidence packet assembler), D40 (Phase 14 S2 Slice 1 claim contract and deterministic validator), D41 (Phase 14 S2 Slice 2 local model provider). D42 amends no prior decision.
- **Purpose:** Adopt the reviewed Phase 14 S2 Slice 3 design as the binding design for a future, separately authorized implementation. Design adoption, implementation authorization, and implementation completion are distinct states. D42 adopts the design only.

- **A. Adopted design.** D42 adopts the reviewed Phase 14 S2 Slice 3 design from `docs/planning/phase-14/PHASE14_S2_SLICE3_PROPOSAL.md` (SHA-256 `DE5189CF7E15D23921B590759B9F26E9E5204BD0197B00CF225A8CBF1A0A6BB3`, 813 lines, verified at adoption). The adopted pipeline is:

  EvidencePacket
  -> deterministic prompt construction
  -> existing `LocalModelAdapter`
  -> strict bounded parsing
  -> candidate `EvidenceClaim` objects
  -> existing deterministic `ClaimValidator`
  -> transient generation result

  The model and generator remain untrusted, authority-less explanation components. Evidence remains untrusted input. Prompt delimiters are not a security boundary. No tools, function calling, RAG, vector database, agent runtime, external provider, cloud fallback, or second-LLM authority is introduced. No semantic-entailment guarantee is claimed. Generated claims are candidates only; no automatic acceptance is permitted; human review remains mandatory.

- **B. Claim limits.** D42 adopts these distinct constraints:
  1. D40's existing structural parser limit: maximum 100 claim records.
  2. D42's Slice 3 generation limit: maximum 25 generated candidate claims per response.

  The limits apply at different enforcement boundaries. A response exceeding 25 generated claims must be rejected in full. Silent truncation is prohibited. `ClaimValidator` does not enforce either count limit. Malformed structure, parser-limit violations, and generation-cap violations must remain distinguishable in failure reporting, but all result in no accepted candidate claims. Changing either limit requires a separate explicit decision.

- **C. Prompt limit.** D42 adopts a maximum of 32,768 UTF-8 bytes for the complete deterministic prompt. Measurement is the completed prompt string encoded as UTF-8 immediately before inference. All prompt content is included: fixed instructions, claim schema, evidence references, evidence content, and delimiters. HTTP request serialization overhead is excluded. If the completed prompt exceeds the limit, fail closed with `PROMPT_TOO_LARGE`; do not invoke the model; do not truncate, omit, subset-select, or summarize evidence. This is separate from D39's 262,144-byte Evidence Packet serialization limit and D41's 65,536-byte model response-body cap. Changing the prompt limit requires a separate explicit decision.

- **D. Orchestration deadline.** D42 adopts a maximum 45-second end-to-end orchestration budget:
  1. 5 seconds for deterministic prompt construction.
  2. 35 seconds for the existing D41 inference deadline, unchanged.
  3. 5 seconds for parsing and candidate validation.

  Preserve monotonic deadline measurement; cooperative deadline checks through orchestration entry, prompt construction, inference boundaries, parsing, validation, and result construction; fail-closed deadline handling; and discard of all partial candidate claims and results on deadline expiry. Do not claim arbitrary preemptive cancellation of synchronous parsing. Do not imply that D41's `LocalModelAdapter` deadline remains active after `infer()` returns. This D42 budget does not amend or weaken D41's transport guarantees. Changing the budget requires a separate explicit decision.

- **E. Parser.** D42 adopts the reviewed Jackson 3.1.5 design, including `tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION`, `tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`, bounded structural parsing, and explicit safe failure categories. D40's deferred token-limit policy remains deferred. UTF-8 byte caps are not token limits. No tokenizer, token-count guarantee, or context-window guarantee is invented.

- **F. Evaluation.** D42 adopts the offline evaluation harness design. Preserve human-labelled benchmark fixtures; evidence-category and generation-mode coverage; diagnostic evaluation metrics; offline evaluation separate from the production generation path; and optional model-assisted diagnostics only as a non-authoritative evaluation technique. The offline evaluator may inspect raw model output transiently in memory. It must not persist or log raw prompts or outputs, expose them in ordinary responses, or grant model or evaluator authority. Evaluation cannot accept or approve generated claims.

- **G. Configuration.** D42 records the reviewed recommendation for the initial design: fixed internal constants; no caller-controlled `GenerationOptions` that can weaken adopted ceilings; no runtime configuration that can raise or bypass adopted safety or resource limits. Any future configurability or change to adopted numeric limits requires a separate explicit decision.

- **H. Security and trust boundaries.** D42 preserves D38–D41 unchanged and explicitly retains:
  1. evidence provenance and packet-local references;
  2. untrusted evidence and prompt-injection residual risks;
  3. deterministic `ClaimValidator`;
  4. no semantic-entailment guarantee;
  5. no generator or model authority;
  6. no automatic claim acceptance;
  7. mandatory human review;
  8. no tools or function calling;
  9. no RAG, vector database, MCP, or agent runtime;
  10. no external provider or cloud fallback;
  11. no second-LLM authority;
  12. no endpoint, persistence, frontend, new roles, or execution capability.

  Future endpoint exposure requires a separate future decision.

- **I. Deferred decisions.** D42 explicitly preserves these as deferred:
  1. package name;
  2. evaluation sample sizes and strata;
  3. whether model-assisted diagnostic evaluation will be used;
  4. future endpoint exposure;
  5. token-limit policy.

  D42 does not invent values or silently decide these items.

- **J. Acceptance criteria.** D42 carries forward the approved Slice 3 proposal's future implementation acceptance criteria:
  1. Oversized prompt fails closed before model invocation.
  2. D40's 100-record structural limit is distinct from D42's 25-claim generation limit.
  3. More than 25 generated claims rejects the entire response without truncation.
  4. Deadline expiry during parsing or validation discards all partial claims and results.
  5. Cooperative deadline checks and honest synchronous cancellation semantics.

  These are future implementation acceptance requirements. They are not claims that implementation or tests already exist.

- **K. Accepted residual risks.** D42 preserves these accepted residual risks:
  1. prompt injection cannot be fully prevented;
  2. deterministic validation does not establish semantic entailment;
  3. model digest does not establish per-request cryptographic identity;
  4. evaluation thresholds are not adopted;
  5. human review remains mandatory.

  Accepted residual risks are distinguished from deferred design choices.

- **L. Explicit implementation gate.** D42 adopts the Slice 3 design only. D42 authorizes none of the following: Java implementation; tests or dependencies; configuration changes; endpoints or APIs; persistence or database migrations; frontend or presentation surface; new roles; agent runtime or execution capability. Slice 3 implementation requires a separate subsequent architectural decision and explicit user authorization. D42 does not amend D8 or weaken D38–D41. D42 does not convert D41 into Slice 3 implementation authorization and does not authorize any future endpoint or human-review presentation surface.

- **M. Compatibility.** D42 depends on D38, D39, D40, and D41. It preserves D8 and D28–D31. It does not amend earlier decisions. It creates no execution, approval, proposal-lifecycle, authentication, or provider authority.


## D43 — Phase 14 S2 Slice 3: Bounded Implementation Scope and Authorization Gate

- **Status:** DECIDED AND ADOPTED 2026-11-11. Design adoption and implementation scope. No implementation authorization.
- **Date:** 2026-11-11.
- **Depends on:** D38 (Phase 14 S0 evidence foundation), D39 (Phase 14 S1 deterministic evidence packet assembler), D40 (Phase 14 S2 Slice 1 claim contract and deterministic validator), D41 (Phase 14 S2 Slice 2 local model provider), D42 (Phase 14 S2 Slice 3 design adoption). D43 amends no prior decision.
- **Purpose:** Adopt the reviewed D43 implementation-authorization proposal as the binding implementation scope and acceptance requirements for a future Slice 3 implementation. Adoption of D43 does not authorize implementation. Implementation requires a separate explicit user authorization after D43 adoption.

- **A. Adopted implementation scope.** D43 adopts the reviewed Phase 14 S2 Slice 3 design and its bounded implementation scope from `docs/planning/phase-14/PHASE14_D43_IMPLEMENTATION_AUTHORIZATION_PROPOSAL.md`. The adopted pipeline is:

  EvidencePacket
  -> deterministic prompt construction
  -> existing `LocalModelAdapter`
  -> strict bounded parser
  -> candidate `EvidenceClaim` objects
  -> existing deterministic `ClaimValidator`
  -> transient generation result

  The implementation must reuse the existing boundaries rather than create new provider or validation mechanisms. `EvidencePacketAssembler` remains the sole packet producer. `LocalModelAdapter.infer(String)` remains the sole provider inference method. `ClaimValidator.validate(EvidencePacket, EvidenceClaim)` remains the sole deterministic claim validator. `ClaimWordingScreen.isForbidden(String)` remains the deterministic wording screen. Slice 3 adds only internal prompt construction, strict parsing, generation orchestration, a transient result, and an offline evaluation harness.

- **B. Adopted package recommendation.** D43 records `com.soubhagya.systivex.generation` as the recommended implementation package. D42 deliberately deferred the final package name. The recommendation is non-binding until D43 is adopted and implementation is authorized. Even after adoption, the exact package may be confirmed during implementation planning if actual repository conventions justify a narrowly scoped adjustment. No new architectural module or public API is implied. The generation package may depend on `evidence` and `localmodel`; neither may depend on the generation package.

- **C. Adopted components and record shapes.** D43 adopts the following proposed internal components:
  1. `EvidenceGenerationService` (`public final class`, `@Service`): orchestrate prompt construction, inference, parsing, validation, and transient result creation.
  2. `GenerationResult` (`public record`): `EvidenceReference packetRef`, `EvidenceMode mode`, `GenerationStatus status`, `List<GeneratedClaimResult> claims`, `int claimsParsed`, `int claimsValid`, `int claimsInvalid`. Invariants: `claims` is immutable; failure results contain no candidates; no partial candidate list can escape; `SUCCEEDED` means deterministic validation completed, not human acceptance; no raw prompt, raw model output, stack trace, credential, or sensitive provider detail.
  3. `GeneratedClaimResult` (`public record`): `EvidenceClaim claim`, `ClaimValidationResult validationResult`. The claim uses the existing D40 `EvidenceClaim` type exactly; no second or forked claim schema is permitted. Preserve all existing `EvidenceClaim` fields and invariants: statement, category, evidence references, qualification, limitations, packet reference, and mode.
  4. `GenerationStatus` (`public enum`): `SUCCEEDED`, `NO_OUTPUT`, `PARSE_FAILED`, `PROVIDER_UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESPONSE_TOO_LARGE`, `BUSY`, `PROMPT_TOO_LARGE`, `INVALID_PACKET`. Success and safe-failure statuses are mutually exclusive; every safe-failure status requires an empty candidate collection; success means deterministic validation completed, not human acceptance.
  5. `EvidenceGenerationPromptBuilder` (package-private final class): deterministic prompt construction and UTF-8 size calculation.
  6. `StrictClaimResponseParser` (package-private final class): strict Jackson parsing and structural bound enforcement.

  No new Spring AI starter, provider client, runtime dependency, database dependency, or utility library is authorized.

- **D. Default-off integration.** Generation service availability must be conditional on the actual `LocalModelAdapter` bean, using `@ConditionalOnBean(LocalModelAdapter.class)` or an equivalent explicit availability check. It must not be instantiated with a null provider. A future context test must prove that no `LocalModelAdapter` bean and no `EvidenceGenerationService` bean exist when the provider is disabled, that no generation path can invoke inference when disabled, that enabling a valid provider makes the service available, and that invalid or unavailable provider configuration still fails closed. The provider remains disabled by default (`systivex.local.enabled=false`). Existing D41 fail-closed behavior is unchanged.

- **E. Parser.** D43 adopts the reviewed Jackson 3.1.5 parsing design, including `tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION`, `tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`, bounded structural parsing, and explicit safe failure categories. The implementation must explicitly configure maximum nesting depth of 8, maximum document length of 65,536 bytes, maximum string length of 1,000 characters, and maximum property-name length of 64 characters. Any numeric-length bound is only configured if separately adopted. D40's deferred token-count policy remains deferred. UTF-8 byte caps are not token limits. No tokenizer, token-count guarantee, or context-window guarantee is invented.

- **F. Resource limits.** D43 adopts these constraints:
  1. Prompt cap: maximum 32,768 UTF-8 bytes for the complete deterministic prompt. Measurement is the completed prompt string encoded as UTF-8 immediately before inference. All prompt content is included: fixed instructions, claim schema, evidence references, evidence content, and delimiters. HTTP request serialization overhead is excluded. If oversized, fail closed with `PROMPT_TOO_LARGE`; do not invoke the model; do not truncate, omit, subset-select, or summarize evidence.
  2. D40 structural parser limit: maximum 100 claim records, unchanged.
  3. D42 generation limit: maximum 25 generated candidate claims per response. A response exceeding 25 generated claims must be rejected in full. No silent truncation. `ClaimValidator` does not enforce either count limit. Malformed structure, parser-limit violations, and generation-cap violations must remain distinguishable in failure reporting, but all result in no accepted candidate claims.
  4. Maximum references per claim: 1-20.
  5. Maximum limitations per claim: 0-100, each 1-1,000 characters.
  6. Maximum statement length: 1,000 characters.
  7. Total orchestration budget: 45 seconds, composed of 5 seconds prompt construction, the existing D41 maximum 35 seconds for local inference unchanged, and 5 seconds parsing and candidate validation.
  8. Monotonic deadline measurement and cooperative deadline checks through orchestration entry, prompt construction, inference boundaries, parsing, validation, and result construction. On expiry, stop subsequent processing, discard all partial candidate claims and results, and return `DEADLINE_EXCEEDED`. Do not claim arbitrary preemptive cancellation of synchronous parsing. Do not imply that D41's `LocalModelAdapter` deadline remains active after `infer()` returns. This budget does not amend or weaken D41's transport guarantees.
  9. Raw output retention: none in the runtime path; transient in-memory evaluation only.

  Changing any adopted numeric limit requires a separate explicit decision.

- **G. Deterministic validation and human review.** D43 preserves the existing `ClaimValidator` and `ClaimWordingScreen` contracts. Candidate construction injects `packet.packetRef()` and `packet.mode()` from the packet, never from model output. Passing validation does not mean claim acceptance. All generated claims remain untrusted candidates. Human review remains mandatory. Reference existence establishes provenance only and does not establish semantic entailment.

- **H. Offline evaluation.** D43 adopts the offline evaluation harness scope. Preserve human-labelled benchmark fixtures; evidence-category and generation-mode coverage; deterministic metric calculation; offline evaluation separate from the production generation path; and optional model-assisted diagnostics only as a non-authoritative evaluation technique. The offline evaluator may inspect raw model output transiently in memory. It must not persist or log raw prompts or outputs, expose them in ordinary responses, or grant model or evaluator authority. Evaluation cannot accept or approve generated claims. Do not implement second-LLM evaluation. Keep model-assisted diagnostic evaluation deferred unless a separate decision later authorizes it. Do not invent statistical sample sizes, evaluation thresholds, or quality guarantees.

- **I. Tests and acceptance criteria.** D43 carries forward the approved Slice 3 proposal's future implementation acceptance criteria:
  1. Deterministic prompt output for identical packets.
  2. Prompt UTF-8 exact-boundary and over-boundary tests.
  3. `PROMPT_TOO_LARGE` prevents model invocation.
  4. No evidence truncation, omission, subset selection, or summarization.
  5. Parser rejects malformed JSON, duplicate keys, unknown fields, and structural violations.
  6. D40's 100-record parser cap is independent of the 25-claim generation cap.
  7. Responses containing 25 or fewer generated claims are eligible for deterministic `ClaimValidator` evaluation. Claims that pass deterministic validation remain untrusted candidates and are never automatically accepted. Human review remains mandatory.
  8. More than 25 claims rejects the entire response.
  9. `ClaimValidator` is not incorrectly treated as enforcing either count limit.
  10. Existing reference, category, qualification, limitation, and wording validation remains unchanged.
  11. Prompt construction deadline enforcement.
  12. D41 inference deadline and transport tests remain green.
  13. Parsing and validation deadline expiry discards every partial claim and result.
  14. No false preemptive cancellation guarantees.
  15. Evaluator raw-output access is transient and cannot leak to logs, storage, or ordinary responses.
  16. No endpoint, persistence, frontend, roles, or execution capability.
  17. Existing backend tests remain green.

  Tests must be separated into unit tests, parser adversarial tests, orchestration tests, adapter interaction tests, offline evaluation tests, and full regression tests. These are future implementation acceptance requirements. They are not claims that tests or implementation already exist.

- **J. Implementation sequencing.** Recommended sequence:
  1. Prompt contract and deterministic prompt builder.
  2. Bounded strict parser.
  3. Generation orchestration and deadline propagation.
  4. Existing `ClaimValidator` integration.
  5. Transient generation result.
  6. Offline evaluation harness.
  7. Adversarial and security regression tests.
  8. Full regression and independent implementation review.

  Prompt construction must be complete and deterministic before parser and orchestration work. Parser must be complete before orchestration can validate candidates. Deadline propagation must be implemented with orchestration, not after it. Evaluation harness depends on the generation result contract. Full regression must include all D39, D40, and D41 tests. No future slice, endpoint, presentation surface, or execution feature may be included.

- **K. Risks and rollback.** Preserved risks: prompt injection cannot be fully prevented by prompt construction; deterministic validation does not establish semantic entailment; model output may be plausible but unsupported; model digest verification is not per-request cryptographic identity (D41 G-4); synchronous parsing has bounded cancellation limitations; evaluation metrics may not predict real-world evidence distributions; implementation may accidentally conflate the 100-record parser limit with the 25-claim generation cap; implementation may accidentally expose raw prompt or model output. Rollback strategy: provider disabled by default (`systivex.local.enabled=false`); generation service conditional on the provider bean or failing with a fixed safe error when absent; implementation isolated in the generation package; rollback removes the generation package and tests without changing S1, S2, endpoints, persistence, or roles; no database migration or schema change is authorized; existing S1 and S2 behavior remains unchanged when generation is absent or disabled.

- **L. Deferred choices.** D43 does not resolve these D42-deferred items: exact package name, although `com.soubhagya.systivex.generation` is recommended; evaluation sample sizes and strata; whether model-assisted diagnostic evaluation will be used; future endpoint exposure; token-limit policy. D43 does not invent thresholds, token counts, or sample sizes.

- **M. Explicit implementation gate.** D43 adopts the bounded Slice 3 implementation scope and acceptance criteria. D43 does not authorize implementation. No source-code, test, dependency, configuration, endpoint, persistence, frontend, role, or execution-capability change may begin until the user separately and explicitly authorizes Slice 3 implementation after D43 adoption. Coding is not in progress or authorized. Design adoption, implementation authorization, and implementation completion remain distinct states.

- **N. Compatibility.** D43 depends on D38, D39, D40, D41, and D42. It preserves D38 evidence provenance; D39 packet assembly; D40 claim and validator contracts; D41 local-model integration and transport guarantees; and D42 adopted design boundaries. It does not amend D8 or weaken D38-D42. It creates no execution, approval, proposal-lifecycle, authentication, or provider authority. It does not convert D41 into Slice 3 implementation authorization and does not authorize any future endpoint or human-review presentation surface.

- **O. Scope restriction.** D43 adopts implementation scope only. It does not authorize public or external endpoints; persistence, schema changes, or migrations; frontend or presentation surface; new roles; agent execution or mutation; external providers or cloud fallback; RAG, vector databases, MCP, or second-LLM authority; new dependencies outside the reviewed proposal; token-limit policy changes; or automatic acceptance of generated claims. Preserve all D38-D42 decisions.



## Supersession rule

New decisions amend this file with date and rationale; they never silently edit
history. If reality diverges from any entry, `PROJECT_STATUS.md` records the
discrepancy first.
