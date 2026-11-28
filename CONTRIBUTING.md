# Contributing to Systivex

Thanks for your interest in Systivex. This guide is written for a first-time
contributor. It covers what to read first, how to build and test, and what we expect from
changes. If anything here is unclear, please open an issue so we can improve it.

By participating you agree to follow the [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).

## Read this first

Systivex has a small, deliberate architecture. Before writing code, read:

1. [`README.md`](README.md) — what Systivex is, what exists, and what is deliberately not built.
2. [`ARCHITECTURE.md`](ARCHITECTURE.md) — the control-plane/target-environment split, module
   boundaries, and the deterministic-vs-advisory distinction.
3. [`DEVELOPMENT.md`](DEVELOPMENT.md) — build, test, run, and configure commands.
4. [`DECISIONS.md`](DECISIONS.md) — binding architectural decisions (read the entries relevant to
   the area you are touching before changing it).
5. [`PROJECT_STATUS.md`](PROJECT_STATUS.md) — the phase-by-phase record of what actually exists.
6. [`THREAT_MODEL.md`](THREAT_MODEL.md) — the security principles the design protects.

The backend control plane lives in `backend/` (packages under
`backend/src/main/java/com/soubhagya/systivex/`). The sample target environment is under
`backend/target-services/`; the local telemetry stack is under `observability/`.

## Prerequisites

| Requirement | Notes |
|---|---|
| **JDK 21** | Required. Set `JAVA_HOME` to a JDK 21 install; the build uses `release 21`. |
| **Maven wrapper** | Bundled at `backend/mvnw` (`backend/mvnw.cmd` on Windows). No separate Maven install. |
| **Docker** | Required to run the test suite: integration tests use Testcontainers PostgreSQL. Your Docker daemon must be running. |
| **PostgreSQL 18** | Required only to *run* the application locally (not for tests). |
| **Node 24 LTS** | Required only for the `frontend/` console (Phase 13 S1-S5 and the D48 fixture-only preview). Not needed for backend work. |

The frontend is a self-contained, local-development-only tool; backend work needs no Node
toolchain, and no cache/AI runtime is required today.

> The wrapper scripts are committed without the executable bit on some checkouts. On
> Linux/macOS run `chmod +x mvnw` once from `backend/` if `./mvnw` is not executable.

## Contributor quick start

Full setup, configuration, and verification instructions are in
[`DEVELOPMENT.md`](DEVELOPMENT.md). The short version:

### Backend

Requires **JDK 21**. The test suite uses Testcontainers PostgreSQL, so **Docker
must be running** before you run the tests. Run all commands from `backend/`
using the Maven wrapper (`mvnw` on Linux/macOS, `mvnw.cmd` on Windows):

```bash
cd backend
./mvnw clean test              # full verification used by CI; requires Docker
./mvnw package -DskipTests     # build a runnable jar without running tests
```

On Windows PowerShell, use the bundled wrapper script instead:

```powershell
cd backend
.\mvnw.cmd -B clean test
```

The individual sample target services each have their own wrapper and tests, e.g.:

```bash
cd backend/target-services/order-service && ./mvnw test
```

**Do not weaken, skip, or delete tests to make a build pass.** If a test suite cannot run
in your environment, describe the precise blocker in your pull request instead of masking
it.

### Frontend

Requires **Node 24 LTS** (see `frontend/.nvmrc`). From `frontend/`:

```bash
cd frontend
npm ci
npm test
npm run build
```

The console is local-development only; see the frontend convention below and
[`DEVELOPMENT.md`](DEVELOPMENT.md) §18 for the full instructions and boundaries.

### Documentation

Keep documentation changes focused and accurate. Verify every link and command
against the current repository before submitting.

## Branch and pull-request expectations

- Branch from `main`; use a short descriptive name (for example `docs/contributing` or
  `test/health-parser-edges`).
- Keep each pull request focused on one change. Split unrelated work into separate PRs.
- Reference the issue your change addresses in the PR description, if one exists.
- Write a clear summary: what changed, why, and how you verified it.
- Prefer small, reviewable commits with meaningful messages.
- Fill in the pull-request template, including the verification and security sections.

