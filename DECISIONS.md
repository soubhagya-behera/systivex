# Systivex Architectural Decisions

Record of binding decisions taken at project foundation (2026-09-28).
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

## D6. PostgreSQL as future primary persistence — planned

- **Decision:** PostgreSQL will be the primary system of record (twin state, proposals,
  policy decisions, execution/verification records) when persistence is introduced.
- **Rationale:** Relational integrity and auditability suit safety-critical records.
- **State:** Deferred; no driver, no datasource, no migrations exist today.

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
- **State:** Planned; only Actuator `health` exists today. No application REST/SSE yet.

## D10. No premature infrastructure — decided

- **Decision:** Do not introduce Kafka, RabbitMQ, Kubernetes, WebSockets, vector
  databases, MCP, multi-agent architecture, or cloud infrastructure at this stage.
  Likewise no Docker Compose, security architecture, or frontend scaffolding yet.
- **Rationale:** Each adds ops surface, failure modes, and security scope without a
  consumer. Infrastructure follows demonstrated need and a recorded decision.
- **State:** Enforced in this phase; `pom.xml` contains only `webmvc`, `validation`,
  `actuator` (+ test starters).

## Supersession rule

New decisions amend this file with date and rationale; they never silently edit
history. If reality diverges from any entry, `PROJECT_STATUS.md` records the
discrepancy first.
