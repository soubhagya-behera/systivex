# Systivex Threat Model

Scope: **foundation + persistence + target-runtime principles** (2026-09-29). Enforcement
mechanisms (policy engine, approvals, audit store) are **planned, not implemented** —
these principles bind all future design so the mechanisms can be built correctly.

## 1. System under analysis (today)

A single Spring Boot control plane on `:8080` exposing Actuator `health` plus a
local twin CRUD API (`/api/v1/twin`), backed by PostgreSQL via Flyway migrations
and validated JPA mappings. Alongside it, four Phase 2A target services on
`:8081–:8084` (gateway → order → inventory/payment) with open local HTTP APIs
and no persistence. No user data of value yet, no agents, no execution
capability, and the control plane does not call the target services.
Credential handling is already load-bearing: the datasource password
reaches the app only through `SYSTIVEX_DB_PASSWORD`; the live
`application.properties` is git-ignored and only the secret-free example
template is tracked. The target services hold no credentials at all.

## 2. Trust principles (binding)

### T1. The LLM is untrusted

- LLM output is **data**, never instruction. It is never executed, never used as
  authorization, and never treated as fact about the target system.
- Tool and connector outputs are likewise data by default, not authority:
  they inform proposals but never substitute for control-plane authorization.
- Every LLM-derived claim used in a decision must be corroborated against the
  System Twin or live backend state before it influences action.

### T2. User input is untrusted

- All input arriving at the control plane (REST bodies, SSE parameters, future
  UI/API calls) is validated, size-bounded, and encoded on output.
- Validation (`spring-boot-starter-validation` is present) must be applied at
  every trust boundary; validation failures fail closed with no side effects.

### T3. Backend systems are authoritative

- Ground truth is the System Twin (planned) backed by versioned backend state
  (code, config, runtime signals) — not model output, not client assertions.
- Reads that inform consequential decisions must come through authenticated,
  integrity-checked connectors; stale or unverifiable state blocks action, not permits it.

### T4. The agent cannot authorize itself

- Proposal and authorization are separate roles. The agent (planned) may propose
  changes and request actions; only the control plane's policy/execution path
  (planned) may authorize them.
- No tool, prompt, credential, or code path may allow an agent to mint, escalate,
  or replay its own authorization.

### T5. Consequential actions require control-plane authorization

- Any action with side effects outside the control plane (target-environment
  change, credential use, data mutation, outbound call) must cross the
  policy → execution boundary with an explicit grant.
- Default-deny: absence of a policy decision is denial. Failures fail closed.

### T6. High-risk actions require approval

- Actions classified high-risk (production writes, destructive operations,
  security-sensitive changes — classification to be defined with the policy engine)
  additionally require explicit human or delegated-policy approval before execution.
- Approval is bound to the exact assessed proposal (parameters, target, twin
  revision); any material change re-enters assessment.

### T7. Consequential actions must be auditable

- Every authorization, approval, execution, and verification outcome produces an
  append-only record: who/what was proposed, which policy version decided,
  who approved (if required), what executed, and what verification observed.
- Audit records must be sufficient to reconstruct and explain any change after the fact.

## 3. Out-of-scope today, in-scope for design

- Authentication/authorization mechanism (the twin API and all four target
  service APIs are currently open — acceptable on a local machine, not
  beyond it), secret management, key rotation.
- Twin-connector authentication and data-integrity guarantees.
- Approval UX and delegation model; risk-classification taxonomy.
- Execution sandboxing, blast-radius limits, rollback/verification loops.
- Persistence hardening and audit-log immutability (tables exist now;
  hardening still to come).

Each must reference the principle(s) above when introduced; any exception
requires a dated entry in `DECISIONS.md`.

## 4. What must NOT happen next (anti-goals)

- Direct agent-to-target execution paths bypassing the control plane.
- LLM output concatenated into queries, shell, or config without mediation.
- Silent policy bypasses, self-approval, or unaudited "break-glass" writes.
- Broadening the Actuator or API surface without a recorded security review.
- Hibernate schema auto-generation (`update`/`create`/`create-drop`) against any
  database that matters; committed credentials in any form.
