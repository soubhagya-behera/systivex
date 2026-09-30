# Systivex — Agentic Software System Twin & Change Intelligence Platform

> Systivex is an **agentic software-system twin and engineering safety platform** connecting
> code, architecture, data, runtime behavior, simulation, policy-controlled execution, and verification.

Systivex builds a living **System Twin** of a software system — its code, architecture,
data flows, and runtime behavior — so that proposed changes can be **simulated, risk-assessed,
policy-gated, executed under control, and verified** before and after they touch real systems.

Systivex is **not**:

- an AI code reviewer,
- an SRE chatbot,
- a generic LLM wrapper,
- a chat assistant over logs.

Those are point tools. Systivex is a **control plane for safe engineering action**:
it grounds agent reasoning in an authoritative model of the system and only permits
consequential actions through explicit control-plane authorization.

## Current state (honest snapshot)

**Implemented** (verified 2026-09-30):

- Spring Boot 4.1.1 backend (`backend/`) on Java 21 / Maven.
- Application starts; `GET /actuator/health` returns `{"status":"UP"}`.
- PostgreSQL persistence: Spring Data JPA + Hibernate, Flyway-owned schema
  (`ddl-auto=validate` — Hibernate never modifies the schema).
- Initial twin persistence model: `SystemEntity` (7 closed types) and directed
  `SystemRelationship` (8 closed types), JSONB metadata, UUID keys,
  FK/unique/check constraints, `ON DELETE CASCADE`.
- Minimal twin REST surface (`/api/v1/twin/entities`, `/api/v1/twin/relationships`)
  with DTOs, Bean Validation, and stable 400/404/409 error responses.
- 29 control-plane tests green: service unit tests plus Testcontainers-backed
  migration, repository, and API integration tests.
- Phase 2B target environment (`backend/target-services/`): four independently
  runnable Spring Boot services — gateway (8081) → order (8082) → inventory
  (8084) + payment (8083) — with real HTTP calls between processes and
  service-owned PostgreSQL databases (`order_db`, `payment_db`,
  `inventory_db`; gateway stateless; control-plane `systivex` separate).
  Persistent orders (PENDING → CONFIRMED/FAILED), recorded payment
  authorizations (AUTHORIZED/DECLINED), and real stock with locked
  reservations (seeded `SKU-1001 × 10` for local demos). Checkout across the
  three databases is deliberately not atomic — documented, not hidden.
  Controlled 400/422/502/503 error handling with nothing internal on the
  wire, and 47 service tests green (Testcontainers-backed). No
  observability, no agents — just the persistent distributed system Systivex
  will one day observe. The control plane does not read from these services yet.

**Not yet implemented** (planned architecture only):

- Twin ingestion of the target environment, Git integration, telemetry
- Agent orchestration, tooling, evidence model
- Policy / risk / approval engine and controlled execution, simulation, verification
- Cross-database checkout atomicity/reconciliation, cache (Redis), Spring AI / Ollama, frontend

See [`PROJECT_STATUS.md`](PROJECT_STATUS.md) for the phase record,
[`ARCHITECTURE.md`](ARCHITECTURE.md) for target vs. current architecture,
and [`DEVELOPMENT.md`](DEVELOPMENT.md) for how to build and run what exists today.

## Repository layout

```text
Systivex/
├── backend/                 # IMPLEMENTED: Spring Boot control plane + twin persistence
│   ├── pom.xml              # Spring Boot 4.1.1, Java 21
│   ├── mvnw / mvnw.cmd / .mvn/
│   └── src/
│       ├── main/java/com/soubhagya/systivex/
│       │   ├── SystivexApplication.java
│       │   └── twin/{model,repository,service,api}/
│       ├── main/resources/application.properties          # LOCAL ONLY, git-ignored
│       ├── main/resources/application.example.properties  # tracked safe template
│       ├── main/resources/db/migration/V1__create_system_twin.sql
│       └── test/java/com/soubhagya/systivex/  # unit + Testcontainers integration tests
│   └── target-services/         # IMPLEMENTED (Phase 2B): persistent target runtime Systivex will observe
│       ├── create-target-databases.sql  # LOCAL-ONLY bootstrap: 3 dbs + users, no passwords inside
│       ├── gateway-service/     # :8081, forwards POST /api/v1/checkout to order (stateless)
│       ├── order-service/       # :8082, persisted checkout orchestration (order_db)
│       ├── payment-service/     # :8083, recorded authorizations (payment_db)
│       └── inventory-service/   # :8084, real stock + reservations (inventory_db, V2 dev seed)
│           # each: own pom/mvnw/app class + Flyway history; live application.properties git-ignored,
│           # application.example.properties tracked; datasource passwords via env only
├── README.md                # this file
├── ARCHITECTURE.md          # target architecture + current status
├── DEVELOPMENT.md           # build / test / run instructions
├── PROJECT_STATUS.md        # phase record
├── DECISIONS.md             # architectural decisions
├── THREAT_MODEL.md          # trust principles
└── .gitignore               # root ignores
```

No `frontend/`, no `docker-compose.yml` — intentionally absent
(see `DECISIONS.md`: no premature infrastructure). Database configuration exists
but is local-only: `application.properties` is git-ignored, only the safe
`application.example.properties` template is tracked.

## Quick start (current phase)

Prerequisite: **JDK 21** (`JAVA_HOME` must point to a JDK 21 installation).

```powershell
cd backend
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
$env:Path="C:\Program Files\Java\jdk-21.0.10\bin;" + $env:Path
./mvnw test                      # needs Docker (Testcontainers PostgreSQL)
$env:SYSTIVEX_DB_PASSWORD="<local systivex_app password>"
./mvnw spring-boot:run           # needs local PostgreSQL, see DEVELOPMENT.md
# then: GET http://localhost:8080/actuator/health -> {"status":"UP"}
```

(Outside VS Code the `PATH` prepend matters too; inside a new VS Code
integrated terminal both variables are set automatically.)

Full instructions: [`DEVELOPMENT.md`](DEVELOPMENT.md).

## Documentation map

| Document | Purpose |
|---|---|
| `README.md` | Vision + honest current-state snapshot (this file) |
| `ARCHITECTURE.md` | Target architecture, control/target separation, trust boundaries, current status |
| `DEVELOPMENT.md` | Local build/test/run, prerequisites |
| `PROJECT_STATUS.md` | What is done, what is deliberately absent, next milestone |
| `DECISIONS.md` | Initial architectural decisions and rationale |
| `THREAT_MODEL.md` | Initial trust principles (LLM untrusted, authorization, audit) |

## Project rules (binding)

- Java 21, Spring Boot 4.1.1, Maven.
- Modular monolith for the Systivex **control plane**; microservices are reserved
  for the future **target software environment**, not for Systivex itself at this stage.
- PostgreSQL as primary persistence (Flyway-owned schema, Hibernate validates only);
  Redis only when justified; Spring AI + Ollama later.
- REST + SSE initially.
- Do not introduce Kafka, RabbitMQ, Kubernetes, WebSockets, vector databases, MCP,
  multi-agent architecture, or cloud infrastructure at this stage.
