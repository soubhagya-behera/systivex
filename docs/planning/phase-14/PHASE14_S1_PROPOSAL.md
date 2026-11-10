# PHASE14 S1 PROPOSAL — Deterministic Evidence Packet Assembler

**PROPOSAL ONLY — NOT AN APPROVED DECISION — IMPLEMENTATION NOT AUTHORIZED.**

This document proposes Phase 14 S1 for review. It does not authorize implementation, and it does not append D39 to `DECISIONS.md`. D39 may only be added after this proposal is reviewed and its scope is explicitly approved. This proposal depends on D38, published in PR #22 as commit `262ddd5b05417b0f9944347d54030596042a048b`.

This proposal is documentation only. It does not modify S0 documentation, PR #22, source code, tests, configuration, migrations, dependencies, or runtime behavior.

## 1. Problem and intended outcome

The current control plane exposes deterministic evidence through separate services: twin records, runtime observations, impact, risk, simulation, advisory policy, governed proposals, and audit verification. Future agent-assisted analysis needs a single, reviewable, deterministic evidence input so that explanations can be grounded in approved source data rather than in LLM output.

The intended outcome of S1 is a read-only, internal `evidence` package that assembles a transient evidence packet from existing domain services. The assembler must select and normalize allowlisted source fields, preserve source limitations, and remain strictly non-authoritative. It must not perform agent reasoning, generate natural-language conclusions, authorize changes, approve proposals, or execute operations.

## 2. Existing source-to-category mapping

### OBSERVED_FACT

- Twin entities from `TwinService.listEntities()` / `SystemEntityResponse`: `id`, `type`, `name`, `externalRef`, `environment`, `createdAt`, `updatedAt`.
- Twin relationships from `TwinService.listRelationships()` / `SystemRelationshipResponse`: `id`, `sourceId`, `targetId`, `type`, `createdAt`.
- Persisted runtime metadata from `SystemEntity.metadata`, but only an allowlisted runtime subset:
  - `runtime.health.available`
  - `runtime.health.status`
  - `runtime.observedAt`
  - `runtime.counters` limited to the allowlisted counter families
  - `runtime.limitations`
- Runtime observations are already persisted facts. The assembler must never invoke `POST /api/v1/twin/observations` merely to assemble evidence.

### DETERMINISTIC_FINDING

- `ImpactAnalysisService` / `ImpactResponse`: starts, impacted entities, evidence paths and steps, max depth, node/path counts, truncation, truncation reason, cycle state, snapshot timestamp, twin counts, limits, and advisory note.
- `RiskService` / `RiskResponse`: starts, risk entries, level, reasons, `uncertain`, `stale`, taxonomy version, snapshot timestamp, twin counts, and advisory note.
- Impact remains structural reachability, not confirmed breakage.
- Risk remains a deterministic advisory classification under `risk-v1`.

### HYPOTHETICAL_RESULT

- `SimulationService` / `SimulationResponse`: `simulated`, advisory marker, settings, before and after scopes, graph summary, delta, and limitations.
- Each before/after scope contains a complete impact response and a complete risk response.
- Simulation remains hypothetical and non-persistent.
- The packet must preserve the distinction between the full simulation response and the bounded simulation summary returned by policy evaluation.

### DETERMINISTIC_FINDING / HYPOTHETICAL_RESULT boundary

- `PolicyService` / `PolicyEvaluationResponse`: decision, policy version, advisory marker, settings, fired rules, bounded simulation summary, and note.
- `ALLOW` is an advisory outcome, not authorization.
- `REVIEW` and `DENY` remain advisory policy outcomes.
- The policy response has no before/after scopes, server timestamp, `simulated` flag, or risk taxonomy version. The packet must not fabricate these fields.

### GOVERNANCE_RECORD

