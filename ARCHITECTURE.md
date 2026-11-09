# Systivex Architecture

Status: **target architecture (planned) + current implementation status**
(documentation reviewed 2026-11-03; last automated runtime test verification
2026-11-03, Phase 13 S5 backend regression; last live backend/frontend
verification 2026-11-03, Phase 13 S5). Phase 13 S5 is implemented,
automated-verified, merged via PR #20 (merge commit
`10472f26a54fa488705fe5fbcdaf10bd18fed3cc`), and **passed local live
verification on 2026-11-03**: 20/20 backend checks plus a headless frontend
smoke. Automated tests and GitHub CI remain distinct from live verification.
Nothing below beyond "Current implementation" should be read as implemented.

## 1. High-level target architecture

```text
                        ┌─────────────────────────────────┐
                        │   SYSTIVEX CONTROL PLANE        │
                        │   (modular monolith, Spring)    │
                        │                                 │
  code/arch/data ──────▶│  System Twin                    │
  runtime signals ─────▶│  (code+arch+data+behavior model)│
                        │         │                       │
  change proposal ─────▶│  Change Intelligence:           │
                        │  impact / simulation / risk     │
                        │         │                       │
                        │  Policy + Approval Gate         │◀── human / policy approval
                        │         │                       │
                        │  Controlled Execution +         │
                        │  Verification                   │──▶ target environment
                        └─────────────────────────────────┘
                                           │
                                           ▼
                        ┌─────────────────────────────────┐
                        │   TARGET SOFTWARE ENVIRONMENT   │
                        │   (future microservices under   │
                        │    observation / management)    │
                        └─────────────────────────────────┘
```

Target flow: **observe → model (twin) → propose → simulate → assess risk →
authorize → execute under control → verify**.

## 2. Control-plane vs. target-environment separation

| Concern | Systivex control plane | Target environment |
|---|---|---|
| Role | Thinks, models, gates, authorizes, verifies | Is observed and acted upon |
| Shape | **Modular monolith** (Spring Boot) | **Microservices** (4 Spring Boot apps, Phase 2A) |
| Reasoning | Agent + twin-grounded analysis (planned) | Application business logic |
| Authority | Grants or denies consequential actions | Carries out only authorized actions |
| Trust | Authoritative for decisions and audit | Untrusted until verified |

This separation is deliberate: the thing that decides must not be the thing
being changed, and the thing being changed must never self-authorize.

## 3. Modular-monolith control plane (target)

The control plane is a single Spring Boot deployment with strict module boundaries.
The `twin` module exists in outline (`model`, `repository`, `service`, `api`
packages) plus the Phase 4 `observation` package (scanner + sync, repository
connector only), the Phase 5 `intelligence` package (read-only impact
analysis, plus Phase 7 risk classification and Phase 8 hypothetical
simulation), the Phase 6 `telemetry` package (pull-based runtime
annotation only), the Phase 9 `policy` package (advisory evaluation only),
the Phase 10 `security` package (JWT resource server, role policy,
fail-closed configuration), and the Phase 11 `proposal` package (governed
proposals, approvals, and tamper-evident audit); the remaining target
modules (`execution`, `verification`) are **not yet created** — no
placeholder packages exist for them by decision:

- `twin` — System Twin model and connectors (**model + persistence + minimal
  CRUD API exist**; repository connector exists since Phase 4, health/counter
  runtime annotation exists since Phase 6, trace/log presence still planned)
- `intelligence` — change impact, change risk, and hypothetical simulation
  (**deterministic read-only impact traversal exists since Phase 5**:
  `GET /api/v1/twin/impact`; **ordinal risk classification exists since
  Phase 7**: `GET /api/v1/twin/risk` over impact paths plus runtime
  metadata; **hypothetical change simulation exists since Phase 8**:
  `POST /api/v1/twin/simulations` replays impact + risk over an in-memory
  hypothetical graph and returns `simulated: true` — it is advisory and
  never executes a real change; the separate `policy` package consumes this
  simulation for Phase 9 advisory evaluation)
- `policy` — policy evaluation (**advisory evaluation exists since
  Phase 9**; the governed proposal and approval workflow is implemented in
  the separate `proposal` package since Phase 11 — see `DECISIONS.md` D29)
- `proposal` — governed change proposals and tamper-evident audit
  (**implemented since Phase 11 S1–S6**: immutable revision persistence with
  a `DRAFT`→`ASSESSED`→`AWAITING_APPROVAL`→`APPROVED`/`REJECTED` lifecycle
  (plus `WITHDRAWN`/`EXPIRED`/`SUPERSEDED`), draft/assess/submit/amend/
  withdraw and read APIs, `TWIN_APPROVER` approval/rejection with separation
  of duties and freshness re-checks, a hash-chained append-only audit with
  `GET /api/v1/twin/audit/verify`, and lazy expiry (7-day assessment TTL,
  30-day hard maximum; no scheduler). `APPROVED` is a terminal permission
  record and executes nothing — see `DECISIONS.md` D29)
- `execution` — controlled, auditable action dispatch (**design only, not
  implemented**: constrained by `DECISIONS.md` D30 and gated by the D31
  feasibility gate; no current simulation operation has a qualifying,
  independently verifiable target actuator)
- `verification` — post-change checks against twin + runtime (planned)
- `observation` — code/arch/runtime ingest (**repository connector exists
  since Phase 4**: read-only scan of `backend/target-services/` + explicit
  sync trigger; telemetry ingest still planned)