## What we look for in a change

- **Tests** — new behavior needs tests; bug fixes should include a regression test. Add
  tests in the same area as the existing suite.
- **Error handling** — preserve the existing stable HTTP error contract (the
  `{status, error, message, path, fieldErrors}` envelope with 400/404/409 semantics).
- **Security** — never bypass or weaken the JWT resource-server configuration, role
  policy, loopback guards, or fail-closed startup behavior. Never log tokens, secrets, or
  request bodies.
- **Documentation** — update the relevant document when behavior or commands change.
- **Scope** — keep the change minimal and consistent with existing decisions.

## Secrets, data, and privacy

- Never commit secrets, credentials, tokens, passwords, private keys, or real production
  data — including in tests, fixtures, logs, screenshots, or issue reports.
- Local configuration is git-ignored on purpose: only `application.example.properties`
  templates are tracked. Provide passwords through environment variables (for example
  `SYSTIVEX_DB_PASSWORD`); never hard-code them.
- Sanitize any logs, traces, or payloads you paste into an issue or pull request.

## Reporting a bug, requesting a feature, or proposing architecture changes

- **Bug** — open an issue using the bug report form. Include reproducible steps, expected
  behavior, actual behavior, and sanitized environment details.
- **Feature** — open an issue using the feature request form. Describe the problem,
  your proposed approach, alternatives, and scope.
- **Significant architectural change** — discuss it in an issue **before** implementing.
  Changes to execution capabilities, authentication, security-sensitive behavior, or new
  infrastructure are treated as significant and must not bypass existing decisions.

Execution-related proposals must respect the recorded decisions: controlled execution is
**not implemented and remains gated**, an `APPROVED` proposal stays terminal, and
simulation must remain clearly distinct from real execution (see
[`DECISIONS.md`](DECISIONS.md) D29–D31).

## Frontend convention

The `frontend/` console is implemented in **React + Vite + JavaScript** (not
TypeScript). Phase 13 S1-S5 are implemented: the System Twin explorer, Impact &
Risk, Audit Integrity, Proposals, and Simulation & Policy views. A separate
Phase 14 S2 Slice 4 **candidate preview** (D48; merged via PR #55) renders locally
defined synthetic fixtures for inspection: it is DEV-gated, excluded from
production builds, and uses no backend endpoint, model provider or persistence.
It is a **local-development-only operator console**, not a production-hosted application.
It supports the existing approved workflows; simulation and policy evaluation
remain hypothetical, advisory, and non-executing.

For detailed usage and boundaries, see [`README.md`](README.md),
[`DEVELOPMENT.md`](DEVELOPMENT.md) §18, and the relevant
[`DECISIONS.md`](DECISIONS.md) entries (D33, D34, D35-D37, D44, D46, D47, D48). Do
not imply that frontend work can introduce new backend endpoints,
authentication, CORS, execution, or security capabilities without an
appropriate recorded decision. D44 adopts the fixture-only presentation design
only; it does not authorize implementation. D48 is the separate explicit
authorization for the narrowly scoped fixture-only frontend implementation of
that design, completed via PR #55. D48 grants no Slice 3 runtime or provider
use, no live endpoint, no backend feature, no persistence, no approval and no
execution capability. D47 is an independent Slice 3 authorization-traceability
hold: it records that authorization cannot currently be verified, remains
binding despite D48, and permits no Slice 3 use without a separate, explicit,
prospective authorization decision.

## Finding work to do

We do not maintain a static list of starter tasks. Find current work through the
repository's [open GitHub issues](https://github.com/soubhagya-behera/systivex/issues):

- Check the repository's currently open GitHub issues.
- Prefer issues with explicit scope, acceptance criteria, and suitable contribution labels.
- Read the issue and its recent discussion before starting.
- Comment on the issue before starting.
- Wait for maintainer confirmation to avoid duplicate work.
- If no suitable issue exists, ask the maintainer to help identify a bounded task.
- Do not start obsolete, completed, closed, or unapproved tasks.
