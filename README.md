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

**Implemented** (verified 2026-09-28):

- Spring Boot 4.1.1 backend skeleton (`backend/`) on Java 21 / Maven.
- Application starts; `GET /actuator/health` returns `{"status":"UP"}`.
- Single `contextLoads` test passes.

**Not yet implemented** (planned architecture only):

- System Twin model, architecture/data/runtime connectors
- Agent orchestration, tooling, simulation
- Policy / risk / approval engine and controlled execution
- Observability, verification, target microservices environment
- Persistence (PostgreSQL), cache (Redis), Spring AI / Ollama integration, frontend

See [`PROJECT_STATUS.md`](PROJECT_STATUS.md) for the phase record,
[`ARCHITECTURE.md`](ARCHITECTURE.md) for target vs. current architecture,
and [`DEVELOPMENT.md`](DEVELOPMENT.md) for how to build and run what exists today.

## Repository layout

```text
Systivex/
├── backend/                 # IMPLEMENTED: Spring Boot control-plane skeleton
│   ├── pom.xml              # Spring Boot 4.1.1, Java 21
│   ├── mvnw / mvnw.cmd / .mvn/
│   └── src/
│       ├── main/java/com/soubhagya/systivex/SystivexApplication.java
│       ├── main/resources/application.properties
│       └── test/java/com/soubhagya/systivex/SystivexApplicationTests.java
├── README.md                # this file
├── ARCHITECTURE.md          # target architecture + current status
├── DEVELOPMENT.md           # build / test / run instructions
├── PROJECT_STATUS.md        # phase record
├── DECISIONS.md             # architectural decisions
├── THREAT_MODEL.md          # initial trust principles
└── .gitignore               # root ignores
```

No `frontend/`, no `docker-compose.yml`, no database configuration — intentionally
absent in this phase (see `DECISIONS.md`: no premature infrastructure).

## Quick start (current phase)

Prerequisite: **JDK 21** (`JAVA_HOME` must point to a JDK 21 installation).

```powershell
cd backend
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
$env:Path="C:\Program Files\Java\jdk-21.0.10\bin;" + $env:Path
./mvnw test
./mvnw spring-boot:run
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
- PostgreSQL later as primary persistence; Redis only when justified; Spring AI + Ollama later.
- REST + SSE initially.
- Do not introduce Kafka, RabbitMQ, Kubernetes, WebSockets, vector databases, MCP,
  multi-agent architecture, or cloud infrastructure at this stage.
