# Systivex Project Status

Last verified: **2026-09-29** (Asia/Kolkata). This document must be updated
whenever the implemented state changes. Planned items are not claimed as done.

## Current phase

**Phase 1 — Control-plane persistence foundation.** Phase 0 (repository
foundation) below is kept as history.

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

- Richer twin ingestion (code/arch/runtime connectors), Git integration, telemetry.
- Agent orchestration, tool mediation, simulation, evidence model.
- Policy / approval / controlled-execution / verification logic.
- Filtering, pagination, graph traversal, bulk import on the twin API.
- Authentication/authorization on the API.
- Redis configuration or usage.
- Spring AI / Ollama integration, model configuration.
- React (JavaScript, no TypeScript) frontend; no `frontend/` directory.
- Docker Compose or any container orchestration.
- Target demo microservices; message brokers; Kubernetes; WebSockets;
  vector databases; MCP; multi-agent architecture; cloud infrastructure.
- Placeholder or speculative backend packages beyond `twin`.

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

**Milestone 2 — Twin ingestion from a real source:**

1. Repository connector first (read code structure into `SystemEntity` /
   `SystemRelationship` rows) — no agents, no execution.
2. API filtering/pagination only when a consumer needs it.
3. Authentication on the twin API before the surface leaves the local machine.

Out of scope for Milestone 2: agents, simulation, approvals UI, brokers, K8s,
vector search, frontend beyond API consumption readiness.