- `ProposalService` and related proposal read contracts: proposal key, revision number, status, canonical operations, operations hash, assessment hash, policy decision, policy version, risk taxonomy, twin fingerprint, snapshot timestamp, expiry, and creation/update timestamps.
- Assessment evidence is bound to stored canonical operations, assessment hash, and twin fingerprint.
- User-authored free text such as title, description, reason, issuer, and subject is not trusted evidence input and is excluded by default.

### AUDIT_EVIDENCE

- Per-proposal audit events from `ProposalService.auditTrail(...)`.
- Independent chain verification from `AuditService.verify()`.
- Per-proposal audit events remain distinct from global chain verification.
- An ordered event list is not proof of chain integrity. Only the separate verification result provides that status.

## 3. Proposed internal architecture

Recommended S1 architecture:

- New backend package: `com.soubhagya.systivex.evidence`.
- Internal service: `EvidencePacketAssembler` (or `EvidencePacketService`).
- Immutable records/DTOs: `EvidencePacket`, `EvidenceItem`, `EvidenceReference`, and supporting limit/qualification metadata.
- Explicit source adapter interfaces, for example `EvidenceSource`, with one adapter per source family.
- Existing domain services as the only source of evidence.
- No cross-module repository access.
- No self-referential HTTP calls.
- No direct target-service access.

Recommended initial placement is internal to the modular monolith. No public REST endpoint is proposed for S1. A future public endpoint, if ever needed, requires a separate design and authorization decision.

The assembler must be read-only. It must not perform agent reasoning or generate natural-language conclusions. It supplies evidence only; a later, separately authorized phase may consume it.

## 4. Request and assembly semantics

Proposed internal request contract:

- `analysisMode`: mutually exclusive:
  1. `BASELINE` — existing impact and risk analysis.
  2. `SIMULATION` — explicit hypothetical simulation.
  3. `POLICY_EVALUATION` — explicit advisory policy evaluation, independently recomputed by the existing policy service.
- Common analysis inputs: `from` (1-10 roots), `maxDepth` (1-10, default 6), and `includeTypes` (default `SERVICE,API,DATABASE,TABLE`).
- Optional `proposalKey` and optional `revisionNumber` to request proposal evidence.
- Explicit `includeAuditEvidence` flag. Audit evidence must never be included implicitly.
- No caller-supplied impact, risk, policy decision, or simulation result may be accepted as authoritative evidence.

Simulation and policy evaluation must not be silently executed together. If a caller requests both, assembly must fail with an explicit conflict or require two separate packets. This preserves the existing D37 separation.

Reuse existing validation semantics:

- Maximum 10 starting roots (`ImpactQueryValidator.MAX_STARTS`).
- `maxDepth` between 1 and 10, default 6 (`ImpactQueryValidator.MAX_DEPTH_ALLOWED`, `DEFAULT_MAX_DEPTH`).
- Existing impact traversal caps: 500 nodes and 2,000 paths.
- Simulation operation validation remains server-side in `SimulationProposalValidator`.
- The frontend 10-operation usability limit is not a backend contract and must not be treated as one.

If a requested source is unbounded, the proposal must either define a safe bounded read path or explicitly defer that source. Unbounded reads must never be hidden.

Audit evidence is currently unbounded: `ProposalService.auditTrail(...)` calls `AuditEventRepository.findByProposalKeyOrderBySeqAsc(...)` without pagination. S1 should either:

1. Add a bounded audit read path with an explicit maximum and a recorded truncation limitation, or
2. Defer audit evidence until that bounded contract exists.

The recommended approach is option 1 with an explicit internal maximum (for example, 100 events) and an `auditEvidenceTruncated` limitation when more events exist. The exact cap requires review.

## 5. Evidence packet design

The packet is transient and packet-scoped. It has no durable ID and no persistence.

Proposed packet shape:

- `packetId`: packet-local reference only, such as `P1`.
- `items`: ordered list of `EvidenceItem`.
- `limits`: packet and source limits applied.
- `omissions`: explicit list of requested but unavailable or insufficient evidence.

