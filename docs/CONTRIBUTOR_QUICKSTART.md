# Contributor Quickstart

Welcome! You do not need any prior connection to the maintainer to contribute to
Systivex. This page is a short entry point. Everything here links to the fuller
documentation, so you can start quickly and dig deeper when you need to.

## What Systivex is, in plain language

Systivex builds a living **System Twin** of a software system: a persisted model of its
services, APIs, databases, tables, and the relationships between them. When someone
proposes a change, Systivex can work out **what might be affected**, classify **risk**,
run a **hypothetical** before/after simulation, and evaluate an **advisory** policy
decision — then record the decision in a tamper-evident audit ledger.

It does **not** execute changes. Impact and risk are deterministic and advisory;
simulation is hypothetical and stored nowhere; an `ALLOW` from policy is not permission
to act; and an approved proposal is a governance record, not an instruction to execute.
See the [README](../README.md) for the feature overview and
[ARCHITECTURE.md](../ARCHITECTURE.md) for the design.

## What contributions are welcome

- **Backend code and tests** — Java 21 control plane in `backend/` (twin, observation,
  intelligence, policy, proposal, telemetry) and the four sample target services under
  `backend/target-services/`.
- **Frontend code and tests** — the React + Vite + JavaScript operator console in
  `frontend/`, including accessibility, error handling, and test coverage.
- **Bug reports, feature proposals, documentation, and CI improvements.**

Work that changes execution capabilities, authentication, security controls, or
architecture is welcome as a **discussion first** — open an issue before implementing,
and check [DECISIONS.md](../DECISIONS.md). Some areas are deliberately gated (see
[Boundaries](#boundaries-to-respect) below).

## Where to find work

Use the public issue tracker:
[github.com/soubhagya-behera/Systivex/issues](https://github.com/soubhagya-behera/Systivex/issues).

- Look for issues labelled `good first issue` (small and self-contained) or
  `help wanted` (where outside help is useful). Always read the issue and its recent
  comments; labels are a hint, not a guarantee.
- If no suitable issue is currently open, you can propose a bug fix or feature through
  the public issue forms. Describe the problem, your proposed approach, and the scope.
  That is the normal public way to propose work — no private introduction is needed.
- For a small, clearly scoped, unassigned issue, comment briefly to signal intent and
  start. For architecture-sensitive, ambiguous, or already-discussed issues, comment and
  wait for a reply first. Full guidance is in
  [CONTRIBUTING.md — Finding work to do](../CONTRIBUTING.md#finding-work-to-do).

## Set up locally

Install the prerequisites and follow the existing setup guide — it is the authoritative
source:

| Need | Where to look |
| --- | --- |
| JDK 21, Docker, PostgreSQL 18, Node 24 LTS | [DEVELOPMENT.md §1](../DEVELOPMENT.md#1-prerequisites) |
| Control-plane database and environment | [DEVELOPMENT.md §2a](../DEVELOPMENT.md#2a-local-database-setup-once-per-machine-never-committed) |
| Target-service databases | [DEVELOPMENT.md §8a](../DEVELOPMENT.md#8a-local-target-database-setup-once-per-machine-never-committed) |
| Running and testing the backend | [DEVELOPMENT.md §3–§5](../DEVELOPMENT.md#3-maven-wrapper-commands) |
| Running and testing the frontend | [DEVELOPMENT.md](../DEVELOPMENT.md) §18 |

Short version (full details and Windows notes are in DEVELOPMENT.md):

```bash
cd backend
./mvnw clean test          # Java 21; requires a running Docker daemon

cd ../frontend
npm ci                     # Node 24 LTS
npm test
npm run build
```

You do not need the frontend to work on the backend, or the backend toolchain to work on
a pure frontend change. PostgreSQL is needed only to **run** the application; tests use
throwaway Testcontainers databases.

## Fork, branch, and open a pull request

You do not need write access. Fork
`soubhagya-behera/systivex`, clone your fork, and add the original as `upstream`:

```bash
git clone https://github.com/<your-username>/systivex.git
cd systivex
git remote add upstream https://github.com/soubhagya-behera/Systivex.git
git fetch upstream
git switch -c docs/my-first-change upstream/main
# implement, run the applicable checks, then:
git push -u origin docs/my-first-change
```

Use a prefix such as `feat/`, `fix/`, `test/`, `docs/`, `refactor/`, or `ci/`, and include
the issue number when one exists. Open a pull request against
`soubhagya-behera/systivex` `main` and fill in the pull-request template. CI starts
automatically; a maintainer reviews the change, may request adjustments, and decides the
merge method. CI must pass and project boundaries must be respected before merge. The
complete walkthrough is in
[CONTRIBUTING.md — Fork-based contribution workflow](../CONTRIBUTING.md#fork-based-contribution-workflow)
and [What happens after you open a pull request](../CONTRIBUTING.md#what-happens-after-you-open-a-pull-request).

## Report bugs and security issues

- **Bugs and feature ideas:** open an issue using the
  [bug report or feature request form](https://github.com/soubhagya-behera/Systivex/issues/new/choose).
  Remove tokens, credentials, and real data from anything you paste.
- **Security vulnerabilities:** do **not** open a public issue. Use the repository's
  **Security → Report a vulnerability** flow described in [SECURITY.md](../SECURITY.md),
  or contact the maintainer privately via their GitHub profile.

## Boundaries to respect

- Systivex is a **modular monolith**, not a microservices implementation.
- Impact and risk analysis stay deterministic and advisory; simulation stays hypothetical
  and non-persistent; policy `ALLOW` is not authorization; approval is not execution.
- Controlled execution is **not implemented** and remains gated (D30/D31).
- Phase 14: D47 remains independently binding — S2 Slice 3 must not be invoked,
  continued, exposed, or integrated. The D48 fixture-only candidate presentation stays
  synthetic and local-development-only. The pagination proposal (PR #54) is
  **PROPOSED / NOT ADOPTED / DOCUMENTATION ONLY** and authorizes no implementation.

Read the decisions relevant to the area you touch before changing it; see
[DECISIONS.md](../DECISIONS.md) and the governance section of
[CONTRIBUTING.md](../CONTRIBUTING.md#frontend-convention).

## Go deeper

| Document | Use it for |
| --- | --- |
| [README.md](../README.md) | What Systivex does and what exists today |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | Detailed contribution rules and workflow |
| [DEVELOPMENT.md](../DEVELOPMENT.md) | Build, test, run, configure, and API walkthroughs |
| [ARCHITECTURE.md](../ARCHITECTURE.md) | Modules, trust boundaries, and current status |
| [DECISIONS.md](../DECISIONS.md) | Binding architectural decisions D1–D48 |
| [THREAT_MODEL.md](../THREAT_MODEL.md) | Security principles |
| [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md) | Community expectations |
| [SECURITY.md](../SECURITY.md) | Private vulnerability reporting |
