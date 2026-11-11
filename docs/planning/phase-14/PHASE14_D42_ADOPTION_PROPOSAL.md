# Phase 14 D42 Adoption Proposal — Phase 14 S2 Slice 3 Design

## PROPOSED — NOT ADOPTED — NO IMPLEMENTATION AUTHORIZATION

This file is a governance proposal only. It is not an entry in canonical `DECISIONS.md`, does not adopt D42, and does not authorize implementation of Slice 3.

**Source design:** `docs/planning/phase-14/PHASE14_S2_SLICE3_PROPOSAL.md`
**Source design SHA-256:** `DE5189CF7E15D23921B590759B9F26E9E5204BD0197B00CF225A8CBF1A0A6BB3`
**Source design length:** 813 lines
**Baseline commit:** `c6ae7265339c2aeebec58db0f0b22be79dcf2e09`
**Governing decisions:** D38, D39, D40, D41 (adopted and immutable)

---

## 1. Decision identifier

**Proposed decision:** D42 — Phase 14 S2 Slice 3: Evidence-Grounded Generation and Evaluation Harness (design adoption only)

**Status:** PROPOSED — NOT ADOPTED — NO IMPLEMENTATION AUTHORIZATION

**Purpose:** Consider whether to adopt the reviewed Phase 14 S2 Slice 3 design as the binding design for a future implementation decision.

---

## 2. Decision statement

D42, if adopted, would adopt the Phase 14 S2 Slice 3 design as the authoritative design for a future, separately authorized implementation.

D42 would **not**:

- implement Slice 3;
- authorize any implementation of Slice 3;
- create code, tests, dependencies, configuration, endpoints, persistence, frontend, roles, or execution capability;
- conflate adoption of D42 with authorization to implement Slice 3;
- amend D38, D39, D40, or D41.
Adoption of the design and authorization to implement the design are distinct decisions. Completion of implementation is a further, separate state. D42 may adopt the design only.

---

## 3. Context and rationale

Phase 14 S1 (D39) provides a deterministic, read-only, transient `EvidencePacket`. Phase 14 S2 Slice 1 (D40) provides the provider-independent claim contract and deterministic `ClaimValidator`. Phase 14 S2 Slice 2 (D41) provides the bounded local model provider. Slice 3 is the next planned capability: evidence-grounded candidate claim generation and offline/adversarial evaluation.

The reviewed Slice 3 design proposes the smallest internal read-only pipeline that reuses all three existing boundaries:

```text
EvidencePacket
  -> deterministic prompt construction
  -> existing LocalModelAdapter
  -> strict bounded parsing
  -> candidate EvidenceClaim objects
  -> existing deterministic ClaimValidator
  -> transient generation result
```

No automatic claim acceptance is proposed. Human review remains mandatory. The design has passed two independent architectural reviews and a final targeted audit.

---

## 4. Adopted design scope (if D42 is adopted)

### 4.1 Pipeline

The adopted design would be:

1. Accept only a live in-process `EvidencePacket` produced by `EvidencePacketAssembler`.
2. Build one deterministic prompt from the packet using fixed, canonical, allowlisted serialization.
3. Call `LocalModelAdapter.infer(String)` exactly once.
4. Parse the bounded response with a strict parser into candidate claim structures.
5. Construct `EvidenceClaim` objects, injecting `packet.packetRef()` and `packet.mode()` from the packet, never from model output.
6. Call `ClaimValidator.validate(packet, claim)` for each candidate.
7. Return a transient result containing candidates, validation results, and safe failure categories.

No generated claim may be automatically accepted. Human review remains authoritative.

### 4.2 Prompt safety

The adopted design preserves the following as data, never instructions:

- evidence content is untrusted data;
- prompt delimiters are not a security boundary;
- the model and generation component have no authority;
- no database, filesystem, shell, credentials, target-service, or arbitrary network access;
- no tools or function calling;
- policy `ALLOW` remains advisory;
- hypothetical results remain hypothetical;
- `APPROVED` remains a stored governance fact;
- audit evidence never implies chain integrity;
- human review remains authoritative.

Prompt construction may reduce accidental instruction following but does not eliminate prompt injection. The security boundary is the combination of no tool access, strict parsing, deterministic validation, no auto-acceptance, and mandatory human review.

### 4.3 Provenance and validation

- Candidate claims may reference only `E<n>` values present in the same packet.
- `ClaimValidator` remains the sole authority for reference existence, category consistency, qualification, limitation inheritance, insufficiency, and wording.
- Unknown, fabricated, or foreign references are rejected; they are never remapped or silently repaired.
- Category, qualification, limitations, packet identity, and mode are preserved.
- Validation proves deterministic structure and provenance only. It does not prove semantic entailment.