Each `EvidenceItem` contains:

- `ref`: packet-local reference, such as `E1`, `E2`.
- `category`: one of the five D38 categories.
- `source`: exact original source or resource reference, such as `twin.entity`, `impact.analysis`, `risk.assessment`, `simulation.response`, `policy.evaluation`, `proposal.revision`, or `audit.chain.verify`.
- `scope`: relevant entity or proposal scope.
- `sourceVersion`: source-provided version where available, such as `risk-v1` or `policy-v1`.
- `timestamps`: source timestamps with their original semantics.
- `content`: allowlisted structured content only.
- `limitations`: source-provided limitations.
- `qualification`: support status, such as `CURRENT`, `STALE`, `UNCERTAIN`, `TRUNCATED`, `MISSING`, `HYPOTHETICAL`, or `HISTORICAL`.

Packet-local references must be deterministic and stable for the same input. No durable evidence ID, global snapshot ID, source hash, confidence score, or unsupported freshness guarantee may be introduced.

Evidence ordering must be deterministic. A recommended order is by category, then source, then scope, then packet-local reference.

The packet must not contain agent claims or narrative explanations in S1. The D38 claim structure is reserved for a later, separately authorized phase; S1 may define its shape as unavailable or unpopulated, but must not generate claims.

## 6. Data allowlists and exclusions

### Twin records

Allow:

- `id`, `type`, `name`, `externalRef`, `environment`, `createdAt`, `updatedAt`.
- Relationship `id`, `sourceId`, `targetId`, `type`, `createdAt`.
- Runtime `health.available`, `health.status`, `observedAt`, allowlisted counters, and source limitations.

Exclude:

- The entire arbitrary `metadata` map.
- Any metadata key not explicitly allowlisted.
- JWTs, authorization headers, passwords, client secrets, private keys, database credentials, connection secrets, environment secrets, raw HTTP response bodies, unnecessary issuer/subject identity details, unfiltered metadata, and sensitive business values not required for the specific analysis.

### Impact and risk

Allow:

- All fields currently exposed by `ImpactResponse` and `RiskResponse`, including paths, steps, truncation, cycle state, limits, counts, taxonomy version, uncertainty, stale flags, and advisory notes.

Exclude:

- Any field not present in the source DTO.
- Any fabrication of impact truncation or cycle fields in risk evidence, because `RiskResponse` does not expose them.

### Simulation

Allow:

- `simulated`, advisory marker, settings, before/after scopes, graph summary, delta, and limitations.
- Before/after impact and risk responses as source data.

Exclude:

- Any implication that simulation persisted or executed a change.

### Policy

Allow:

- Decision, policy version, advisory marker, settings, fired rules, bounded simulation summary, and note.

Exclude:

- Before/after scopes, server timestamps, `simulated` flag, and risk taxonomy version, because the policy response does not provide them.
- Any implication that `ALLOW` authorizes execution.

### Proposal governance

Allow by default:

- `proposalKey`, `revisionNumber`, `status`, `operations`, `operationsHash`, `assessment`, `assessmentHash`, `policyDecision`, `policyVersion`, `riskTaxonomy`, `twinFingerprint`, `snapshotObservedAt`, `expiresAt`, `createdAt`, `updatedAt`.

Exclude by default:

- `title`, `description`, `proposerIssuer`, `proposerSubject`, `approverIssuer`, `approverSubject`, and `reason`.
- Arbitrary user-authored text and identity details.
- Any field not explicitly allowlisted.

Proposal text may be considered for a future, separately reviewed display-only capability, but it must never be treated as trusted instruction or evidence content in S1.

### Audit evidence

Allow by default:

- `id`, `seq`, `occurredAt`, `action`, `outcome`, `proposalKey`, `revisionNumber`, `operationsHash`, `assessmentHash`, `prevHash`, `eventHash`.

