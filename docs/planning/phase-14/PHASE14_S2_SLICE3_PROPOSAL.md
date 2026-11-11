# Phase 14 S2 Slice 3 — Evidence-Grounded Generation and Evaluation Harness

## DRAFT — NOT ADOPTED — DESIGN ONLY

**Status:** Draft for independent review. Slice 3 is **not authorized** and **not implemented**.

**Provisional decision candidate:** D42 (identified only; not created, not adopted, no `DECISIONS.md` change).

**Baseline:** `c6ae7265339c2aeebec58db0f0b22be79dcf2e09` (PR #27 merged).

**Governing decisions (adopted and immutable):** D38, D39, D40, D41.

**No implementation is contained in this document.** No code, tests, dependencies, configuration, endpoints, persistence, roles, or UI are added by this draft.

---

## 0. Document control

| Item | Value |
|---|---|
| File | `docs/planning/phase-14/PHASE14_S2_SLICE3_PROPOSAL.md` |
| State | Untracked draft |
| Decision status | Not adopted |
| Implementation status | Not authorized |
| Authoring mode | Design/planning only |
| Next step | Independent review, then separate authorization decision |

---

## 1. Goal and non-goals

### 1.1 Goal

Design the smallest internal, read-only capability that can generate candidate `EvidenceClaim` objects from an in-process `EvidencePacket`, validate them with the existing deterministic `ClaimValidator`, and evaluate the quality and safety of that generation offline and adversarially.

The capability is an **explanation-synthesis layer over evidence**, not an authority layer. It must preserve every D38–D41 boundary and must not convert evidence, policy results, or model output into authorization, execution, or durable truth.

### 1.2 Non-goals (binding)

Slice 3 does **not** authorize or design:

- Any implementation, code, tests, dependencies, or configuration.
- Any public endpoint, frontend, human-review UI, database table, migration, or role.
- Any agent runtime, orchestration, tools, MCP, RAG, embeddings, vector database, chat memory, or multi-agent architecture.
- Any cloud-provider fallback or non-loopback model endpoint.
- Any proposal mutation, approval, authorization, or execution capability.
- Any automatic acceptance of generated claims.
- Any semantic-entailment proof or confidence score.
- Any change to D38, D39, D40, or D41.
- Any weakening of D41 transport, digest, deadline, retry, or concurrency guarantees.

---

## 2. Dependencies and verified baseline

### 2.1 Governing decisions

- **D38** — Phase 14 S0 evidence foundation: evidence categories, trust boundaries, transient packet, no agent authority.
- **D39** — Phase 14 S1 evidence packet assembler: deterministic, read-only, bounded, allowlisted, internal only. No D39 text was appended to `DECISIONS.md`; D39 is represented by its proposal and implementation.
- **D40** — Phase 14 S2 Slice 1 claim contract and deterministic validator: claim shape, 32-pair qualification/status table, mode-aware insufficiency, wording screen, packet provenance.
- **D41** — Phase 14 S2 Slice 2 local model provider: Spring AI 2.0.2, loopback Ollama, bounded synchronous inference, no retry/queue/fallback, digest verification.

### 2.2 Verified implementation facts (must be used, not re-described as design-only)

- `EvidencePacketAssembler` is an internal, deterministic, read-only `@Service` in package `com.soubhagya.systivex.evidence`.
- `EvidencePacket` is an in-process record with `packetRef` (`P<n>`), `mode`, items, limits, and omissions.
- `EvidenceClaim` is an in-process record with statement, category, `E<n>` references, qualification, limitations, packet ref, and mode.
- `ClaimValidator.validate(EvidencePacket, EvidenceClaim)` is the existing deterministic validator and returns `ClaimValidationResult`.
- `ClaimWordingScreen.isForbidden(String)` performs the deterministic wording screen.
- `EvidenceLimits` defines all S1 structural limits.
- `LocalModelAdapter.infer(String prompt)` is the only provider inference method and returns `String`.
- `LocalModelAdapter` enforces a single-flight semaphore, a 35-second overall deadline, digest verification, and maps provider failures to runtime exceptions.
- `LocalModelTransport.MAX_RESPONSE_BYTES = 65_536`.
- `LocalModelTransport` uses a direct-only `ProxySelector`, `HttpClient.Redirect.NEVER`, 5-second connect timeout, and 30-second read timeout.
- `OllamaEndpointValidator` permits only loopback HTTP IP literals with an explicit port and no path/query/fragment/userinfo.
- `ModelDigest.validate(String)` enforces `sha256:<64 lowercase hex>`.
- Spring AI 2.0.2 and Jackson 3.1.5 are already resolved by Slice 2.
- Full backend baseline after PR #27: 622/622 tests passed; focused Slice 2: 63/63.

### 2.3 Existing package boundaries

- `com.soubhagya.systivex.evidence` owns packet, claim, validation, wording, limits, and adapters. It is provider-independent and must remain so.
- `com.soubhagya.systivex.localmodel` owns the Ollama provider boundary and must not depend on evidence.
- A generation capability must live in a separate internal package that depends on both, with no reverse dependency.

---

## 3. Trust boundaries and data flow

### 3.1 Trust model

| Element | Trust level | Authority |
|---|---|---|
| System Twin, impact, risk, simulation, policy, proposal, audit sources | Trusted source data | Facts only; policy ALLOW remains advisory |
| `EvidencePacketAssembler` | Trusted internal component | Evidence selection only |
| `EvidencePacket` | Trusted in-process object | No mutation authority |
| Prompt builder | Trusted internal component | Data shaping only |
| Local model / Ollama | Untrusted | No authority; output is data |
| Generation service | Untrusted orchestrator | No authority; coordination only |
| `ClaimValidator` | Trusted deterministic component | Structure/provenance only |
| Human reviewer | Authority of record | Semantic support and approval |

### 3.2 Data flow (proposed)

```text
Trusted domain sources (same JVM)
  -> EvidencePacketAssembler (D39, existing)
  -> EvidencePacket (in-process, transient)
  -> Generation service (proposed, internal)
       -> deterministic prompt construction (packet serialized as untrusted data)
       -> LocalModelAdapter.infer(prompt) (D41, existing)
       -> bounded raw response (<= 65,536 bytes)
       -> strict parser (proposed)
       -> candidate EvidenceClaim objects (packetRef/mode injected by generator, not model)
       -> ClaimValidator.validate(packet, claim) (D40, existing)
       -> transient GeneratedClaimResult list
  -> human review (mandatory)
```

### 3.3 Prohibited data flows

- No caller-supplied serialized or deserialized packet may enter generation.
- No model output may be written to persistence, logs, telemetry, or an endpoint.
- No model output may bypass `ClaimValidator`.
- No generated claim may be treated as approved, authorized, or executable.
- No evidence item may be created or remapped by the generator.
- No packet identity may be supplied by the model.

---

## 4. A. Generation architecture

### 4.1 Recommended minimal flow

The recommended flow is a **single synchronous generation pass**:

1. Validate that the supplied `EvidencePacket` is non-null and internally consistent (constructor invariants already enforce this).
2. Build one deterministic prompt from the packet using a fixed, canonical, allowlisted serialization.
3. Call `LocalModelAdapter.infer(prompt)` exactly once.
4. Parse the bounded response with a strict parser into candidate claim structures.
5. Construct `EvidenceClaim` objects, injecting `packet.packetRef()` and `packet.mode()` from the packet (not from model output).
6. Call `ClaimValidator.validate(packet, claim)` for each candidate.
7. Return a transient result containing each candidate claim, its validation result, and a fixed failure category for infrastructure failures.

This is the smallest flow consistent with D39–D41 because it reuses all three existing boundaries and adds only prompt construction, parsing, and orchestration.

### 4.2 Why not multi-stage generation

Alternatives considered:

| Alternative | Assessment |
|---|---|
| Multi-pass generation (outline then claims) | More model calls, more latency, more nondeterminism, no additional safety benefit; rejected |
| Per-category generation passes | Multiplies inference attempts and conflicts with D41 single-flight/no-retry; rejected |
| Tool/function calling | Explicitly prohibited by D40/D41 |
| Streaming incremental claims | Complicates strict parsing and failure attribution; rejected for v1 |
| RAG/retrieval augmentation | Prohibited; evidence is already packet-scoped and allowlisted |
| Second LLM as validator | Not authoritative; see section 6 |

### 4.3 Generation result shape (proposed, not implemented)

A proposed transient result type would contain:

- Packet reference and mode (copied from packet).
- Generation status: `SUCCEEDED`, `NO_OUTPUT`, `PARSE_FAILED`, `PROVIDER_UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESPONSE_TOO_LARGE`, `BUSY`, `PROMPT_TOO_LARGE`, `INVALID_PACKET`.
- A list of proposed `GeneratedClaimResult` entries, each containing:
  - candidate `EvidenceClaim`
  - `ClaimValidationResult`
  - no raw model text
- Safe diagnostic counts only (claims parsed, claims valid, claims invalid, failure category).

No raw prompt, raw model output, or packet content may be included in the runtime result or in exceptions. Offline evaluation raw-output access is governed separately by §11.3.

---

## 5. B. Evidence and claim provenance

### 5.1 Packet acceptance rules

The generation component must:

- Accept only a live in-process `EvidencePacket` object.
- Never accept bytes, JSON, strings, maps, or caller-constructed DTOs that represent a packet.
- Never deserialize a packet from any source.
- Treat the packet reference as immutable and copy it into every candidate claim.
- Treat packet mode as immutable and copy it into every candidate claim.
- Use only `packet.items()` and `packet.omissions()` as evidence context.
- Use only existing packet-local `E<n>` references in candidate claims.

### 5.2 Reference and category rules

- Candidate claims may reference only `E<n>` values present in the same packet.
- `ClaimValidator` remains the sole authority for reference existence, category consistency, qualification, limitation inheritance, insufficiency, and wording.
- The generator must not drop unknown references to make a claim pass; unknown references must cause validation failure.
- The generator must not remap a reference to a different evidence item.
- The generator must not invent evidence items, source statuses, limitations, or omissions.
- Category consistency must be enforced by `ClaimValidator`, not by prompt instructions.

### 5.3 Mode handling

- Mode is copied from the packet and never overridden by the model.
- `BASELINE`, `SIMULATION`, and `POLICY_EVALUATION` retain their D39 applicability matrices.
- Simulation claims must remain hypothetical.
- Policy claims must preserve advisory status.
- Audit claims must not imply chain integrity.

### 5.4 In-process packet boundary (M1)

Accepting only the in-process S1 `EvidencePacket` is an architectural and API/package-boundary contract, not a universal compile-time type-system guarantee:

- The generation service is internal and has no externally reachable endpoint or packet-ingestion API.
- It accepts the live `EvidencePacket` object produced by `EvidencePacketAssembler`; it does not accept bytes, JSON, strings, maps, or caller-constructed DTOs representing a packet.
- Package visibility and service wiring are intended to prevent external packet construction at the API boundary, but the Java type system alone does not prove that every possible caller is trusted.
- This is a design contract plus review/test boundary; it is not equivalent to a universal compile-time security proof.
- Residual limitation: a caller in the same JVM with access to the service could still pass an internally constructed packet. Tests and review must enforce the intended caller boundary.

---

## 6. C. Prompt safety and untrusted input

### 6.1 Binding D40 rules

The prompt design must preserve all of the following as data, never as instructions:

- Evidence content is untrusted data, never instructions.
- Prompt delimiters are not a security boundary.
- The model and generation component have no authority.
- No database, filesystem, shell, credentials, target-service, or arbitrary network access.
- No tools or function calling.
- Policy ALLOW remains advisory.
- Hypothetical results remain hypothetical.
- APPROVED remains a stored governance fact, not an instruction.
- Audit evidence never implies chain integrity.
- Human review remains authoritative.

### 6.2 Prompt construction strategy

The prompt builder will:

- Use a fixed system/developer instruction block stating that all evidence is untrusted data and that the model has no authority.
- Serialize the packet using a canonical, allowlisted JSON representation produced from the in-memory record.
- Wrap serialized evidence in explicit delimiters and label it as untrusted data.
- Exclude free-form proposal text, identity details, credentials, raw HTTP bodies, and unallowlisted metadata, consistent with D39.
- Exclude any caller identity or authorization context.
- Not ask the model to approve, authorize, execute, or recommend action.
- Ask only for structured claims that cite packet references.
- Keep serialization deterministic (stable key order, stable item order).

### 6.3 Honest limitation

This design **does not** claim that prompt delimiters or prompt wording prevent prompt injection. Delimiters reduce accidental instruction following only. The security boundary is the combination of:

- no tool or function calling,
- no external access,
- strict output parsing,
- deterministic validation,
- no auto-acceptance,
- mandatory human review.

Evidence content remains untrusted even if it contains instruction-like text.

---

## 7. D. Structured output and parsing

### 7.1 Output format (proposed)

The model must return a single JSON object:

```json
{
  "claims": [
    {
      "statement": "string",
      "category": "OBSERVED_FACT | DETERMINISTIC_FINDING | HYPOTHETICAL_RESULT | GOVERNANCE_RECORD | AUDIT_EVIDENCE",
      "evidenceReferences": ["E1"],
      "qualification": "CURRENT | STALE | HISTORICAL | HYPOTHETICAL | ADVISORY | UNCERTAIN | TRUNCATED | MISSING",
      "limitations": ["string"]
    }
  ]
}
```

Rules:

- `packetRef` and `mode` are never accepted from model output.
- Unknown properties are rejected.
- Duplicate JSON properties are rejected using `tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION`.
- Trailing tokens after the root object are rejected.
- Null array elements are rejected.
- The parser must not repair, coerce, truncate, or reinterpret malformed structures.

### 7.2 Parser constraints (inherited and proposed)

| Constraint | Source | Value / rule |
|---|---|---|
| Response byte cap | Inherited D41 (`LocalModelTransport`) | 65,536 bytes maximum; no byte 65,537 reaches caller |
| Connect timeout | Inherited D41 | 5 seconds |
| Read timeout | Inherited D41 | 30 seconds |
| Overall deadline | Inherited D41 | 35 seconds monotonic |
| Direct proxy / redirects | Inherited D41 | `Proxy.NO_PROXY`, `Redirect.NEVER` |
| Duplicate detection | Proposed parser | `tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION` (Jackson 3.1.5) |
| Unknown properties | Proposed parser | `tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` |
| D40 structural parser claim limit | Existing D40 contract | 100 claim records maximum; unchanged by this proposal |
| Generation candidate claim cap | Proposed generation boundary | 25 claim records maximum; distinct from the D40 parser structural limit (see §7.4) |
| Max nesting depth | Proposed parser | 8 (candidate) |
| Max string length | Proposed parser + D40 | 1,000 characters (`EvidenceLimits.MAX_TEXT_LENGTH`) |
| Max JSON name length | Proposed parser | 64 characters (candidate) |
| Max document length | Proposed parser | 65,536 bytes (inherited cap) |
| Max claims per generation | Proposed generation limit | 25 claim records (candidate; see Sections 7.4 and 8.2) |
| References per claim | Existing D40 (`EvidenceClaim`) | 1–20 |
| Limitations per claim | Existing D40 (`EvidenceClaim`) | 0–100, each 1–1,000 characters |

### 7.3 Missing, malformed, partial, and oversized output

| Condition | Required handling |
|---|---|
| Empty output | Generation status `NO_OUTPUT`; no claims; no exception to caller |
| Malformed JSON | `PARSE_FAILED`; no claims |
| Partial JSON | `PARSE_FAILED`; no claims |
| Oversized response | Adapter throws `AnalysisResponseTooLargeException`; mapped to `RESPONSE_TOO_LARGE`; no claims |
| Deadline exceeded | Adapter throws `AnalysisDeadlineExceededException`; mapped to `DEADLINE_EXCEEDED`; no claims |
| Provider unavailable | Adapter throws `AnalysisProviderUnavailableException`; mapped to `PROVIDER_UNAVAILABLE`; no claims |
| Busy | Adapter throws `AnalysisBusyException`; mapped to `BUSY`; no claims |
| Too many claims | `PARSE_FAILED` (structural); no partial acceptance |
| Fabricated/unknown reference | Claim marked invalid by `ClaimValidator`; never repaired |
| Invalid qualification/status pair | Claim marked invalid by `ClaimValidator` |
| Missing limitations | Claim marked invalid by `ClaimValidator` |

No silent repair is permitted. If a candidate cannot be validated as parsed, it is reported as invalid or the whole output is rejected according to the failure category; the generator never edits claim content to make it pass.

### 7.4 Claim-count boundary distinction (H1)

D40's existing structural parser contract and Slice 3's proposed generation boundary are separate:

1. D40 already authorizes a structural parser limit of 100 claim records. This is part of the existing Slice 2/D40 provider-parser contract and remains unchanged by this proposal.
2. Slice 3 proposes a generation-specific candidate limit of 25 claims. This is a Slice 3 generation boundary, not a D40 contract and not a parser structural limit.
3. The two limits govern different boundaries and must not be conflated in implementation or documentation.
4. If the model returns more than 25 claims, reject the entire generated response with `PARSE_FAILED` (or an equivalent generation-specific structural failure category). Never silently truncate to 25.
5. If the model returns more than 100 claims, that is first a violation of the existing D40 structural parser limit and must be rejected before any generation-specific claim-count evaluation.
6. Malformed JSON, structural-limit violations, and generation-specific claim-count violations must remain distinguishable in failure reporting, but all result in no accepted candidate claims.
7. `ClaimValidator.validate(...)` is invoked only after a response has passed both the D40 structural limits and the proposed Slice 3 generation claim cap. It does not enforce either count.

### 7.5 Token-limit deferral (M3)

The proposed byte limits are not token limits:

- The 32,768-byte prompt cap is a UTF-8 byte cap on the completed deterministic prompt string. It is not a token limit and does not estimate or constrain model tokens.
- The 65,536-byte response cap is a response-body byte cap. It is not a token limit and does not estimate or constrain model tokens.
- No token-count guarantee, tokenizer, token estimate, token quota, or model-context-window guarantee is provided by these byte limits.
- Token-limit policy remains deferred under D40 section I and requires a separate explicit decision/adoption before it can become an enforced contract.

### 7.6 Prompt measurement and fail-closed behavior (H2)

The 32,768-byte cap applies only to the completed deterministic prompt string passed to `LocalModelAdapter.infer(String)`:

- What is measured: the complete prompt string after deterministic construction.
- Encoding: UTF-8 bytes.
- Measurement point: immediately before inference.
- Included content: fixed instructions, claim schema, evidence references, evidence content, delimiters, and any other text in the prompt.
- Excluded from this measurement: transport JSON serialization overhead, HTTP request framing, headers, and any provider-side request envelope. The prompt-string cap is not a complete HTTP request-body cap.
- The 262,144-byte Evidence Packet cap from S1 remains a separate packet-assembly boundary.

Fail-closed behavior:

- If the completed prompt exceeds 32,768 UTF-8 bytes, do not call the model.
- Do not truncate evidence.
- Do not silently omit evidence.
- Do not select a smaller evidence subset.
- Do not summarize evidence to force it under the limit.
- Return `PROMPT_TOO_LARGE` (or an equivalent safe failure category) with no candidate claims.
- A packet may validly pass S1 assembly and still be ineligible for Slice 3 generation because its completed prompt exceeds the separate prompt cap.

---

## 8. G. Resource and failure boundaries

### 8.1 Inherited D41 limits (unchanged)

| Limit | Value | Source |
|---|---|---|
| Provider | Local Ollama only | D41 |
| Response cap | 65,536 bytes | D41 |
| Connect timeout | 5 seconds | D41 |
| Read timeout | 30 seconds | D41 |
| Overall deadline | 35 seconds monotonic | D41 |
| Inference attempts | Exactly one | D41 |
| Retry/backoff | None | D41 |
| Queue | None | D41 |
| Cloud fallback | None | D41 |
| Concurrency | Single-flight semaphore | D41 |
| Proxy | Direct only | D41 |
| Redirects | Disabled | D41 |
| Model identity | Installed-model digest via `/api/tags` + `/api/show` | D41 (G-4 limitation preserved) |
| Endpoint | Loopback HTTP IP literal with explicit port | D41 |
| Pull strategy | `PullModelStrategy.NEVER` | D41 |

### 8.2 Proposed generation-level limits (candidate, not adopted)

| Limit | Candidate value | Rationale |
|---|---|---|
| Max claims per generation | 25 | Keeps output reviewable and below the 65,536-byte cap; adjustable only by decision |
| Max prompt bytes | 32,768 | Prevents oversized untrusted input and model context overflow; generation fails rather than truncates |
| Max generation invocations per request | 1 | Preserves D41 single-flight and no-retry semantics |
| Raw output retention | None outside in-memory evaluation | Privacy and leakage control; see Section 11.3 |
| Candidate claim cap per packet | 25 | Same as above; no batching |

These are proposed limits with rationale. They are not adopted and require explicit decision if changed.

### 8.3 Failure categories

Generation failures must use a fixed, safe category set:

- `INVALID_PACKET`
- `PROMPT_TOO_LARGE`
- `PROVIDER_UNAVAILABLE`
- `DEADLINE_EXCEEDED`
- `RESPONSE_TOO_LARGE`
- `BUSY`
- `PARSE_FAILED`
- `NO_OUTPUT`
- `VALIDATION_REJECTED` (claim-level, not generation-level)

No exception message may contain prompt content, packet content, model output, credentials, or endpoint details beyond the fixed category.

### 8.4 Proposed orchestration deadline and cancellation (H3)

Slice 3 may use a PROPOSED total orchestration budget of 45 seconds. This is not an adopted or implemented guarantee and requires D42 adoption:

- maximum 5 seconds for deterministic prompt construction;
- maximum 35 seconds for the existing D41 inference call, preserving D41's 35-second inference deadline unchanged;
- maximum 5 seconds for parsing, validation, and result construction.

The 35-second D41 inference deadline remains the binding inference limit. The proposed 45-second Slice 3 budget does not weaken, replace, or extend D41's transport, retry, proxy, redirect, response-cap, or digest guarantees.

Cooperative monotonic deadline checks must occur:

- at Slice 3 orchestration entry;
- during prompt construction;
- immediately before inference;
- immediately after inference;
- while parsing tokens or records;
- during candidate validation;
- before returning the result.

If the deadline expires, stop subsequent processing, discard all partial candidate claims, and return `DEADLINE_EXCEEDED`.

Cancellation honesty:

- `LocalModelAdapter.infer(String)` currently owns and clears its own 35-second D41 deadline inside the method. Slice 3 must not assume that deadline remains active after `infer()` returns.
- Slice 3 must maintain its own orchestration deadline and check it cooperatively between phases.
- Synchronous parsing cannot be arbitrarily preempted. If a parser call blocks, cancellation can only take effect at the next cooperative check or between tokens/records.
- Prefer a bounded streaming or incremental token/record processing design with deadline checks between tokens or records, rather than claiming preemptive cancellation of a blocking parser.
- What is cancellable: phase transitions, per-record/per-claim processing, and returning before starting the next candidate.
- What is not guaranteed cancellable: a single blocking parser call that does not yield control.

---

## 9. E. Deterministic validation vs semantic evaluation

### 9.1 Critical distinction

`ClaimValidator` proves deterministic structure and provenance only. It proves:

- packet identity and mode,
- reference existence,
- category consistency,
- qualification propagation,
- limitation inheritance,
- mode-aware insufficiency,
- forbidden wording patterns.

It does **not** prove that the cited evidence supports the statement. Semantic entailment remains for human review, the authority of record.

### 9.2 Evaluation layers

| Layer | Purpose | Authority |
|---|---|---|
| Deterministic structural/provenance evaluation | Re-run `ClaimValidator` on generated claims | Deterministic, authoritative for structure only |
| Offline benchmark against human-labelled fixtures | Measure quality and failure rates | Evaluation only; no auto-acceptance |
| Optional model-assisted evaluation | Diagnostic comparison or critique | Diagnostic only; never authoritative |

### 9.3 Second LLM judge recommendation

A second LLM judge is **not necessary** for Slice 3 and is **not recommended as an authority**. If used at all, it must be:

- clearly labelled diagnostic,
- run offline or in a separate evaluation harness,
- unable to accept or reject claims for production,
- unable to override `ClaimValidator` or human review.

No model's approval of another model is authoritative.

### 9.4 No auto-accepted claims

No generated claim may be accepted automatically. Human review remains mandatory for semantic support, even when `ClaimValidator` returns valid.

---

## 10. F. Evaluation methodology

### 10.1 Fixture structure (proposed)

Each evaluation fixture should contain:

- A fully constructed `EvidencePacket` produced by the real assembler (not hand-written JSON where avoidable).
- A generation output or a deterministic stub output.
- Human-labelled expected outcomes:
  - expected claim count range or exact set,
  - expected validator accept/reject decisions,
  - expected failure categories,
  - expected reference sets,
  - expected limitation preservation.
- Metadata: mode, category coverage, evidence availability scenario, adversarial scenario tag.

### 10.2 Coverage requirements

Fixtures must cover:

- All five evidence categories: `OBSERVED_FACT`, `DETERMINISTIC_FINDING`, `HYPOTHETICAL_RESULT`, `GOVERNANCE_RECORD`, `AUDIT_EVIDENCE`.
- All three modes: `BASELINE`, `SIMULATION`, `POLICY_EVALUATION`.
- Available, unavailable, stale, truncated, hypothetical, historical, and advisory evidence cases.
- Missing, fabricated, and foreign evidence references.
- Limitation inheritance failures.
- Unsupported causal or certainty language.
- Invented numbers, dates, names, and facts.
- Prompt-injection and adversarial evidence content.
- Policy ALLOW misuse.
- Hypothetical-as-executed confusion.
- Audit-list-as-chain-integrity confusion.

### 10.3 Measurable dimensions

| Dimension | Definition |
|---|---|
| Parse success | Fraction of outputs that parse into the required JSON shape |
| Structural validity | Fraction of candidate claims accepted by `ClaimValidator` |
| Validator acceptance/rejection | Counts by `ClaimFailure` category |
| Reference precision | Fraction of cited references that exist in the packet |
| Limitation preservation | Fraction of source limitations correctly inherited |
| Unsupported-claim rate | Fraction of human-labelled unsupported claims incorrectly accepted |
| Human-review agreement | Agreement between validator-valid claims and human-labelled support |

No confidence scores or calibrated probability claims may be introduced.

### 10.4 Candidate sample sizes and strata (proposed, not adopted)

D40 explicitly deferred evaluation sample sizes and strata. Candidate options:

| Tier | Scope | Candidate size | Purpose |
|---|---|---|---|
| Tier A — mandatory safety | Adversarial cases | 45 (15 per mode) | Prompt injection, fabricated refs, category laundering, policy misuse, audit-chain confusion |
| Tier B — quality benchmark | Human-labelled packets | 60 (20 per mode) | Unsupported claims, limitation inheritance, reference precision |
| Tier C — category coverage | Category-focused packets | At least 10 cases per evidence category | Category consistency and mode applicability |

Stratification should be by mode and evidence availability (`AVAILABLE`, `UNAVAILABLE`, `TRUNCATED`, `OMITTED`). These are proposed starting points only and require explicit adoption.

---

## 11. H. Data lifecycle

### 11.1 Transient-only lifecycle

The following must remain transient and in-memory only:

- `EvidencePacket`
- prompt string
- raw model output
- candidate claims
- validation results
- generation results
- evaluation fixtures loaded at test time

No persistence, logs containing prompt or packet content, conversation history, or telemetry leakage is permitted.

### 11.2 Safe diagnostics

Permitted diagnostics are limited to:

- fixed failure categories,
- counts,
- pass/fail booleans,
- latency and size measurements that do not include content.

Prohibited diagnostics include:

- prompt text,
- packet JSON,
- model output,
- claim statements in logs,
- evidence content in telemetry,
- raw exception messages containing any of the above.

### 11.3 Offline evaluation raw-output access (resolved)

The runtime generation path and the offline evaluation harness have different access requirements:

Runtime path:

- No raw prompt or raw model output is returned in `GenerationResult`, exceptions, logs, telemetry, or endpoints.
- Candidate claims and validation results only.

Offline evaluation harness:

- May inspect raw model output transiently in memory through a test/evaluation seam for diagnostics and metric calculation.
- May classify parse failures and retain enough information to calculate parse success, structural validity, and failure categories.
- Must not write raw prompt or raw model output to durable storage, logs, telemetry, or API responses.
- Must not expose raw output to users or grant additional authority to the evaluator or model.
- Must not use a model-as-judge to approve claims.
- Human review remains the final authority; the evaluator measures and classifies, it cannot accept claims.

This resolves open question 7 as a design direction, subject to D42 adoption. It does not authorize implementation.

---

## 12. I. Interfaces and module boundaries

### 12.1 Existing APIs (unchanged)

- `EvidencePacketAssembler` (internal `@Service`)
- `EvidencePacket`
- `EvidenceClaim`
- `ClaimValidator`
- `ClaimWordingScreen`
- `EvidenceLimits`
- `LocalModelAdapter.infer(String)`

### 12.2 Proposed APIs (not implemented)

These are recommendations only; no classes or placeholders are created by this draft:

- An internal generation service in a new package, for example `com.soubhagya.systivex.analysis` or `com.soubhagya.systivex.evidence.generation`, depending on review. The package must depend on `evidence` and `localmodel`; neither may depend on it.
- A proposed `GeneratedClaimResult` transient record containing candidate claim, validation result, and no raw text.
- A proposed `GenerationResult` transient record containing status, safe counts, and candidate results.
- A proposed `GenerationOptions` value only if a decision explicitly requires configurable limits; otherwise fixed constants are preferred.

### 12.3 Explicitly absent from Slice 3

- No public endpoint.
- No frontend.
- No human-review UI.
- No database table or migration.
- No new role.
- No change to `EvidencePacketAssembler` public behavior.
- No change to `ClaimValidator`.
- No change to `LocalModelAdapter`.

---

## 13. J. Acceptance criteria

### 13.1 Mandatory safety tests (CI-gating)

1. Generation rejects null or inconsistent packets with `INVALID_PACKET`.
2. Generation never accepts serialized or deserialized packet input.
3. Candidate claims always carry packet ref and mode from the packet.
4. Unknown/fabricated/foreign references are rejected by `ClaimValidator`.
5. Category mismatch is rejected.
6. Qualification overstatement is rejected.
7. Missing limitations are rejected.
8. Mode-aware insufficiency is enforced.
9. Forbidden wording is rejected.
10. Prompt injection content in evidence does not create authority or execution language.
11. Policy ALLOW is not converted into authorization.
12. Hypothetical results remain hypothetical.
13. APPROVED remains a stored fact, not an instruction.
14. Audit evidence does not imply chain integrity.
15. Provider failures map to safe categories without leaking content.
16. Oversized responses produce no claims.
17. Empty/malformed/partial responses produce no claims and no silent repair.
18. No persistence, logs, telemetry, or endpoints are added.
19. No auto-accepted claims exist.
20. Prompt-size fail-closed: construct a completed deterministic prompt exceeding 32,768 UTF-8 bytes; verify that inference is never invoked; verify the defined `PROMPT_TOO_LARGE` failure; verify that evidence is not truncated, omitted, subset-selected, or summarized to fit.
21. Claim-count distinction: verify D40's 100-record structural parser limit remains distinct from Slice 3's proposed 25-generated-claim limit; verify a structurally valid response containing more than 25 generated claims is rejected in full; verify no silent truncation occurs; verify `ClaimValidator` is not incorrectly treated as enforcing either count limit; include boundary cases around 25 and 100 so the two limits cannot be accidentally conflated.
22. Deadline and partial-output discard: simulate deadline expiry during parsing or candidate validation after inference has returned; verify no partial candidate claims or partial result are returned; verify the defined deadline-exceeded failure; verify the design relies on cooperative checks and does not claim unsupported preemptive cancellation.

### 13.2 Adapter and transport tests (already satisfied by D41)

Reuse all D41 tests without weakening them:

- response cap exact/overflow,
- chunked unknown length,
- missing/misleading Content-Length,
- declared-over-limit,
- `transferTo()`,
- single attempt,
- proxy bypass,
- redirect rejection,
- digest verification,
- deadline behavior.

### 13.3 Offline evaluation tests (benchmark, not model-quality gate unless thresholds adopted)

1. Fixtures parse and run without a live Ollama instance (stub adapter).
2. Coverage across all five categories and three modes.
3. Reference precision measured and reported.
4. Limitation preservation measured and reported.
5. Unsupported-claim rate measured and reported.
6. Human-review agreement measured and reported.
7. No confidence scores are produced.

### 13.4 Objectively verifiable completion conditions

Slice 3 implementation, if later authorized, is complete only when:

- all mandatory safety tests pass,
- all D41 tests still pass,
- `git diff --check` is clean,
- full backend suite passes,
- no endpoint, persistence, frontend, role, or dependency is added beyond the authorized scope,
- evaluation results are reported without auto-acceptance.

---

## 14. Risks and residual limitations

- Prompt construction cannot guarantee prevention of prompt injection.
- Deterministic validation does not prove semantic entailment.
- Model output may be plausible but unsupported; human review is required.
- Installed-model digest verification is not per-request cryptographic identity (D41 G-4).
- Evaluation sample sizes and strata are not yet adopted.
- Model nondeterminism may affect reproducibility of quality benchmarks.
- Context-window limits may require fail-fast rather than truncation.
- Offline benchmarks may not predict real-world evidence distributions.

---

## 15. Alternatives considered

| Alternative | Recommendation |
|---|---|
| Multi-pass generation | Rejected; more calls and nondeterminism without safety benefit |
| Per-category generation | Rejected; multiplies inference attempts |
| Tool/function calling | Prohibited by D40/D41 |
| Streaming claims | Rejected for v1; complicates strict parsing |
| RAG/retrieval | Prohibited; evidence already packet-scoped |
| Second LLM judge as authority | Rejected; diagnostic only if used |
| Auto-acceptance of valid claims | Prohibited |
| Storing raw outputs for debugging | Rejected; privacy risk |
| Changing `LocalModelAdapter` to accept options | Rejected for Slice 3 unless separately authorized |

---

## 16. Implementation sequencing (if separately authorized later)

1. Independent review of this draft and adoption decision (provisional D42 if adopted).
2. Add internal generation package and transient result types.
3. Implement deterministic prompt builder and strict parser.
4. Wire candidate construction to `ClaimValidator`.
5. Add mandatory safety and adversarial tests.
6. Add offline evaluation fixtures and benchmarks.
7. Optional manual verification against a loopback Ollama instance.
8. Separate review of any endpoint, UI, persistence, or role proposal.

No step may be performed under this draft.

---

## 17. Open questions requiring an explicit decision

1. Should the generation package be `analysis` or `evidence.generation`?
2. Are fixed constants sufficient, or is a `GenerationOptions` value needed?
3. What is the adopted maximum claims per generation (candidate 25)?
4. What is the adopted maximum prompt bytes (candidate 32,768)?
5. What are the adopted evaluation sample sizes and strata?
6. Is any model-assisted diagnostic evaluation desired at all?
7. Resolved by this revision: raw model output may be inspected transiently in memory by the offline evaluation harness only, under the constraints in §11.3. Runtime paths still return no raw output.
8. Should generation be exposed only to internal callers, or is a future endpoint desired (separate decision)?

These must not be silently decided by implementation.

---

## 18. Explicit statement of non-authorization

This document is a design draft only. It does not adopt D42 or any other decision. It does not authorize implementation, dependencies, configuration, endpoints, persistence, roles, frontend, agent runtime, tools, RAG, embeddings, vector databases, chat memory, cloud fallback, proposal mutation, approval, authorization, or execution. Slice 3 remains gated and not implemented. D38–D41 remain unchanged.

---

## Appendix A — Existing facts relied upon

### A.1 Evidence enums and constants

- `EvidenceMode`: `BASELINE`, `SIMULATION`, `POLICY_EVALUATION`
- `EvidenceCategory`: `OBSERVED_FACT`, `DETERMINISTIC_FINDING`, `HYPOTHETICAL_RESULT`, `GOVERNANCE_RECORD`, `AUDIT_EVIDENCE`
- `EvidenceSourceStatus`: `AVAILABLE`, `UNAVAILABLE`, `OMITTED`, `TRUNCATED`
- `EvidenceQualification`: `MISSING`, `TRUNCATED`, `UNCERTAIN`, `STALE`, `HISTORICAL`, `HYPOTHETICAL`, `ADVISORY`, `CURRENT`
- `MAX_REFERENCES_PER_CLAIM = 20`
- `MAX_TEXT_LENGTH = 1000`
- `MAX_LIMITATIONS = 100`
- `MAX_ITEMS_PER_SOURCE_FAMILY = 100`
- `MAX_TOTAL_ITEMS = 1000`
- `MAX_SERIALIZED_BYTES = 262144`
- `MAX_AUDIT_EVENTS = 100`

### A.2 Local model constants

- `MAX_RESPONSE_BYTES = 65536`
- `CONNECT_TIMEOUT = 5s`
- `READ_TIMEOUT = 30s`
- `OVERALL_OPERATION_DEADLINE = 35s`
- `INFERENCE_PERMIT` is a single fair semaphore
- `ModelDigest` regex: `sha256:[0-9a-f]{64}`

### A.3 Existing failure classes

- `LocalModelAdapter.AnalysisBusyException`
- `LocalModelAdapter.AnalysisProviderUnavailableException`
- `LocalModelAdapter.AnalysisResponseTooLargeException`
- `LocalModelAdapter.AnalysisDeadlineExceededException`
- `LocalModelAdapter.ModelIdentityMismatchException`
- `OperationDeadlineExceededException`
- `LocalModelTransport.ResponseTooLargeException`