### 4.4 Evaluation

The adopted design includes an offline evaluation harness with:

- human-labelled evidence/claim fixtures;
- coverage across all five evidence categories and all three modes;
- adversarial cases for fabricated/foreign references, missing limitations, unsupported causal/certainty language, invented facts, prompt injection, policy `ALLOW` misuse, hypothetical-as-executed confusion, and audit-list-as-chain-integrity confusion;
- metrics for parse success, structural validity, validator acceptance/rejection, reference precision, limitation preservation, unsupported-claim rate, and human-review agreement;
- optional model-assisted evaluation as diagnostic only;
- no confidence scores or calibrated probability claims.

Evaluation cannot approve generated claims. Human review remains mandatory.

---

## 5. Resource and safety limits

D42 recommends adopting the Slice 3 numeric resource limits below as binding design constraints, subject to formal adoption of D42. They are design-adoption terms, not implemented guarantees. Once D42 is adopted, changing any of these values requires a separate explicit decision.
### 5.1 Inherited D41 limits (unchanged)

| Limit | Value | Source |
|---|---|---|
| Provider | Local Ollama only | D41 |
| Response cap | 65,536 bytes | D41 |
| Connect timeout | 5 seconds | D41 |
| Read timeout | 30 seconds | D41 |
| Inference deadline | 35 seconds monotonic | D41 |
| Inference attempts | Exactly one | D41 |
| Retry/backoff | None | D41 |
| Queue | None | D41 |
| Cloud fallback | None | D41 |
| Concurrency | Single-flight semaphore | D41 |
| Proxy | Direct only | D41 |
| Redirects | Disabled | D41 |
| Model identity | Installed-model digest via `/api/tags` and `/api/show` | D41 (G-4 limitation preserved) |
| Endpoint | Loopback HTTP IP literal with explicit port | D41 |
| Pull strategy | `PullModelStrategy.NEVER` | D41 |

### 5.2 Proposed Slice 3 limits

| Limit | Proposed value | Notes |
|---|---|---|
| Prompt cap | 32,768 UTF-8 bytes | Complete deterministic prompt measured immediately before inference; excludes HTTP serialization overhead |
| D40 structural parser claim limit | 100 claim records | Existing D40 contract; unchanged |
| Slice 3 generated-candidate claim cap | 25 claim records | Separate Slice 3 generation boundary |
| Max references per claim | 1–20 | Existing `EvidenceClaim` contract |
| Max limitations per claim | 0–100, each 1–1,000 characters | Existing `EvidenceClaim` contract |
| Max statement length | 1,000 characters | Existing `EvidenceLimits.MAX_TEXT_LENGTH` |
| Max nesting depth | 8 (candidate) | Parser constraint |
| Max JSON name length | 64 characters (candidate) | Parser constraint |
| Max document length | 65,536 bytes | Inherited response cap |
| Total orchestration budget | 45 seconds (proposed) | 5 seconds prompt construction, 35 seconds D41 inference, 5 seconds parsing/validation |
| Raw output retention | None in runtime path; transient in-memory evaluation only | See Section 7 |

### 5.3 Prompt fail-closed behavior

If the completed prompt exceeds 32,768 UTF-8 bytes:

- do not call the model;
- do not truncate evidence;
- do not silently omit evidence;
- do not select a smaller evidence subset;
- do not summarize evidence to force it under the limit;
- return `PROMPT_TOO_LARGE` with no candidate claims.

A packet may validly pass S1 assembly and still be ineligible for Slice 3 generation because its completed prompt exceeds the separate prompt cap.

### 5.4 Claim-count boundary

D40's structural parser limit and Slice 3's generation-specific cap are distinct:

- D40 already authorizes a structural parser limit of 100 claim records.
- Slice 3 proposes a generation-specific candidate limit of 25 claims.
- Exceeding 25 rejects the entire generated response.
- No silent truncation is permitted.
- `ClaimValidator` does not enforce either count limit.
- Malformed structure, parser-limit violations, and generation-cap violations must remain distinguishable in failure reporting.

### 5.5 Deadline and cancellation

D42 recommends adopting the 45-second total Slice 3 orchestration budget as a binding design constraint, subject to formal adoption of D42. It does not weaken D41's 35-second inference deadline.
Cooperative monotonic deadline checks must occur:

- at orchestration entry;
- during prompt construction;
- immediately before inference;
- immediately after inference;
- while parsing tokens or records;
- during candidate validation;
- before returning the result.

