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
| Shape | **Modular monolith** (Spring Boot) | **Future microservices** (example / managed systems) |
| Reasoning | Agent + twin-grounded analysis (planned) | Application business logic |
| Authority | Grants or denies consequential actions | Carries out only authorized actions |
| Trust | Authoritative for decisions and audit | Untrusted until verified |

This separation is deliberate: the thing that decides must not be the thing
being changed, and the thing being changed must never self-authorize.

## 3. Modular-monolith control plane (target)

The control plane is a single Spring Boot deployment with strict module boundaries
(target modules, **not yet created** — no placeholder packages exist today):

- `twin` — System Twin model and connectors (planned)
- `intelligence` — change impact, simulation, risk assessment (planned)
- `policy` — policy evaluation and approval workflow (planned)
- `execution` — controlled, auditable action dispatch (planned)
- `verification` — post-change checks against twin + runtime (planned)
- `observation` — code/arch/runtime ingest (planned)
- `api` — REST + SSE surface (planned; only Actuator health exists today)

Module rules (binding for future work): modules communicate through explicit
APIs; no cross-module persistence access; every consequential action crosses
the policy/execution boundary (see §6).

## 4. Future target microservices (deferred)

A small set of demo microservices will later serve as the **target environment**
the twin models and the control plane acts upon. They are **deferred**:

- Not created in this phase.
- No Kubernetes, no message broker, no service mesh at this stage.
- When introduced, they remain strictly outside the control-plane trust boundary.

## 5. System Twin concept (planned, not implemented)

The System Twin is the planned authoritative model of a target system, joining:

- **code** (structure, dependencies),
- **architecture** (services, boundaries, contracts),
- **data** (stores, flows, schemas),
- **runtime behavior** (health, metrics, traces, events).

Agent reasoning must be grounded in the twin and in live backend state —
never in LLM output alone. The twin does not exist yet; there is no twin
package, schema, or persistence in the repository today.

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

## 7. API shape (target)

- **REST** for twin queries, change proposals, approvals, execution records (planned).
- **SSE** for streaming long-running analysis/simulation progress (planned).
- No WebSockets at this stage (see `DECISIONS.md`).

## 8. Current implementation status (verified 2026-09-28)

Implemented:

- `backend/`: Spring Boot 4.1.1, Java 21 (`release 21`), Maven build.
- Dependencies: `spring-boot-starter-webmvc`, `spring-boot-starter-validation`,
  `spring-boot-starter-actuator` (+ test starters). Nothing else.
- One `@SpringBootApplication` class; one `contextLoads` test.
- `application.properties` contains only `spring.application.name=systivex`.
- Verified: `./mvnw test` → 1 test, 0 failures; `./mvnw package` → executable jar;
  `./mvnw spring-boot:run` → Tomcat 11 on `:8080`, `GET /actuator/health` → `{"status":"UP"}`;
  only the `health` actuator endpoint is exposed (defaults).

Intentionally absent (not bugs — deferred by decision):

- Twin, intelligence, policy, execution, verification, observation modules.
- PostgreSQL, Redis, Spring AI / Ollama, security, frontend, Docker Compose.
- Kafka / RabbitMQ / Kubernetes / WebSockets / vector DB / MCP / multi-agent / cloud.

Next architectural step (see `PROJECT_STATUS.md`): define the control-plane
module skeleton and twin domain model — without adding infrastructure.