- `api` — REST + SSE surface (initial twin CRUD exists; only Actuator health
  existed before Phase 1)

Module rules (binding for future work): modules communicate through explicit
APIs; no cross-module persistence access; every consequential action crosses
the policy/execution boundary (see §6).

The **frontend is a local-development-only read-only/advisory operator console**
(Phase 13 S1 merged via PR #8, `DECISIONS.md` D33; S2 via PR #12 and S3 via
PR #14 on 2026-10-29; S4 via PR #18 on 2026-11-02 under D36; S5 implemented
under D37 and merged via PR #20 on 2026-11-03; it is not a control-plane
module). It lives in `frontend/` as a React + Vite +
**JavaScript** (no TypeScript) app. The S1 System Twin explorer reads existing
`TWIN_READ` GET endpoints; S2 adds a live-only Impact & Risk view that reads the
existing impact and risk GETs for one root UUID; S3 adds a live-only Audit
Integrity view that reads only `GET /api/v1/twin/audit/verify` and never repairs
or mutates the chain; S4 adds a live-only, read-only proposal governance view
over five `TWIN_READ` GETs with no lifecycle mutation. S5 adds a live-only
**Simulation & Policy** view that calls only
`POST /api/v1/twin/simulations` and
`POST /api/v1/twin/policy-evaluations` with `TWIN_ANALYZE`, one loaded root,
optional operations, separate explicit actions/results, and no automatic or
combined request. The two S5 POSTs are advisory and non-persistent; the policy
endpoint independently re-runs simulation server-side. S5's local live
verification passed on 2026-11-03 against standalone local Keycloak 26.8.1 on
`127.0.0.1:8180`, the backend on `127.0.0.1:8090`, and Vite on
`127.0.0.1:5173`; the checks covered 401 without a token, 403 for a
`TWIN_READ`-only identity, a successful `TWIN_ANALYZE` baseline simulation,
an independent policy result (`REVIEW`, `policy-v1`), unchanged persistence,
and a headless browser smoke. This remains local-development verification, not
production identity or hosting. All S2–S5 views are excluded from synthetic
preview mode. The console adds no backend endpoint or
CORS rule: the Vite dev server binds to `127.0.0.1` and proxies `/api` to
`http://127.0.0.1:8080` by default, or to the loopback port selected by
`SYSTIVEX_BACKEND_PORT` (the protected local config here uses `8090`). It
stays local-development-only until the serving and
identity requirements for a shared environment are reviewed, and no
production/static hosting or live OIDC login exists.

## 4. Target microservices (Phase 2B persistent runtime; observation planned)

Four independently deployable services under `backend/target-services/` form the
first **target environment** — the kind of system the twin will eventually model
and the control plane will act upon. They are ordinary business services with no
Systivex awareness in them:

```text
client ──▶ gateway-service (:8081, POST /api/v1/checkout)
               │ HTTP (RestClient, URL from config)
               ▼
           order-service (:8082, POST /api/v1/orders) ──▶ order_db (orders)
               ├──▶ inventory-service (:8084, POST /internal/v1/inventory/reserve) ──▶ inventory_db
               │         (inventory_items + inventory_reservations)
               └──▶ payment-service (:8083, POST /internal/v1/payments/authorize) ──▶ payment_db
                         (payments)
```

Database ownership (binding, see `DECISIONS.md` D17):

| Service | Database / user | Tables | Reads/writes other DBs? |
|---|---|---|---|
| order-service | `order_db` / `order_app` | `orders` (PENDING → CONFIRMED/FAILED) | Never |
| payment-service | `payment_db` / `payment_app` | `payments` (AUTHORIZED/DECLINED, both recorded) | Never |
| inventory-service | `inventory_db` / `inventory_app` | `inventory_items`, `inventory_reservations` | Never |
| gateway-service | None (stateless edge) | — | N/A |

The control-plane database (`systivex` / `systivex_app`) is separate and
shares nothing with these three. Each service migrates its own schema with its
own Flyway history (`V1` schema everywhere, plus a `V2` dev seed of
`SKU-1001 × 10` in inventory only); Hibernate validates (`ddl-auto=validate`)
and never migrates.

- Each has its own pom, Maven wrapper, application class, and Actuator health.
- Downstream URLs and datasource credentials come from configuration (env vars
  with localhost defaults), never from source. Fixed dev ports (8081–8084);
  control plane stays on 8080.
- Each target service enforces loopback at startup: a last-ordered server
  factory customizer defaults an unset `server.address` to `127.0.0.1` and
  rejects wildcard or non-loopback values before bind. This applies after
  normal Boot configuration binding, so ignored local properties, profiles,
  environment variables, CLI arguments, and `SPRING_APPLICATION_JSON` cannot
  bypass it. When `management.server.port` is separate, a child-context
  `@ManagementContextConfiguration` applies the same rule to
  `management.server.address`.
- Checkout persists at every step: PENDING row first, then reserve, then
  authorize, then CONFIRMED — or FAILED when any downstream step fails. Failed
  attempts are recorded, never dropped and never falsely CONFIRMED.
- Consistency is per-database, not global: the three services commit in their
  own local transactions with no coordinator. A FAILED order can coexist with
  a standing inventory reservation; a crash between the PENDING insert and the
  final update leaves a PENDING row. This is documented behavior (the honest
  record a future reconciliation story needs), not atomicity.
- Inventory concurrency is handled locally: the reserve path takes a
  row-level write lock (plus a `@Version` column) so concurrent reserves for
  the same product serialize instead of overselling. No Redis, no broker.