If the deadline expires, stop subsequent processing, discard all partial candidate claims, and return `DEADLINE_EXCEEDED`.

Synchronous parsing cannot be arbitrarily preempted. A blocking parser call can only be cancelled at the next cooperative check or between tokens/records. The design does not claim unsupported preemptive cancellation. `LocalModelAdapter.infer(String)` owns and clears its own D41 deadline; Slice 3 must not assume that deadline remains active after `infer()` returns.

### 5.6 Parser controls

The design uses Jackson 3.1.5:

- `tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION`
- `tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`

Structural limits include nesting depth, string length, JSON name length, document length, claim count, reference count, and limitation count. D40's deferred token-limit handling remains deferred. UTF-8 byte caps must not be described or treated as token limits.

---

## 6. Security and trust boundaries

The design preserves the following boundaries:

- evidence remains untrusted input;
- the generator and model have no authority;
- no semantic-entailment guarantee;
- no tools or function calling;
- no RAG, vector database, agent runtime, or external provider;
- no cloud fallback;
- no second-LLM authority;
- no automatic acceptance of generated claims;
- no new endpoint, persistence, frontend, role, or execution capability;
- future endpoint exposure requires a separate future decision.

Evidence content may contain instruction-like text. Prompt delimiters reduce accidental instruction following only; they do not eliminate prompt injection.

---

## 7. Evaluator raw-output access

The runtime generation path and offline evaluation harness have different access requirements.

Runtime path:

- no raw prompt or raw model output in results, exceptions, logs, telemetry, or endpoints;
- candidate claims and validation results only.

Offline evaluation harness:

- may inspect raw model output transiently in memory through a test/evaluation seam;
- may classify parse failures and calculate metrics;
- must not write raw prompt or raw model output to durable storage, logs, telemetry, or API responses;
- must not expose raw output to users;
- must not grant additional authority to the evaluator or model;
- must not use a model-as-judge to approve claims;
- human review remains final authority.

---

## 8. Recommendations on open design choices

### 8.1 Package name

**Recommendation:** Defer the exact package name to the implementation authorization decision.

D42 should adopt the boundary requirement only: an internal package depending on `evidence` and `localmodel`, with no reverse dependency. Exact naming is an implementation detail and should not be fixed by design adoption unless review identifies a naming constraint.

### 8.2 Fixed constants versus `GenerationOptions`

**Recommendation:** Adopt fixed constants for the first implementation.

Fixed constants are deterministic, reviewable, and avoid a premature configuration surface. A `GenerationOptions` value should be introduced only if a later decision identifies a concrete need and justifies the additional authority and review surface.

### 8.3 Adoption of the 25-claim and 32,768-byte prompt limits

**Recommendation:** Adopt both as binding Slice 3 design constraints, subject to formal adoption of D42.

The 25-claim generation cap is independent of D40's existing 100-record structural parser limit. Exceeding 25 generated claims rejects the entire response. No silent truncation.

An oversized prompt fails closed with `PROMPT_TOO_LARGE` before inference. No evidence truncation, omission, subsetting, or summarization.
These are design-adoption terms, not implemented guarantees. Once D42 is adopted, changing either limit requires a separate explicit decision.

### 8.7 Orchestration deadline

**Recommendation:** Adopt the 45-second total Slice 3 orchestration budget as a binding design constraint, subject to formal adoption of D42:

- Maximum 5 seconds for deterministic prompt construction.
- Existing D41 maximum 35 seconds for local inference, unchanged.
- Maximum 5 seconds for parsing and candidate validation.

The sum is the proposed 45-second end-to-end orchestration budget.

Preserve monotonic measurement, cooperative deadline checks, fail-closed behavior, discard of partial candidate claims and results on expiry, and honest limitations of synchronous parsing and cancellation. The 45-second budget is a design-adoption term, not an already implemented guarantee. Future changes require a separate explicit decision.

### 8.4 Evaluation sample sizes and strata
**Recommendation:** Defer numeric sample sizes and strata.

The design already requires coverage across categories, modes, and adversarial cases. Specific sample counts require evidence and calibration that are not available in this repository. D42 should not invent thresholds.

### 8.5 Model-assisted diagnostic evaluation

**Recommendation:** Adopt the boundary only, not the use.

D42 may adopt the rule that any model-assisted evaluation is diagnostic only and non-authoritative. Whether such evaluation is used at all should remain a separate implementation or evaluation decision.

### 8.6 Future endpoint exposure

**Recommendation:** Explicitly defer endpoint exposure.

