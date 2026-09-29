# Systivex Architecture

Status: **target architecture (planned) + current implementation status (verified 2026-09-28)**.
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
packages); the remaining target modules are **not yet created** — no placeholder
packages exist for them by decision:

- `twin` — System Twin model and connectors (**model + persistence + minimal
  CRUD API exist**; connectors planned)
- `intelligence` — change impact, simulation, risk assessment (planned)
- `policy` — policy evaluation and approval workflow (planned)
- `execution` — controlled, auditable action dispatch (planned)
- `verification` — post-change checks against twin + runtime (planned)
- `observation` — code/arch/runtime ingest (planned)
- `api` — REST + SSE surface (initial twin CRUD exists; only Actuator health
  existed before Phase 1)

Module rules (binding for future work): modules communicate through explicit
APIs; no cross-module persistence access; every consequential action crosses
the policy/execution boundary (see §6).

## 4. Target microservices (Phase 2A runtime exists; observation planned)

Four independently deployable services under `backend/target-services/` form the
first **target environment** — the kind of system the twin will eventually model
and the control plane will act upon. They are ordinary business services with no
Systivex awareness in them:

```text
client ──▶ gateway-service (:8081, POST /api/v1/checkout)
               │ HTTP (RestClient, URL from config)
               ▼
           order-service (:8082, POST /api/v1/orders)
               ├──▶ inventory-service (:8084, POST /internal/v1/inventory/reserve)
               └──▶ payment-service (:8083, POST /internal/v1/payments/authorize)
```

- Each has its own pom, Maven wrapper, application class, and Actuator health.
- Downstream URLs come from configuration (`*_URL` env vars with localhost
  defaults), never from source. Fixed dev ports (8081–8084); control plane stays
  on 8080.
- Checkout is deliberately thin: validate → reserve → authorize → respond.
  Inventory rejects quantities above a demo limit, payment declines amounts above
  a demo limit (both 422); unreachable/erroring downstream becomes 503 at order
  level and 502 at gateway level, with sanitized bodies throughout.
- Stateless and database-free by decision (persistence arrives in Phase 2B);
  the approve/reject rules are deterministic so tests and demos are repeatable.
- No Kubernetes, no message broker, no service mesh at this stage.
- The control plane does not call, scrape, or model these services yet. They
  remain strictly outside the control-plane trust boundary: untrusted until
  verified, never self-authorizing, carrying only their own local logic.

## 5. System Twin concept (persistence exists; reasoning still planned)

The System Twin is the planned authoritative model of a target system, joining:

- **code** (structure, dependencies),
- **architecture** (services, boundaries, contracts),
- **data** (stores, flows, schemas),
- **runtime behavior** (health, metrics, traces, events).

Agent reasoning must be grounded in the twin and in live backend state —
never in LLM output alone. What exists today is the persistence footing:
`system_entity` / `system_relationship` tables (Flyway V1), JPA mappings kept
in lockstep by `ddl-auto=validate`, and CRUD behind DTOs. No twin package
beyond that, no schema, no connectors yet.

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

## 7. API shape (initial REST exists)

- **REST** for twin queries, change proposals, approvals, execution records.
  Implemented so far: twin entity/relationship CRUD under `/api/v1/twin`
  (no filtering/pagination yet); proposals/approvals/records are still planned.
- **SSE** for streaming long-running analysis/simulation progress (planned).
- No WebSockets at this stage (see `DECISIONS.md`).

## 8. Current implementation status (verified 2026-09-29)

Implemented:

- `backend/`: Spring Boot 4.1.1, Java 21 (`release 21`), Maven build.
- Dependencies: `webmvc`, `validation`, `actuator`, `data-jpa`,
  `spring-boot-starter-flyway` + `flyway-database-postgresql`, `postgresql`
  driver (+ test: `webmvc-test`, `restclient-test`, `testcontainers-postgresql`).
  Nothing else.
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

Why PostgreSQL + Flyway + a relational graph at this stage: the records the
control plane will eventually authorize against (twin state, proposals,
decisions, executions) need integrity guarantees and a readable audit trail
more than they need graph-traversal speed. Two tables with FK/unique/check
constraints cover Phase 1; a graph store would add operational weight with no
consumer yet (see `DECISIONS.md` D10).

Intentionally absent (not bugs — deferred by decision):

- Twin connectors/ingestion, intelligence, policy, execution, verification,
  observation modules.
- Redis, Spring AI / Ollama, security, frontend, Docker Compose.
- Kafka / RabbitMQ / Kubernetes / WebSockets / vector DB / MCP / multi-agent / cloud.

Next architectural step (see `PROJECT_STATUS.md`): twin ingestion from a real
source (repository connector first) — still without agents or execution.