Exclude by default:

- `actorIssuer`, `actorSubject`, `reason`, and `correlationId`, unless a documented requirement justifies a narrowly scoped use.
- Any claim that an ordered event list proves chain integrity.

Global chain verification evidence may include status, event count, head sequence, broken sequence, and diagnostic detail. It must remain distinct from per-proposal event evidence.

## 7. Consistency, bounds, and failure behavior

### 7.1 Confirmed source contracts

The following behavior was verified against the current implementation:

- `ImpactAnalysisService.analyze(...)` and `analyzeResolved(...)` are read-only transactions. They load all twin entities and relationships before traversal.
- `ImpactResponse.snapshotObservedAt` is generated at analysis time (`Instant.now()`), not a persisted twin snapshot version.
- `RiskService.assess(...)` and `assessResolved(...)` are read-only transactions. They reuse impact traversal and classify the impacted set.
- `RiskResponse.snapshotObservedAt` is also generated at classification time (`Instant.now()`). It is separate from any runtime `observedAt`.
- `RiskResponse` has no impact-style truncation or cycle fields.
- `SimulationService.simulate(...)` and `simulateResolved(...)` are read-only transactions. They load all twin entities and relationships and reuse impact and risk classification.
- `PolicyService.evaluate(...)` and `evaluateResolved(...)` are read-only transactions. Policy evaluation independently reruns simulation through `SimulationService`; caller-supplied risk or deltas are never trusted.
- `PolicyEvaluationResponse` is a bounded summary and does not include before/after scopes, server timestamps, a `simulated` flag, or risk taxonomy version.
- `TwinService.listEntities()` and `listRelationships()` are read-only transactions and load all rows.
- `ProposalService` read methods are read-only transactions. Selected-revision reads are bounded by the requested revision. List reads use page and size with a maximum page size of 100.
- `ProposalService.history(...)` and `auditTrail(...)` are unbounded reads.
- `AuditService.verify()` is a read-only transaction and reads all audit events in sequence order.

### 7.2 Unbounded sources requiring bounds or deferral

The following sources are read-only but are not safe to expose unbounded in an evidence packet:

- `TwinService.listEntities()` and `listRelationships()`: load the entire twin graph.
- `ProposalService.history(...)`: loads every revision for a proposal key.
- `ProposalService.auditTrail(...)`: loads every audit event for a proposal key.
- `AuditService.verify()`: loads every audit event in the global chain.

S1 must not include these sources unbounded. Recommended handling:

- Twin graph evidence: include only selected entity/relationship scopes or apply an explicit item cap with a recorded truncation limitation.
- Proposal history: include only the selected or latest revision by default; full history requires a bounded, separately reviewed read path.
- Proposal audit events: use a bounded read path with an explicit maximum and a recorded truncation limitation, or defer audit evidence until that contract exists.
- Global audit verification: include only the verification result, not the full event list, unless a bounded and separately reviewed read path exists.

### 7.3 Transaction and snapshot behavior

Confirmed behavior: each participating source service uses a read-only transaction and performs no writes in the proposed S1 read path.

Proposed behavior: `EvidencePacketAssembler` should be `@Transactional(readOnly = true)`. Its nested read-only service calls can join that transaction. A `REPEATABLE_READ` isolation level may be set at the assembler boundary to improve cross-source consistency. Nested service methods do not override the active transaction isolation.

The packet must not claim a single global snapshot unless the implementation proves that all participating reads occur in one transaction and that the chosen isolation level provides that guarantee.

Cross-source consistency limits:

