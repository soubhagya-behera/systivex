# Contributing to Systivex

Thanks for your interest in Systivex. This guide is written for a first-time
contributor. It covers what to read first, how to build and test, and what we expect from
changes. If anything here is unclear, please open an issue so we can improve it.

By participating you agree to follow the [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).

New to Systivex? Start with the short
[Contributor Quickstart](docs/CONTRIBUTOR_QUICKSTART.md) for a welcoming overview;
this document is the detailed reference.

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

## Fork-based contribution workflow

You do not need write access to the Systivex repository. Work from your own fork and
open a pull request back to the original project. Replace `<your-username>` with your
GitHub username and `<branch>` with your branch name.

1. **Fork** the repository on GitHub from
   `https://github.com/soubhagya-behera/systivex` (the **Fork** button).
2. **Clone your fork**:

   ```bash
   git clone https://github.com/<your-username>/systivex.git
   cd systivex
   ```

3. **Add the original repository as `upstream`**:

   ```bash
   git remote add upstream https://github.com/soubhagya-behera/systivex.git
   ```

4. **Fetch upstream** before starting work, and again before pushing if the branch has
   been open for a while:

   ```bash
   git fetch upstream
   ```

5. **Create a branch based on `upstream/main`** (see the naming convention below):

   ```bash
   git switch -c <branch> upstream/main
   ```

6. **Implement the contribution**, following the build and test instructions above.
7. **Run the applicable tests** — backend (`./mvnw clean test`, requires Docker), target
   services (per-service `./mvnw test`), and/or frontend (`npm test`, `npm run build`).
   Report exactly what you ran in the PR.
8. **Push to your fork**:

   ```bash
   git push -u origin <branch>
   ```

9. **Open a pull request** against `soubhagya-behera/systivex`, base branch `main`, from
   your fork's `<branch>`. Use the pull-request template and link the issue your change
   addresses, if one exists.

If you refresh an already-open PR, add commits to the same branch and push again; the PR
updates automatically. Do not rewrite published history without maintainer agreement.

## Branch naming

Use a short, descriptive prefix so the purpose of a branch is obvious:

| Prefix | Use for |
| --- | --- |
| `feat/` | New functionality |
| `fix/` | Bug fixes |
| `test/` | Test additions or changes |
| `docs/` | Documentation changes |
| `refactor/` | Internal changes with no behavior change |
| `ci/` | Build or CI workflow changes |

Examples: `docs/contributor-onboarding`, `test/health-parser-edges`,
`fix/51-blank-entity-name`. Include the issue number when one exists; a short slug after
it is helpful but not required. There is no mandatory commit-message format — prefer
small, reviewable commits with meaningful messages.

## What happens after you open a pull request

- **CI starts automatically.** Every PR runs Backend CI (Java 21, Testcontainers, the
  four target services, and the loopback-config check) and Frontend CI (Node 24 tests
  and production build, with dependency audit when frontend manifests change). You can
  see the results on the PR page.
- **A maintainer reviews the changes** for correctness, scope, security, testing, and
  consistency with the recorded decisions.
- **Review comments may request changes.** Respond to feedback in the PR thread and push
  additional commits to the same branch; the PR and CI update automatically. It is
  normal for a first PR to go through one or more review rounds.
- **CI must pass before merging.** If a check fails, fix the cause in your branch; do not
  disable checks, skip tests, or weaken assertions.
- **Changes must respect project boundaries.** Follow [`ARCHITECTURE.md`](ARCHITECTURE.md),
  [`DECISIONS.md`](DECISIONS.md), and [`THREAT_MODEL.md`](THREAT_MODEL.md). In particular,
  no change may weaken authentication or loopback guards, invoke Slice 3 generation
  (D47), expose the D48 fixture-only candidate presentation outside local development,
  or implement the proposed pagination design (PR #54) without a separate maintainer
  decision.
- **The maintainer decides the merge method and performs the merge.** Contributors
  should not merge their own PRs. No specific review turnaround time is promised.

## Contribution etiquette

- Keep each pull request focused on one logical change. Split unrelated work into
  separate PRs.
- Avoid unrelated formatting or cleanup changes inside a focused PR; propose them
  separately.
- Add a regression test for every bug fix, and tests for new behavior.
- Never weaken, skip, or delete tests to make CI pass. If a test cannot run in your
  environment, describe the precise blocker in the PR instead of masking it.
- Do not introduce new dependencies without justification; adding one requires a
  recorded decision (see [`DEVELOPMENT.md`](DEVELOPMENT.md) §2).
- Discuss architecture-sensitive changes — execution, authentication, security, new
  infrastructure, or new API surface — in an issue **before** implementing them, as
  described under [Reporting a bug, requesting a feature, or proposing architecture
  changes](#reporting-a-bug-requesting-a-feature-or-proposing-architecture-changes).
- Fill in the pull-request template, including the verification and security sections,
  and reference the issue your change addresses, if one exists.

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

GitHub Issues are the source of truth for available work:
`https://github.com/soubhagya-behera/systivex/issues`. There is no static starter-task
list; the issue tracker is updated as work is planned and completed.

- Browse **open** issues first. Filter by the `good first issue` label for small,
  self-contained changes and by `help wanted` for issues where outside help is welcome.
  Labels are a hint, not a guarantee — always read the issue itself.
- An issue is available when it is **open**, has a clear problem statement and acceptance
  criteria, is not already claimed by someone else, and has not been closed, completed,
  superseded, or left unapproved. Check the recent comments for a claim before starting.
- Read the full issue and its recent discussion before you begin.
- **Comment before implementing** when the issue is architecture-sensitive, ambiguous,
  claimed or in active discussion, or when you need a scope decision. Wait for a response
  in those cases to avoid duplicate work. For a small, clearly scoped, unassigned issue,
  a short "I'd like to work on this" comment is enough to signal intent and you can
  proceed without waiting.
- If no existing issue matches what you want to do, open a new issue using the bug report
  or feature request form. Describe the problem, your proposed approach, and the scope.
  That is the normal public way to propose work and make it visible and claimable — you
  do not need a private introduction to contribute. Significant architectural changes
  must be discussed in an issue before implementation.
- Do not start obsolete, completed, closed, or unapproved tasks, and do not implement
  proposals that are explicitly marked as documentation-only or not adopted.