Slice 3 has no public endpoint. Any future endpoint requires a separate design and authorization decision, including authorization, trust-boundary, and presentation review.

---

## 9. Acceptance criteria for a future implementation

The following are future implementation acceptance requirements. They are not completed tests or implemented guarantees:

1. Generation rejects null or inconsistent packets with `INVALID_PACKET`.
2. Generation never accepts serialized or deserialized packet input.
3. Candidate claims always carry packet ref and mode from the packet.
4. Unknown, fabricated, or foreign references are rejected by `ClaimValidator`.
5. Category mismatch is rejected.
6. Qualification overstatement is rejected.
7. Missing limitations are rejected.
8. Mode-aware insufficiency is enforced.
9. Forbidden wording is rejected.
10. Prompt injection content does not create authority or execution language.
11. Policy `ALLOW` is not converted into authorization.
12. Hypothetical results remain hypothetical.
13. `APPROVED` remains a stored fact, not an instruction.
14. Audit evidence does not imply chain integrity.
15. Provider failures map to safe categories without leaking content.
16. Oversized responses produce no claims.
17. Empty, malformed, or partial responses produce no claims and no silent repair.
18. No persistence, logs, telemetry, or endpoints are added.
19. No auto-accepted claims exist.
20. Prompt-size fail-closed: a completed prompt exceeding 32,768 UTF-8 bytes must never invoke inference; evidence must not be truncated, omitted, subset-selected, or summarized.
21. Claim-count distinction: D40's 100-record structural parser limit and Slice 3's 25-claim generation cap must remain distinct; a structurally valid response with more than 25 generated claims must be rejected in full, with no silent truncation; `ClaimValidator` must not be treated as enforcing either count; boundary cases around 25 and 100 must be included.
22. Deadline and partial-output discard: deadline expiry during parsing or candidate validation must discard all partial claims and results; the design must rely on cooperative checks and must not claim unsupported preemptive cancellation.

---

## 10. Risks and residual limitations

### 10.1 Accepted residual risks

- Prompt construction cannot guarantee prevention of prompt injection.
- Deterministic validation does not establish semantic entailment.
- Model output may be plausible but unsupported; human review is required.
- Installed-model digest verification is not per-request cryptographic identity (D41 G-4).
- Evaluation thresholds are not adopted unless D42 explicitly decides otherwise.
- Model nondeterminism may affect reproducibility of quality benchmarks.
- Offline benchmarks may not predict real-world evidence distributions.

### 10.2 Deferred design choices

- Exact package name.
- Numeric evaluation sample sizes and strata.
- Whether model-assisted diagnostic evaluation is used.
- Future endpoint exposure.
- Any token-count policy.

### 10.3 Future implementation risks

- Implementation may accidentally conflate the 100-record parser limit with the 25-claim generation cap.
- Implementation may overstate cancellation capabilities.
- Implementation may accidentally expose raw prompt or model output.
- Implementation may introduce configurability not authorized by D42.

These risks are controlled by the acceptance criteria and by requiring a separate implementation authorization decision.

---

## 11. Relationship to D38–D41

D42 is compatible with D38, D39, D40, and D41 and preserves them unchanged.

- D38 remains the evidence foundation and trust boundary.
- D39 remains the S1 assembler design and implementation.
- D40 remains the claim contract and deterministic validator.
- D41 remains the local model provider design and implementation.
- D41 is not treated as authorization for Slice 3.
- D42, if adopted, would adopt the Slice 3 design only.

D42 creates no execution, approval, proposal-lifecycle, authentication, or provider authority. It does not modify D8, D38, D39, D40, or D41.

---

## 12. Implementation authorization status

D42 is a design-adoption proposal only. It does not authorize implementation.

A future implementation decision would be required before any Slice 3 code, tests, dependencies, configuration, endpoints, persistence, frontend, roles, or execution capability may be added. That future decision must explicitly reference D42 and satisfy the acceptance criteria in Section 9.

---

## 13. Conditions for any subsequent implementation decision

Any subsequent implementation decision must:

- reference D42 as the adopted design, if D42 is adopted;
- preserve all D38–D41 boundaries;
- satisfy Section 9 acceptance criteria;
- include security review of prompt injection and data leakage;
- include bounded-read and resource-boundary review;
- not add endpoints, persistence, frontend, roles, or execution capability without separate authorization;
- not implement model-assisted evaluation as an authority;
- not treat proposed safeguards as implemented guarantees until tests pass.

---

## 14. Governance note

This document proposes D42 for review. It does not adopt D42, does not modify `DECISIONS.md`, and does not authorize implementation. Adoption requires an explicit, dated decision following repository governance conventions.