- Source timestamps remain independently generated and must retain their original semantics.
- Runtime `observedAt`, impact/risk `snapshotObservedAt`, simulation before/after timestamps, and proposal assessment `snapshotObservedAt` must never be merged into one `lastUpdated`.
- `ImpactResponse.snapshotObservedAt` and `RiskResponse.snapshotObservedAt` are generated at analysis time (`Instant.now()`). They are not persisted twin snapshot versions and must not be presented as source mutation times.
- Runtime freshness uses the existing 30-minute `risk-v1` rule only where runtime metadata supports it.
- Risk has no impact-style truncation or cycle fields; the packet must not fabricate them.
- Policy evidence is a bounded summary, not full simulation scopes.

Bounds:

- Maximum 10 analysis roots.
- `maxDepth` between 1 and 10, default 6.
- Impact caps: 500 nodes and 2,000 paths.
- Proposed packet safety caps: 100 evidence items per source family and 1,000 total items. These are proposed internal safety limits, not existing backend guarantees, and require review.
- Proposed audit read cap: 100 events with explicit truncation reporting. This requires a bounded read path and review.

Failure behavior:

- Required analysis source failure must abort packet assembly with the original exception. It must not be converted into an empty result.
- Missing optional proposal evidence must be recorded as an omission with a limitation.
- Missing audit evidence must be recorded as an omission with a limitation.
- If audit evidence exceeds the configured cap, the packet must record an explicit truncation limitation.
- If simulation or policy evaluation cannot be performed, the packet must record the omission and reason, not a safe or empty conclusion.
- No source failure may be silently converted into an empty result or a safe conclusion.

## 8. Security and trust requirements

Preserve D28-D38 and all D29-D31 gates.

The evidence assembler must:

- Be read-only.
- Have no credentials or token access.
- Have no arbitrary network, filesystem, shell, or actuator access.
- Never call target services.
- Never mutate proposals or audit records.
- Never create approval or execution authority.
- Treat metadata and proposal text as untrusted data.
- Use explicit allowlists for every serialized field.

No `TWIN_AGENT` role.

No new endpoint or security matcher in this S1 proposal. A future public endpoint requires a separate design and authorization decision.

### 8.1 Caller context

The internal assembler must not receive JWTs, authorization headers, credentials, secrets, or raw tokens.

Caller identity is not required for S1 because the proposed read path uses existing services that do not require identity for read-only evidence assembly. If a future HTTP boundary exposes evidence packets, authorization must occur at that boundary using trusted principal information. The assembler itself must remain credential-free and must not introduce a new role.

## 9. Verification proposal

Tests should cover:

- All five evidence categories.
- Correct source and resource provenance.
- Stable ordering and packet-local references.
- Timestamp preservation.
- Stale and uncertain runtime evidence.
- Impact truncation and cycle limitations.
- Hypothetical versus deterministic distinction.
- Policy `ALLOW` non-authorization.
- Audit list versus chain-verification distinction.
- Proposal data minimization.
- Arbitrary metadata and user-text exclusion.
- Explicit omission of unavailable evidence.
- Bounded output and truncation markers.
- No database mutation or proposal lifecycle mutation.
- No target-service calls.
- No credentials, tokens, or sensitive identity leakage.

## 10. Explicit non-goals

S1 does not authorize:

- LLM, Spring AI, Ollama, or cloud provider integration.
- Agent orchestration or multi-agent architecture.
- Prompt engineering or model-generated claims.
- MCP or unrestricted tool execution.
- Embeddings, memory, or vector database.
- Evidence persistence or migrations.
- Background jobs, schedulers, or retention.
- Frontend work.
- New REST or SSE endpoint.
- New roles or authorization changes.
- Proposal approvals, execution, or actuator integration.
- Production identity or hosting changes.

## 11. Alternatives considered

1. Public REST endpoint for evidence packets in S1.
   Rejected for S1 because endpoint exposure, request validation, and security review require a separate authorization decision.

2. Direct repository access from the evidence package.
   Rejected because it violates module boundaries and bypasses existing service validation and transaction semantics.

3. Self-referential HTTP calls to existing endpoints.
   Rejected because they add network overhead, duplicate authentication complexity, and create avoidable failure modes.

