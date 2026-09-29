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

**Implemented** (verified 2026-09-29):

- Spring Boot 4.1.1 backend (`backend/`) on Java 21 / Maven.
- Application starts; `GET /actuator/health` returns `{"status":"UP"}`.
- PostgreSQL persistence: Spring Data JPA + Hibernate, Flyway-owned schema
  (`ddl-auto=validate` — Hibernate never modifies the schema).
- Initial twin persistence model: `SystemEntity` (7 closed types) and directed
  `SystemRelationship` (8 closed types), JSONB metadata, UUID keys,
  FK/unique/check constraints, `ON DELETE CASCADE`.
- Minimal twin REST surface (`/api/v1/twin/entities`, `/api/v1/twin/relationships`)
  with DTOs, Bean Validation, and stable 400/404/409 error responses.
- 29 tests green: service unit tests plus Testcontainers-backed migration,
  repository, and API integration tests.

**Not yet implemented** (planned architecture only):

- Richer twin ingestion (code/arch/runtime connectors), Git integration, telemetry
- Agent orchestration, tooling, evidence model
- Policy / risk / approval engine and controlled execution, simulation, verification
- Cache (Redis), Spring AI / Ollama integration, frontend, target microservices

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
