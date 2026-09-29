# Systivex Architectural Decisions

Record of binding decisions taken at project foundation (2026-09-28) and
extended in Phase 1 (2026-09-29).
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

## Supersession rule

New decisions amend this file with date and rationale; they never silently edit
history. If reality diverges from any entry, `PROJECT_STATUS.md` records the
discrepancy first.