4. LLM-generated claims inside S1.
   Rejected because D38 separates evidence assembly from narrative generation and claim authorization.

5. Unbounded audit event inclusion.
   Rejected because `ProposalService.auditTrail(...)` currently has no pagination. S1 must either add a bounded read path or defer audit evidence.

6. A single claimed global snapshot.
   Rejected unless the implementation can prove transactional snapshot behavior across all participating services.

## 12. Resolved design decisions

### A. Transaction isolation

Recommendation: use one assembler method annotated `@Transactional(readOnly = true)` with the default `READ_COMMITTED` isolation.

Rationale and trade-offs:

- Confirmed behavior: `ImpactAnalysisService`, `RiskService`, `SimulationService`, `PolicyService`, `TwinService`, `ProposalService`, and `AuditService` all use read-only transactions and perform no writes in the proposed S1 read path.
- Spring's default propagation is `REQUIRED`. Nested read-only service calls invoked from the assembler join the active transaction instead of starting independent transactions.
- `READ_COMMITTED` is the safest default for S1 because it avoids implying a cross-source snapshot. Each statement observes committed data at statement time, while source timestamps record the actual observation/analysis times.
- `REPEATABLE_READ` would provide one database snapshot for the transaction, but it is a database transaction snapshot, not a globally synchronized snapshot. It would also introduce retry and serialization considerations if future code adds writes to the same transaction.
- The packet must record source timestamps independently and must not claim a global snapshot under either isolation level.

### B. Packet and source limits

Recommended concrete limits for S1:

- Analysis inputs: maximum 10 roots, `maxDepth` 1-10 with default 6.
- Impact traversal: maximum 500 nodes and 2,000 paths, as implemented.
- Simulation operation validation: no backend maximum operation count exists. The frontend limit of 10 operations is a usability limit only and must not be treated as a backend contract.
- Packet item limits: 100 evidence items per source family and 1,000 evidence items total.
- Serialized packet size: 256 KiB (262,144 bytes) of UTF-8 JSON.
- Audit events: 100 events maximum per packet.

If any source exceeds its item cap, the packet must include the first N items in deterministic order and set the source status to `TRUNCATED` with an explicit limitation. If the serialized packet would exceed 256 KiB, apply source caps in deterministic order and record which source was truncated. Every truncation must be explicit; silent omission is prohibited.

For simulation operations, include the operation count and canonical operations hash in every simulation evidence item. Include the full operation list only when the count is 100 or fewer. If the count exceeds 100, set `operationsTruncated = true` and include the first 100 operations in canonical order. This is a proposed packet safety rule, not an existing backend contract.

### C. Audit evidence

Recommendation: do not use `AuditService.verify()` as a bounded partial verifier in S1. Its current implementation reads the entire audit chain. Global chain-verification evidence is deferred from S1.

Per-proposal audit evidence may be included only behind a bounded read path:

- Maximum 100 events per packet.
- Deterministic order by `seq` ascending.
- `auditEventsTruncated = true` when more events exist.
- Explicit limitation text when truncation occurs.

The packet must keep per-proposal audit events distinct from global chain verification. Partial event retrieval never establishes chain integrity. Only the separate `AuditService.verify()` result can establish chain status, and that result is deferred from S1 because the current implementation is unbounded.

### D. Proposal text and identity data

Recommendation: exclude free-form `title`, `description`, `reason`, `proposerIssuer`, `proposerSubject`, `approverIssuer`, `approverSubject`, and `correlationId` from the initial evidence packet.

Include structured governance data only:

- `proposalKey`, `revisionNumber`, `status`, `operations`, `operationsHash`, `assessment`, `assessmentHash`, `policyDecision`, `policyVersion`, `riskTaxonomy`, `twinFingerprint`, `snapshotObservedAt`, `expiresAt`, `createdAt`, `updatedAt`.

