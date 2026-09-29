# Systivex Project Status

Last verified: **2026-09-28** (Asia/Kolkata). This document must be updated
whenever the implemented state changes. Planned items are not claimed as done.

## Current phase

**Phase 0 — Initial repository foundation only.**

## What is complete

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

- System Twin model, connectors, persistence.
- Agent orchestration, tool mediation, simulation, risk engine.
- Policy / approval / controlled-execution / verification logic.
- Observability beyond default Actuator health; no custom endpoints.
- PostgreSQL configuration, migrations, repositories.
- Redis configuration or usage.
- Spring AI / Ollama integration, model configuration.
- Security architecture (authn/authz).
- React (JavaScript, no TypeScript) frontend; no `frontend/` directory.
- Docker Compose or any container orchestration.
- Target demo microservices; message brokers; Kubernetes; WebSockets;
  vector databases; MCP; multi-agent architecture; cloud infrastructure.
- Placeholder or speculative backend packages beyond the single application class.

## Verification record (2026-09-28)

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
  Note: repo has no `.git` yet — the rule takes effect on `git init`.

## Immediate next milestone (proposed, not started)

**Milestone 1 — Control-plane module skeleton + twin domain outline:**

1. Define the modular-monolith package structure under `com.soubhagya.systivex`
   (e.g. `twin`, `policy`, `execution`, `api`) with boundary rules — no speculative logic.
2. Sketch the System Twin domain model (entities/relationships only, no persistence yet).
3. Add a minimal versioned REST surface (e.g. `GET /api/v1/status`) with tests.
4. Introduce PostgreSQL configuration + migrations only when the twin model
   actually needs persistence — not before.

Out of scope for Milestone 1: agents, simulation, approvals UI, brokers, K8s,
vector search, frontend beyond API consumption readiness.