- Payment still declines amounts above 5000.00 by deterministic rule (no
  provider integration); declines are now persisted as DECLINED rows.
- Failures stay controlled: 400 validation, 422 business rejection, 503 order
  level (including order-storage outage), 502 gateway level, sanitized bodies
  throughout — no SQL text or stack traces on the wire.
- No Kubernetes, no message broker, no service mesh at this stage.
- The control plane does not call, scrape, or model these services yet. They
  remain strictly outside the control-plane trust boundary: untrusted until
  verified, never self-authorizing, carrying only their own local logic.

## 4a. Observability instrumentation (Phase 3A; collection verified, consumption planned)

Every service (four target services plus the control plane) emits
OpenTelemetry traces and Prometheus metrics from the same two
Boot-managed dependencies. Collection is local only:

 ```text
 gateway/order/payment/inventory (+ control plane)
   │  OTLP traces + logs (:4318)   │  /actuator/prometheus scrape
   ▼                               ▼
 ┌─────────────────────────────────────────────────┐
 │  OTel Collector (local, compose)                │
 │  traces → debug exporter (collector stdout)     │
 │  metrics → Prometheus receiver → :8889          │
 │  logs → OTLP/HTTP exporter → Loki :3100         │  (Phase 3B-2)
 └─────────────────────────────────────────────────┘
          │                        │  Prometheus scrape (:8889 only)
          ▼                        ▼
 ┌──────────────────┐    ┌──────────────────┐
 │  Loki :3100       │    │  Prometheus :9090 │  (Phase 3B-1)
 │  (Phase 3B-2)    │    └──────────────────┘
 └──────────────────┘              │  PromQL
          │                        ▼
          │              ┌──────────────────┐
          └─────────────▶│  Grafana :3000    │  (provisioned)
                         │  Metrics dashboard│  (Phase 3B-1)
                         │  Explore logs     │  (Phase 3B-2)
                          └──────────────────┘
 ```

Compose publishes the seven host-facing telemetry ports on `127.0.0.1`
only. Container-internal receiver bindings and container-to-container paths
are unchanged; metrics stay pull-based through the collector's Prometheus
receiver and Prometheus still scrapes only the collector's `:8889`.

Phase 3B-1 adds visualization without touching the collection path:
Prometheus scrapes only the collector's `:8889` exposition (never the
Java services directly — no duplicate collection). The collector stamps
all app metrics with its scrape-job resource (`service_name="systivex"`),
so per-service identity on this path comes from the receiver-kept
`server_port` label, mapped once in `prometheus.yml` to a `service`
label (control-plane/gateway-service/order-service/payment-service/
inventory-service). The single `Systivex Metrics` dashboard (provisioned
from `observability/grafana/`, no browser setup) shows services
reporting, request rate, requests by outcome, average latency, JVM heap,
process CPU, and the checkout/inventory/payment business counters — all
low-cardinality labels only; OTel histograms arrive without buckets on
this path, so latency is a sum/count average, not a histogram quantile.

- Tracing: automatic server/client HTTP spans everywhere; trace context
  (W3C `traceparent`) propagates gateway → order → inventory + payment —
  verified as one trace ID across all four services in the collector log.
  Three custom business spans mark what automatic instrumentation cannot:
  `checkout` (order), `inventory.reservation`, `payment.authorization`.
   Recorded observation errors are sanitized per deployable
   (`ObservationErrorSanitizer` in its own handler group ordered before
   Boot's tracing handler group, so the tracing handler records the
   sanitized error): type name + HTTP status only, no response
   bodies or business messages — see `DECISIONS.md` D19, `THREAT_MODEL.md`
   §3a.
- Metrics: standard HTTP server/client, JVM, and process families plus six
  tag-free business counters (`checkout.success/failure`,
  `inventory.reservation.success/rejected`,
  `payment.authorization.success/declined`).
