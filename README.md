<div align="center">

# 🧠 Systivex

### Software-System Twin & Change Intelligence Platform

<p>
Systivex builds a living <b>System Twin</b> of a software system so every proposed change
can be reasoned about before anyone makes it. It deterministically analyses impact,
assesses risk, simulates outcomes, evaluates policy, and records each decision in a
governed, tamper-evident ledger.
</p>

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-336791?style=for-the-badge&logo=postgresql&logoColor=white)
![Maven 3.9.16](https://img.shields.io/badge/Maven-3.9.16-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)

[![Backend CI](https://github.com/soubhagya-behera/systivex/actions/workflows/backend-ci.yml/badge.svg?branch=main)](https://github.com/soubhagya-behera/systivex/actions/workflows/backend-ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square)](LICENSE)

**[The Problem](#-the-problem) • [Features](#-features) • [Architecture](#-architecture-at-a-glance) • [Capabilities](#-capabilities-and-status) • [Quick Start](#-quick-start) • [Security](#-security-and-execution-boundaries)**

</div>

---

## 🎯 The Problem

| Engineering Problem | The Systivex Approach |
| --- | --- |
| Dependencies are implicit and easy to miss | Persist a twin of services, APIs, databases, and their relationships |
| Change impact is guessed rather than measured | Deterministic breadth-first impact analysis over the twin graph |
| Risk is subjective and inconsistent | Ordinal `LOW`/`MEDIUM`/`HIGH`/`UNKNOWN` risk from impact paths and runtime facts |
| Proposed-change consequences are difficult to assess before rollout | Hypothetical before/after simulation — nothing is executed |
| Decisions are hard to audit | Governed proposals plus a hash-chained, verifiable audit ledger |

---

## ✨ Features

<table>
<tr><td width="50%"><strong>🧬 Persisted System Twin</strong><br/>A durable graph of services, APIs, databases, tables, and relationships in PostgreSQL.</td>
<td width="50%"><strong>🔎 Read-only Synchronization</strong><br/>A connector loads source repositories and target services into twin rows without modifying anything.</td></tr>
<tr><td width="50%"><strong>🧭 Change-Impact Analysis</strong><br/>Deterministic graph traversal returns ordered evidence paths for a proposed change.</td>
<td width="50%"><strong>⚠️ Risk Assessment</strong><br/>Ordinal risk classification from impact paths and runtime facts; advisory, never authorization.</td></tr>
<tr><td width="50%"><strong>🧪 Hypothetical Simulation</strong><br/>Before/after comparison over an in-memory graph, marked <code>simulated</code> and never persisted.</td>
<td width="50%"><strong>📋 Advisory Policy Evaluation</strong><br/><code>ALLOW</code> / <code>REVIEW</code> / <code>DENY</code> over the simulated change — an input to judgement, not permission.</td></tr>
<tr><td width="50%"><strong>🗳️ Governed Proposals</strong><br/>Immutable revisions with assessment, submission, and human approval or rejection under separation of duties.</td>
<td width="50%"><strong>🔐 Tamper-evident Audit</strong><br/>A hash-chained, append-only ledger with an endpoint that verifies chain integrity.</td></tr>
<tr><td width="50%"><strong>🛡️ JWT-protected API</strong><br/>Every <code>/api/v1/twin/*</code> endpoint requires a validated OAuth2 bearer token and an allowlisted role.</td>
<td width="50%"><strong>📡 Runtime Observation</strong><br/>Pull-based health and allowlisted business counters feed the twin; the telemetry stack is local-only.</td></tr>
</table>

---

## 🏗️ Architecture at a Glance

```mermaid
flowchart TB
    subgraph OBS["Observed inputs — read-only"]
        direction LR
        SRC["Sample-service source code<br/>backend/target-services/"]
        SVC["Running sample services"]
    end
    subgraph CORE["Systivex control plane — modular monolith (Spring Boot 4 · Java 21)"]
        direction LR
        CONN["Read-only repository connector"]
        OBSERVE["Pull-based runtime observation"]
        TWIN[("System Twin")]
        IMPACT["Impact analysis"]
        RISK["Risk assessment"]
        SIM["Hypothetical simulation"]
        POLICY["Advisory policy evaluation"]
        GOV["Governed proposals"]
        AUDIT["Tamper-evident audit ledger"]
    end
    SRC --> CONN --> TWIN
    SVC --> OBSERVE --> TWIN
    AUTH["JWT resource server"] -. "guards every /api/v1/twin/* call" .-> API["JWT-secured REST API"]
    API --> CORE
    TWIN --> IMPACT
    TWIN --> RISK
    TWIN --> SIM
    SIM --> POLICY
    GOV --> AUDIT
    CORE --- DB[("PostgreSQL")]
    GOV -. "APPROVED is terminal" .-> EXECUTION{{"Execution boundary<br/>NOT IMPLEMENTED — GATED"}}
```

Full design, trust boundaries, and the deterministic-vs-advisory distinction live in
[`ARCHITECTURE.md`](ARCHITECTURE.md).

---

## 🧩 Capabilities and Status

| Capability | Status |
| --- | --- |
| System-twin persistence and REST | ✅ Implemented and tested |
| Read-only repository synchronization | ✅ Implemented and tested |
| Deterministic change-impact analysis | ✅ Implemented and tested |
| Change-risk assessment (deterministic, advisory) | ✅ Implemented and tested |
| Hypothetical change simulation | ✅ Implemented and tested |
| Advisory policy evaluation | ✅ Implemented and tested |
| Governed proposals and human decisions | ✅ Implemented and tested |
| Tamper-evident audit verification | ✅ Implemented and tested |
| JWT-protected API | ✅ Implemented and tested |
| Runtime observation and telemetry integration | ✅ Implemented and tested |
| Controlled execution | 🚧 Design-only — gated by D30/D31 |
| Frontend / operator console | 🚧 Phase 13 S1–S5 merged (S4 via PR #18, S5 via PR #20); S5 passed local live verification 2026-11-03; local-development-only |
| Agent orchestration, verification, Git integration | 📋 Planned, not implemented |

Impact, risk, and simulation derive deterministically from the twin and never authorize
change. Risk and policy evaluation are advisory; simulation is hypothetical and never
executes. See [`PROJECT_STATUS.md`](PROJECT_STATUS.md) and [`DECISIONS.md`](DECISIONS.md).

---

## 🚀 Quick Start

**Prerequisites:** JDK 21 and, for the tests, a running **Docker** daemon (Testcontainers
starts a throwaway PostgreSQL 18).

```bash
cd backend
chmod +x ./mvnw      # Unix/Linux/macOS: make the Maven wrapper executable
./mvnw test          # requires Docker
```

```powershell
cd backend
.\mvnw.cmd test      # Windows PowerShell; requires Docker
```

<details>
<summary>Running the application locally</summary>

Startup needs PostgreSQL 18, a trusted JWT issuer, and the `SYSTIVEX_DB_PASSWORD`
environment variable. There is no security-off mode and no default credentials, so the app
will not start without a configured issuer. Full instructions — database setup, issuer
configuration, and `./mvnw spring-boot:run` — are in [`DEVELOPMENT.md`](DEVELOPMENT.md).

</details>

<details>
<summary>Running the read-only operator console (frontend)</summary>

The `frontend/` React + Vite + JavaScript console is **local-development only**.
Phase 13 S1 is merged via PR #8 (`DECISIONS.md` D33); S2 via PR #12, S3 via
PR #14, S4 via PR #18, and S5 via PR #20 on 2026-11-03 (merge commit
`10472f26a54fa488705fe5fbcdaf10bd18fed3cc`). Phase 13 S5 (Hypothetical
Simulation & Advisory Policy UI) is implemented under `DECISIONS.md` D37,
automated-verified, and **passed local live verification on 2026-11-03**:
20/20 backend checks plus a headless browser smoke. The local live check used
standalone local-development Keycloak 26.8.1 at
`http://127.0.0.1:8180/realms/systivex-dev`, the backend on
`127.0.0.1:8090` (health `UP`), and the console on
`http://127.0.0.1:5173`. It verified 401 without a token, 403 for a
`TWIN_READ`-only identity on both S5 POSTs, a successful `TWIN_ANALYZE`
simulation (baseline unchanged), and an independent policy result
(`REVIEW`, `policy-v1`); persistence stayed at 15 entities, 18 relationships,
and 1 proposal. The headless smoke found no token in browser storage, the URL,
or the DOM. This is local development verification, not production identity or
hosting. The console binds to `127.0.0.1` and proxies `/api` to
`http://127.0.0.1:8080` by default; set `SYSTIVEX_BACKEND_PORT=8090` to reach
the protected local backend configuration used on this machine. It adds no
backend surface and has no production hosting or live OIDC login.

```bash
cd frontend
npm ci
npm test        # Vitest + Testing Library (258 tests; 58 focused S5/409 tests)
npm run build   # production bundle
npm run dev     # serve at http://127.0.0.1:5173 (start the backend first)
```

Open the console, paste a bearer token you already hold — it is kept in memory for
the tab only — and explore entities, relationships, the dependency graph, the
**Impact & Risk** view, the **Audit Integrity** view, the **Proposals** view, or
the **Simulation & Policy** view. Select one loaded root
and choose Analyze explicitly; S2 then sends only `GET /api/v1/twin/impact?from=<UUID>`
and `GET /api/v1/twin/risk?from=<UUID>`, using the backend defaults
(`maxDepth=6`; `SERVICE,API,DATABASE,TABLE`). The view shows impact evidence
paths, truncation/cycle/limits, advisory risk entries, and separate impact/risk
computation times; risk has no truncation or cycle fields, so risk completeness
is not claimed. The **Audit Integrity** view requires an explicit **Verify audit
chain** action and sends only `GET /api/v1/twin/audit/verify`, displaying the
bounded `OK`/`BROKEN` status, event and head counts, nullable first broken
sequence, and diagnostic detail; it never repairs or mutates the chain. The
**Proposals** view loads only the first proposal page on entry, then loads one
proposal's latest revision on selection; revision history, a selected revision,
and the per-proposal audit trail load only after explicit actions. It uses only
the five D36 `TWIN_READ` GET endpoints, displays nullable fields and stored
hashes as inert text, and performs no approval, rejection, execution, or
client-side chain verification.

The **Simulation & Policy** view requires `TWIN_ANALYZE` (not `TWIN_READ`
alone). Select one root, optionally add up to 10 hypothetical operations, then
choose **Run simulation** or **Evaluate policy** independently. Entering the
view, selecting a root, or editing operations sends no request; there is no
combined action and no automatic request. The view calls only
`POST /api/v1/twin/simulations` and
`POST /api/v1/twin/policy-evaluations`; the policy endpoint independently
re-runs simulation server-side. The 10-operation cap is a frontend usability
limit only, not an API restriction. Results are advisory and read-only: the UI
does not execute, approve, persist, or mutate a proposal or target system, and
it shows the server advisories exactly (`SIMULATED — NOT EXECUTED` and
`ADVISORY POLICY EVALUATION — NOT AN AUTHORIZATION; NOT EXECUTED`). The S2–S5
views are live-only and are excluded from synthetic preview mode. There is no
live OIDC login flow and no bundled identity provider: the console never
persists the token, performs read-only `GET`s, and adds only those two approved
advisory `POST`s against the System Twin API.

</details>

---

## 🛡️ Security and Execution Boundaries

> **Controlled execution is not implemented. An `APPROVED` proposal is a terminal
> governance record; it does not authorize or execute an operation.**

Every `/api/v1/twin/*` endpoint requires a validated OAuth2 bearer token and an allowlisted
role (`TWIN_READ`, `TWIN_ANALYZE`, `TWIN_OPERATOR`, `TWIN_ADMIN`, `TWIN_APPROVER`). There is
no live OIDC login flow, no bundled identity provider, and no default credentials. A
local-development Keycloak setup has been used for manual token validation, but
production identity-provider configuration and live OIDC sign-in are not implemented
or claimed. Systivex is not an execution engine, agent, or tool broker, and no target
actuator exists — report issues via [`SECURITY.md`](SECURITY.md).

---

## 🗂️ Project Structure

`backend/` holds the Java 21 control plane (with its sample `target-services/`),
`frontend/` holds the React + Vite operator console (Phase 13 S1–S5 merged;
S5 passed local live verification 2026-11-03; local development only), and
`observability/` holds a local telemetry stack (collector, Prometheus, Loki,
Grafana).

| Document | Purpose |
| --- | --- |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | System design and trust boundaries |
| [`DEVELOPMENT.md`](DEVELOPMENT.md) | Build, test, run, and configuration |
| [`PROJECT_STATUS.md`](PROJECT_STATUS.md) | Phase-by-phase record of what exists |
| [`DECISIONS.md`](DECISIONS.md) | Architectural decisions (D1–D37) |
| [`THREAT_MODEL.md`](THREAT_MODEL.md) | Security principles and threat model |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to contribute |
| [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) | Community expectations |
| [`SECURITY.md`](SECURITY.md) | How to report a vulnerability |

---

## 🤝 Contributing and License

Contributions are welcome — start with [`CONTRIBUTING.md`](CONTRIBUTING.md). Proposed
execution, authentication, or security-sensitive changes require review and must not bypass
existing decisions. Systivex is licensed under the **Apache License 2.0**; see
[`LICENSE`](LICENSE).

<div align="center">
<sub>Copyright 2026 Soubhagya Kumar Behera</sub>
</div>