Rationale: user-authored text and identity details are untrusted data and can be used for prompt injection or unnecessary data exposure. Structured operation types, identifiers, hashes, versions, fingerprints, and timestamps are required for provenance and can be included safely. Hashes retain their original scope and purpose; they are not proof that the entire current system state is correct.

### E. Caller context

Recommendation: the internal assembler must not receive JWTs, authorization headers, credentials, secrets, or raw tokens.

Caller identity is not required for S1 because the proposed read path uses existing services that do not require identity for read-only evidence assembly. If a future HTTP boundary exposes evidence packets, authorization must occur at that boundary using trusted principal information. The assembler itself must remain credential-free and must not introduce a `TWIN_AGENT` role or any new role.

## 12.1 Source availability and failure statuses

The following statuses are proposed packet-level statuses. They are not existing API fields.

- `AVAILABLE`: the source returned complete allowlisted evidence.
- `UNAVAILABLE`: the source could not be read, or required evidence was missing.
- `OMITTED`: the source was not requested, not applicable to the selected mode, or deliberately excluded by policy.
- `TRUNCATED`: the source returned more data than the configured cap, or the source itself reports truncation.

Failure behavior:

- If a required analysis source fails, abort packet assembly and propagate the original exception. Do not return a partial packet as success.
- If an optional proposal is not found, record an `OMITTED` or `UNAVAILABLE` item with a limitation and continue.
- If an optional audit source is not found or cannot be read safely, record an `OMITTED` or `UNAVAILABLE` item with a limitation and continue.
- If a source returns partial or truncated evidence, include the available portion, mark the item `TRUNCATED`, and record the limitation.
- If a source exceeds its configured cap, include the first N items in deterministic order, mark the item `TRUNCATED`, and record the cap and omitted count.
- Missing evidence must never be converted into an empty successful result or an implicit safe conclusion.

## 13. Remaining risks and deferred capabilities

- Serialization versioning for any future evidence persistence remains deferred.
- A future public endpoint requires a separate design and authorization decision.
- Audit evidence may be deferred if maintainers reject a bounded read-path change.
- Global audit chain verification remains deferred from S1 because the current verifier reads the entire chain.
- Packet size and item caps are proposed safety limits, not existing backend guarantees.

## 14. Acceptance checklist

S1 is acceptable only if all of the following are true:

- Packet output is deterministic for the same input, including item order and packet-local references.
- Packet size does not exceed 256 KiB.
- No source family exceeds 100 items and no packet exceeds 1,000 items.
- Every truncation is explicitly represented with a limitation.
- Every evidence item has exact source and resource provenance.
- Impact, risk, simulation, policy, proposal, and runtime timestamps retain their original semantics.
- Runtime evidence preserves stale and uncertain flags.
- Impact truncation and cycle limitations are preserved.
- Hypothetical simulation results remain distinguishable from deterministic findings.
- Policy `ALLOW` is never represented as authorization.
- Per-proposal audit events remain distinct from global chain verification.
- Proposal evidence excludes free-form text and unnecessary identity details.
- Arbitrary metadata and user-authored text are excluded.
- Unavailable evidence is explicitly omitted or marked unavailable, never silently converted to an empty result.
- Assembly is read-only and performs no database mutation or proposal lifecycle mutation.
- No target-service calls occur.
- No credentials, tokens, authorization headers, secrets, or sensitive identity information appear in the packet.
- No LLM, agent runtime, provider, endpoint, migration, dependency, scheduler, or persistence is introduced.

## 13. Approval gates

Implementation of Phase 14 S1 is not authorized by this proposal. Required gates:

1. Review of this proposal.
2. Explicit approval of the S1 scope and limits.
3. Security review of allowlists, exclusions, and data minimization.
4. Approval of any bounded audit read-path change.
5. A separate D39 decision appended to `DECISIONS.md` only after the above gates are satisfied.

Until those gates are satisfied, this document remains a proposal only.