- Logs: console output is unchanged (`traceId`/`spanId` correlation;
  outcome + IDs, never payloads or secrets), and the same records now also
  ship over OTLP to the collector and into Loki (Phase 3B-2, see below).
  Boot wires the OTLP log exporter but installs no logging bridge, so each
  deployable adds the smallest one: the OTel Logback appender (version
  paired with Boot's managed OTel SDK, see `DECISIONS.md` D21) declared in
  `logback-spring.xml` plus a tiny installer bean. Loki indexes
  `service.name` only; trace/span IDs travel as structured metadata —
  searchable (`| traceId="<hex>"`) but never stream labels, so no
  per-trace/per-request cardinality. Grafana Explore is the log UI; no log
  dashboard, no Tempo.
- Sampling is 1.0 (local development only). The control plane emits but
  consumes nothing — twin ingestion of telemetry is future work (D18).

## 5. System Twin concept (persistence exists; reasoning still planned)

The System Twin is the planned authoritative model of a target system, joining:

- **code** (structure, dependencies),
- **architecture** (services, boundaries, contracts),
- **data** (stores, flows, schemas),
- **runtime behavior** (health, metrics, traces, events).

Agent reasoning must be grounded in the twin and in live backend state —
never in LLM output alone. What exists today is the persistence footing:
`system_entity` / `system_relationship` tables (Flyway V1), JPA mappings kept
in lockstep by `ddl-auto=validate`, and CRUD behind DTOs — plus, since
Phase 4, the first real content: a read-only repository connector that
models the four target services, their databases/tables, APIs, and
dependencies as connector-owned rows (stable `target-*` external refs,
provenance in metadata) — plus, since Phase 5, the first reasoning over
that content: a read-only deterministic impact traversal (`intelligence`
package, `GET /api/v1/twin/impact`) that reports potentially affected
components with ordered evidence paths over persisted edges — plus, since
Phase 6, validated runtime annotation: health plus the six allowlisted
business counters pulled from the loopback Actuator surface into a
namespaced `runtime` metadata key (`telemetry` package,
`POST /api/v1/twin/observations`; trace/log presence deliberately absent).
Current packages are `twin`, `observation`, `intelligence`, `telemetry`,
`policy`, `security`, `proposal`, and `evidence`; governed proposals,
approvals, and the tamper-evident audit exist since Phase 11, but no
execution or verification module exists.

## 6. Trust boundaries

### 6a. Agent / control boundary (planned)

- The agent proposes; the control plane disposes.
- The agent has **no direct access** to production actions, credentials,
  or the target environment.
- Every tool call with side effects is mediated, validated, and logged
  by the control plane.

### 6b. Policy / risk / execution boundary (planned)

```text
agent proposal → risk assessment → policy evaluation → (approval if high-risk)
→ authorized execution → audit record → verification
```

- Consequential actions require **control-plane authorization**.
- High-risk actions additionally require **explicit human/policy approval**.
- All consequential actions must produce an **auditable record**
  (who/what/policy-decision/result).

See `THREAT_MODEL.md` for the principles these boundaries enforce.

### 6c. Evidence boundary for agent-assisted analysis (Phase 14 S0 design; Phase 14 S1 implemented under D39)

Future agent-assisted analysis is a read-only explanation layer over existing
deterministic System Twin evidence. D38 authorized design only; D39
authorized and Phase 14 S1 implemented the deterministic evidence packet
assembler described below. No agent runtime, model provider, prompt, or
narrative generation is implemented. The future trust flow is:

System Twin and runtime observations
→ deterministic impact and risk
→ hypothetical simulation
→ advisory policy evaluation
→ evidence selection and qualification
→ human-readable analysis

Evidence categories remain distinct and are never collapsed into one
confidence score: `OBSERVED_FACT`, `DETERMINISTIC_FINDING`,
`HYPOTHETICAL_RESULT`, `GOVERNANCE_RECORD`, `AUDIT_EVIDENCE`.

The conceptual evidence envelope is transient and packet-scoped, not a Java
record, DTO, database entity, or API contract. It carries packet-local
references, category, original source/resource reference, entity or proposal
scope, source-provided version where available, source timestamps with their
original semantics, structured content from approved DTO fields, source-provided
limitations, and integrity metadata only when an existing source supplies it.
Claims reference one or more evidence items and carry qualification/support
status and explicit limitations. No durable evidence IDs, global snapshot IDs,
source hashes, timestamps, confidence scores, or freshness guarantees are
invented where the existing APIs do not provide them.

Timestamps retain their distinct semantics: twin `createdAt`/`updatedAt`,
runtime `observedAt`, impact/risk `snapshotObservedAt`, the four simulation
impact/risk timestamps, proposal assessment `snapshotObservedAt`, and
proposal/audit creation and occurrence timestamps. They are never merged into
one `lastUpdated`. Runtime observation receipts are per-request with no
persisted run history. Risk freshness uses the existing 30-minute `risk-v1`
rule only where runtime metadata supports it; `RiskResponse` has no
impact-style truncation/cycle fields and the model must not fabricate them.

Source authority is preserved: impact proves structural reachability, not
confirmed breakage; risk is advisory under `risk-v1`; simulation is
hypothetical and non-persistent; policy `ALLOW` is not authorization; proposal
assessment is bound to stored canonical operations, assessment hash, and twin
fingerprint; an ordered audit-event list is not proof of chain integrity. Hashes
retain their original scope and purpose.

Phase 14 S1 is not authorized by D38. Agent-generated narratives, model
provider integration, and tool mediation remain separately gated.

#### Phase 14 S1 implementation (D39)

The internal `evidence` package implements the assembler boundary. There is
no REST endpoint, no persistence, no caller-identity input, and no agent
runtime: `EvidencePacketAssembler` is an internal, deterministic, read-only
`@Service` that builds transient packets from the existing owning domain
services only, through one `EvidenceSource` adapter per source family
(twin entities/relationships, twin runtime observations, impact, risk,
simulation, policy, proposal revision, per-proposal audit events). Adapters
map already-loaded source data into immutable evidence items using strict
allowlists; free-form proposal text, identity details, unallowlisted
metadata, and credentials never enter a packet.

Assembly is driven by one validated request carrying exactly one mode —
`BASELINE` (resolved impact + risk), `SIMULATION` (resolved simulation
only), or `POLICY_EVALUATION` (resolved simulation, then resolved policy
evaluation; the unbounded `evaluate(SimulationRequest)` path is never used).
Roots (1–10, `maxDepth` 1–10 default 6, `includeTypes` default
SERVICE/API/DATABASE/TABLE) and operation lists (maximum 100, rejected
before `SimulationService`, never truncated) are validated against the
bounded graph snapshot from `TwinService.readGraphSnapshot()` — never the
unbounded list methods. A truncated or inconsistent snapshot refuses all
graph analysis and is recorded as `UNAVAILABLE` with explicit limitations;
proposal evidence uses only the bounded latest/selected revision lookup and
audit evidence the bounded 100-event read (exact omitted counts); global
chain verification is never invoked and a partial audit list never implies
chain integrity.

Packets carry packet-local `P<n>`/`E<n>` references assigned only after
deterministic ordering (category, then source), source statuses
`AVAILABLE`/`UNAVAILABLE`/`OMITTED`/`TRUNCATED`, included/omitted counts,
and source limitations. Limits are enforced at the boundary: 100 items per
source family, 1,000 items total, and 256 KiB (262,144 bytes) of serialized
UTF-8 JSON measured on the final packet — over-size fails with a typed
`PACKET_TOO_LARGE` error, never with byte-truncated JSON or silently
dropped evidence. Assembly runs in one read-only `READ_COMMITTED`
transaction; `READ_COMMITTED` is not a globally consistent snapshot, and
source timestamps keep their original per-source semantics. The assembler
supplies evidence only: it generates no claims or narratives, authorizes
nothing (policy `ALLOW` remains advisory), approves no proposal, and
executes no change.

#### Phase 14 S2 Slice 1 (authorized under D40, not yet implemented)

D40 authorizes S2 Slice 1 only: a provider-independent claim contract and
deterministic validator over S1 packets. Claims (statement, category,
`E<n>` references, qualification, limitations, packet reference, mode) are
accepted only after deterministic validation — schema, packet identity,
reference existence, category consistency, qualification/status propagation
(32-pair table, `UNCERTAIN` floor for `UNAVAILABLE`), limitation
inheritance, mode-aware insufficiency (9-family × 3-mode applicability
matrix), and wording screen. Provenance validation proves which packet
items a claim cites; it does **not** prove the cited items support the
statement — semantic entailment remains for human review, the authority of
record. Slice 1 adds no provider, model, prompt, endpoint, persistence, or
generation capability. Slice 2 (Spring AI + local Ollama per D8) and
Slice 3 (generation + evaluation) remain separately gated; D8's deferred
status is unchanged. No agent runtime exists.

## 7. API shape (initial REST exists)

- **REST** for twin queries, change proposals, approvals, execution records.
  Implemented so far: twin entity/relationship CRUD under `/api/v1/twin`
  (no filtering/pagination yet) plus read-only impact analysis
  (`GET /api/v1/twin/impact`: 1–10 starts by UUID or external ref,
  `maxDepth` 1–10 default 6, `includeTypes` default
  SERVICE/API/DATABASE/TABLE; deterministic BFS with FORWARD/REVERSE
  evidence paths, per-path revisit cycle guard, 500-node / 2000-path caps,
  400 on invalid input, 404 on unknown identifiers) plus runtime
  observation (`POST /api/v1/twin/observations`: `{}` default, loopback
  only with 403 off loopback, health + six allowlisted counters into the
  namespaced `runtime` metadata key, bounded receipt, 400/404/409
  fail-closed with full rollback).
  Governed proposals are implemented since Phase 11 S1–S6
  (`POST /api/v1/twin/proposals` with `.../{key}/withdraw|assess|submit|
  approve|reject`, `PATCH .../{key}`, and list/latest/revisions/audit reads)
  plus hash-chained audit verification (`GET /api/v1/twin/audit/verify`);
  none of this dispatches a target change — `APPROVED` is a terminal
  permission record, and controlled-execution *records* remain unimplemented
  design (D30/D31).
  Change-risk assessment (`GET /api/v1/twin/risk`: same `from`/`maxDepth`/
  `includeTypes` conventions as impact; ordinal `LOW`/`MEDIUM`/`HIGH`/
  `UNKNOWN` levels with sorted reasons and `uncertain`/`stale` flags over
  impact depth, path fan-out, service health, cumulative failure counters,
  and 30-minute freshness; read-only, advisory, never authorization —
  see `DECISIONS.md` D25);
  authentication (Phase 10, see `DECISIONS.md` D28): the whole
  `/api/v1/twin/*` surface requires JWT bearer tokens —
  twin/impact/risk GETs `TWIN_READ`, twin POSTs `TWIN_ADMIN`,
  simulations/policy-evaluations `TWIN_ANALYZE`, sync/observations
  `TWIN_OPERATOR` (loopback guard still authoritative), proposal
  approve/reject `TWIN_APPROVER` (Phase 11 S5; never inherited from
  `TWIN_ADMIN`);
  `GET /actuator/health` anonymous (status only),
  `/actuator/prometheus` requires `TWIN_READ`, all else denied.
- **Read-only/advisory operator console (S1–S5 merged; S5 via PR #20; local
  live verification passed 2026-11-03; local development only).**
  `DECISIONS.md` D33 scoped the `frontend/` React + Vite + JavaScript console to
  existing `TWIN_READ` GET endpoints: S1 (PR #8) reads
  `/api/v1/twin/entities[/{id}]` and
  `/api/v1/twin/relationships[/{id}]`; S2's Impact & Risk view (PR #12) reads
  `/api/v1/twin/impact?from=<UUID>` and `/api/v1/twin/risk?from=<UUID>` for one
  selected root; S3's Audit Integrity view (PR #14) reads only
  `/api/v1/twin/audit/verify` and never repairs or mutates the chain; S4 (PR #18,
  D36) reads the five proposal GETs without lifecycle mutation. S5 (D37) adds
  only `POST /api/v1/twin/simulations` and
  `POST /api/v1/twin/policy-evaluations` with `TWIN_ANALYZE`, separate explicit
  actions and retained results, no automatic/combined request, and no
  persistence, approval, execution, or proposal mutation. No API surface is
  added and no CORS rule is configured; the Vite proxy makes it same-origin
  during development. All S2–S5 views are live-only. No slice changes the
  execution boundary, and S5 does not change backend security or roles.
- **SSE** for streaming long-running analysis/simulation progress (planned).
- No WebSockets at this stage (see `DECISIONS.md`).

## 8. Current implementation status (documentation reviewed 2026-11-03; last automated backend verification 2026-11-03; last live backend/frontend verification 2026-11-03, Phase 13 S5; Phase 13 S1–S5 merged, S5 local live verification passed)

Implemented:

- `backend/`: Spring Boot 4.1.1, Java 21 (`release 21`), Maven build.
- Dependencies: `webmvc`, `validation`, `actuator`, `data-jpa`,
  `spring-boot-starter-flyway` + `flyway-database-postgresql`, `postgresql`
  driver, `spring-boot-starter-opentelemetry`,
  `micrometer-registry-prometheus` (+ test: `webmvc-test`,
  `restclient-test`, `testcontainers-postgresql`). Nothing else.
- `twin` module: `SystemEntity` / `SystemRelationship` entities (UUID keys,
  closed enum types, JSONB metadata via native Hibernate mapping), two
  repositories, a small transactional service, DTO-based REST controller,
  and a narrow exception handler (400/404/409).
- Persistence: PostgreSQL 18, Flyway `V1__create_system_twin.sql` runs at
  startup; `spring.jpa.hibernate.ddl-auto=validate`, Hikari defaults.
- Local config stays out of Git: live `application.properties` ignored, only
  `application.example.properties` (password via `SYSTIVEX_DB_PASSWORD`) tracked.
- Verified: `./mvnw clean test` → 29 tests, 0 failures (8 service unit +
  3 migration + 6 repository + 11 API + 1 contextLoads, Testcontainers PG 18);
  `./mvnw package -DskipTests` → executable jar; local run against PostgreSQL →
  Flyway migrates V1, `GET /actuator/health` → `{"status":"UP"}`,
   entity/relationship REST cycle + 400/404 paths exercised.
- Target environment persistence (Phase 2B, see §4): the four target services
  now run against service-owned PostgreSQL databases with per-service Flyway
  histories, validated JPA mappings, persistent orders / payments /
  inventory+reservations, and a checkout flow backed by real databases with a
  documented per-database consistency model.
- Observability (Phase 3A, see §4a): OTLP + Prometheus instrumentation in all
  five deployables, error redaction at the observation boundary, local
  collector pipeline (`observability/`), distributed trace across the
  checkout chain verified in the collector log, business counters and
  correlated logs verified live and in tests (85 tests total:
  control plane 31, gateway 8, order 18, payment 12, inventory 16).
- Twin observation (Phase 4): read-only repository connector + explicit
  sync trigger in the control plane (`observation` package); 15 entities /
  18 edges modelling the target environment, verified in tests (control
  plane suite now 51: 31 pre-existing + 20 new).
- Change-impact analysis (Phase 5): read-only deterministic BFS over the
  persisted twin graph in the control plane (`intelligence` package);
  `GET /api/v1/twin/impact` returns impacted services/APIs/databases/
  tables with ordered FORWARD/REVERSE evidence paths and per-step
  explanations (see `DECISIONS.md` D23). No migration, no new
  dependencies; verified in tests (control plane suite now 88: 51
  pre-existing + 37 new).
- Runtime observation (Phase 6): pull-based health/counter annotation in
  the control plane (`telemetry` package);
  `POST /api/v1/twin/observations` pulls loopback Actuator health plus the
  six allowlisted business counters into the namespaced `runtime`
  metadata key of matched `target-service:*` rows (one transaction,
  fail-closed, bounded receipt; see `DECISIONS.md` D24). No migration, no
  new dependencies; trace/log presence deliberately absent.
- Change-risk assessment (Phase 7): ordinal classification in the control
  plane (`intelligence` package); `GET /api/v1/twin/risk` joins the Phase 5
  impacted set with Phase 6 runtime facts (depth base, 5+ path fan-out,
  health, cumulative failures, 30-minute freshness; sorted reasons,
  uncertainty flags; see `DECISIONS.md` D25). No traversal duplication, no
  migration, no new dependencies, no writes.
- Change simulation (Phase 8): read-only before/after comparison in the
  control plane (`intelligence` package);
  `POST /api/v1/twin/simulations` validates a change proposal
  (`REMOVE_ENTITY` with incident-edge cascade, `ADD_RELATIONSHIP`,
  `REMOVE_RELATIONSHIP`; fixed 400/404/409 order, canonical duplicate
  detection) and replays the Phase 5 traversal plus the Phase 7
  classification over identical roots and settings on the baseline and
  on an in-memory hypothetical graph. Returns deterministic deltas,
  baseline/hypothetical counts, explicit truncation/uncertainty
  limitations, and `simulated: true` with the `SIMULATED — NOT EXECUTED`
  advisory (see `DECISIONS.md` D26). No traversal or classification
  duplication, no migration, no new dependencies, no writes of any kind.
- Advisory policy evaluation (Phase 9): deterministic ALLOW/REVIEW/DENY
  classification in the control plane (new `policy` package);
  `POST /api/v1/twin/policy-evaluations` accepts the exact Phase 8
  proposal wire format, re-runs the simulation server-side (never trusts
  caller-supplied risk), and evaluates the computed hypothetical result
  against the immutable `policy-v1` ruleset (HIGH/truncation → advisory
  DENY; increase/unknown/uncertain → REVIEW; else advisory ALLOW, never
  authorization). Bounded summary response with fired rules, stable
  codes, and explicit non-authorization wording (see `DECISIONS.md`
  D27). No persistence, no approval workflow, no execution, no new
  dependencies, no writes of any kind.
- Control-plane authentication (Phase 10): OAuth2 JWT resource server in
  the control plane (new `security` package); fail-closed
  `systivex.security.jwt.*` configuration (trusted issuer mandatory, no
  security-off mode); allowlist-only role mapping with `TWIN_ADMIN`
  inheritance (never `TWIN_APPROVER` — Phase 11 S5 approval/rejection
  endpoints require that explicitly granted role); per-endpoint role
  policy with loopback guards still authoritative behind
  `TWIN_OPERATOR`; anonymous status-only health,
  `TWIN_READ`-restricted Prometheus metrics, deny-by-default otherwise
  (see `DECISIONS.md` D28). Automated JWT tests use local test keys; the
  local Keycloak development setup has been used for manual token validation.
  Production issuer configuration and live OIDC sign-in remain unimplemented
  and are not claimed.
- Governed change proposals (Phase 11 S1–S6, see §3 and `DECISIONS.md`
  D29): Flyway `V2__proposal_governance.sql` adds immutable
  revision-numbered `proposal_revision`, `audit_event`, and the singleton
  `audit_chain_head`; the `proposal` package appends a tamper-evident
  SHA-256 hash chain (`audit-event-v1`) transactionally under the head-row
  lock and verifies it read-only via `GET /api/v1/twin/audit/verify`;
  draft, assessment, submission, amendment, withdrawal, and read APIs are
  served with `TWIN_APPROVER` approval/rejection, `(issuer, subject)`
  separation of duties, freshness re-checks, and lazy expiry
  (`min(expires_at, createdAt + 30 days)`, 7-day assessment TTL, no
  scheduler). An `APPROVED` record is a terminal authorization artifact and
  executes nothing. Verified in tests (full `./mvnw clean test` suite: 398
  tests, 0 failures; see `PROJECT_STATUS.md` Phase 11 S1–S6). No new
  dependencies, no target-service change.
- Deterministic evidence packets (Phase 14 S1, see §6c and `DECISIONS.md`
  D39): an internal `evidence` package with `EvidencePacketAssembler`,
  nine source adapters, and immutable transient packets across the five
  evidence categories and three modes (`BASELINE`, `SIMULATION`,
  `POLICY_EVALUATION`), built only from the bounded graph snapshot and
  bounded proposal/audit reads. No endpoint, persistence, credentials,
  claims, agent runtime, or execution; read-only `READ_COMMITTED` with no
  global-snapshot claim. Verified: full `./mvnw test` suite 505 tests,
  0 failures (see `PROJECT_STATUS.md` Phase 14 S1).
- Read-only/advisory operator console (Phase 13 S1–S5 merged; S5 via PR #20,
  merge commit `10472f26a54fa488705fe5fbcdaf10bd18fed3cc`, with local live
  verification passed 2026-11-03; see §7 and
  `DECISIONS.md` D33/D36/D37): `frontend/` is a React +
  Vite + **JavaScript** (no TypeScript) console. S1 provides the System Twin
  explorer over the existing `TWIN_READ` GETs
  (`/api/v1/twin/entities[/{id}]`,
  `/api/v1/twin/relationships[/{id}]`) with client-side filtering, a
  dependency-graph view, and accessible loading/empty/error states. S2 adds a
  live-only Impact & Risk view that selects one loaded root, requires an
  explicit Analyze action, and issues only `GET /api/v1/twin/impact` and
  `GET /api/v1/twin/risk` with `from=<UUID>` and no other query parameters;
  backend defaults (`maxDepth=6`; `SERVICE,API,DATABASE,TABLE`) apply. S3 adds
  a live-only Audit Integrity view with one explicit verification action that
  issues only `GET /api/v1/twin/audit/verify` and renders the bounded
  `OK`/`BROKEN` result; it never repairs or mutates the chain. S4 (merged via
  PR #18) adds a live-only, read-only proposal governance view over the five
  D36 `TWIN_READ` GETs with no lifecycle mutation; its live smoke was partial
  because the database contained zero proposals.
  S5 (merged via PR #20 on 2026-11-03 under D37) adds a live-only
  **Simulation & Policy** view that calls only
  `POST /api/v1/twin/simulations` and
  `POST /api/v1/twin/policy-evaluations` with `TWIN_ANALYZE`, one loaded root,
  up to 10 operations (a frontend usability limit only), and separate explicit
  action/result state. It sends no request on entry, root selection, or editing,
  has no combined action, and keeps simulation and policy results independent;
  the policy endpoint re-runs simulation server-side. The view is advisory and
  non-persistent and adds no execution, approval, or proposal-mutation control.
  Only S1 exists in synthetic preview mode. The developer-supplied bearer token
  is kept in memory only (never persisted, logged, or placed in a URL). The
  console is **local-development only**: Vite binds `127.0.0.1` and proxies
  `/api` to `http://127.0.0.1:8080`, adding no backend CORS rule, endpoint, or
  hosting. Node 24 LTS drives `npm test` (Vitest + Testing Library; **258 tests
  across 24 files**, including 58 focused S5/409 tests) and `npm run build`;
  the S5 build passed and `npm audit --audit-level=high` reported no
  high/critical findings (one pre-existing moderate Vite advisory). Full backend
  regression passed **398 tests** including Docker/Testcontainers integration
  tests; GitHub Frontend CI and Backend CI passed on PR #20 before merge. A
  Phase 13 S2 headless-Chrome smoke test passed 41/41 checks against
  the local twin (15 entities, 18 relationships, 14 impacted entities, 24
  evidence paths, 14 risk entries); the rejected-token case used an
  invalid-signature token, not a naturally expired token. S3 was verified by
  its automated suite and build; S4's live smoke was partial (zero proposals).
  **S5 local live verification passed on 2026-11-03**: 20/20 backend checks
  ran against standalone local Keycloak 26.8.1
  (`http://127.0.0.1:8180/realms/systivex-dev`), the backend on
  `127.0.0.1:8090` (health `UP`), and the Vite console on
  `http://127.0.0.1:5173`. They verified 401 without a token, 403 for a
  `TWIN_READ`-only identity on both S5 POSTs, a successful `TWIN_ANALYZE`
  baseline simulation (`simulated: true`; 14 impacted before/after; 15
  entities and 18 relationships; no delta), and an independent policy result
  (decision `REVIEW`, version `policy-v1`, bounded simulation summary). Entity,
  relationship, and proposal counts stayed unchanged at **15 / 18 / 1**. A
  headless browser smoke passed the token gate, access confirmation,
  simulation, and policy workflows and found no token in storage, the URL, or
  the DOM. These were local live checks, distinct from automated tests and CI.
  Live OIDC sign-in and production/static hosting are **not** implemented or
  tested (no trusted production issuer/client is configured).

Phase 12 S0–S1 (2026-10-16/17) is **design and feasibility work only, not
runtime implementation**: `DECISIONS.md` D30 records the controlled-execution
constraints and D31 records the feasibility gate, but no execution endpoint,
migration, `TWIN_EXECUTOR` role wiring, or actuator exists, no simulation
operation has a qualifying target actuator, and `APPROVED` stays terminal
(D29 preserved, not superseded).

Why PostgreSQL + Flyway + a relational graph at this stage: the records the
control plane will eventually authorize against (twin state, proposals,
decisions, executions) need integrity guarantees and a readable audit trail
more than they need graph-traversal speed. Two tables with FK/unique/check
constraints cover Phase 1; a graph store would add operational weight with no
consumer yet (see `DECISIONS.md` D10).

Intentionally absent (not bugs — deferred by decision):

- Twin trace/log presence signals, real-world controlled execution,
  target-service dispatch, and execution/change verification modules
  (the repository connector is Phase 4, above; read-only impact traversal
  is Phase 5, above; health/counter annotation is Phase 6, above; ordinal
  risk classification is Phase 7, above; hypothetical change simulation is
  Phase 8, above; advisory policy evaluation is Phase 9, above;
  control-plane authentication is Phase 10, above; governed proposals,
  approvals, and the tamper-evident audit are Phase 11, above).
- Redis, Spring AI / Ollama, and target-service authentication (still
  deferred). The frontend's Node toolchain and CI job exist, but the console is
  **local-development-only** (Phase 13 S1–S5 merged under D33/D36/D37; S5 local
  live verification passed 2026-11-03): no production/static hosting, no
  deployed surface, and no live OIDC sign-in (trusted production issuer/client
  details are still missing). S5's two
  approved POSTs are advisory and non-persistent; no later UI slice is approved.
- Grafana log dashboards, Tempo trace UI, unified trace/log UI, anomaly
  detection, any agent use of telemetry (later 3B slices and later).
- Docker packaging of the Java services, Kafka / RabbitMQ / Kubernetes /
  WebSockets / vector DB / MCP / multi-agent / cloud.
- Target-service cross-database atomicity (rejected in D17; reconciliation is
  future work, not missing plumbing).

Next architectural step (see `PROJECT_STATUS.md`): controlled
execution remains **unimplemented and gated**. `DECISIONS.md` D30
records the binding constraints (explicit `TWIN_EXECUTOR`, human
trigger, revalidation at dispatch, audited outcome proof) and D31
records the Phase 12 S1 feasibility gate — no current simulation
operation has a meaningful, independently verifiable target actuator
(all three are hypothetical-graph edits; the target services expose
business POSTs and a read-only Actuator surface only). `APPROVED`
stays terminal (D29) until a specific operation and actuator satisfy
the gate and a D29 change is explicitly decided. Phase 13 S1 — the scoped,
read-only System Twin console (`DECISIONS.md` D33, `frontend/`) — is **merged
via PR #8 but local-development-only**. Phase 13 S2 — the Impact & Risk view —
is **merged via PR #12 on 2026-10-29** (merge commit
`62656c7a2f7ed547ac1262ae9e8a1d46e20dc7b4`) and smoke-verified. Phase 13 S3 —
the Audit Integrity view — is **merged via PR #14 on 2026-10-29** (merge commit
`fef38681992f024ec0ccb2503e4167c9fd5e276c`). Phase 13 S4 — read-only proposal
governance (`DECISIONS.md` D36) — is **merged via PR #18 on 2026-11-02** with a
partial live smoke (zero proposals). Phase 13 S5 — Simulation & Policy
(`DECISIONS.md` D37) — is **implemented, automated-verified, and merged via
PR #20 on 2026-11-03** (merge commit
`10472f26a54fa488705fe5fbcdaf10bd18fed3cc`); its **local live verification
passed on 2026-11-03** (20/20 backend checks and a headless frontend smoke).
It calls only the two advisory POSTs and
performs no mutation, execution, approval, persistence, or audit repair.
S1–S5 remain local-development-only. Execution stays gated and separate, and
the console is never exposed beyond the local machine before the deferred
authentication story lands.
