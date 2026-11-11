# Phase 14 D43 Implementation Authorization Proposal - Phase 14 S2 Slice 3

## PROPOSED - NOT ADOPTED - NO IMPLEMENTATION AUTHORIZATION

This file is a governance proposal only. It is not an entry in canonical `DECISIONS.md`, does not adopt D43, and does not authorize implementation of Slice 3.

**Authoritative adopted design:** D42 (DECIDED AND ADOPTED 2026-11-11)
**Authoritative design source:** `docs/planning/phase-14/PHASE14_S2_SLICE3_PROPOSAL.md`
**Source design SHA-256:** `DE5189CF7E15D23921B590759B9F26E9E5204BD0197B00CF225A8CBF1A0A6BB3`
**Source design length:** 813 lines
**Governance proposal source:** `docs/planning/phase-14/PHASE14_D42_ADOPTION_PROPOSAL.md`
**Baseline commit:** `c6ae7265339c2aeebec58db0f0b22be79dcf2e09`
**Governing decisions:** D38, D39, D40, D41, D42 (adopted and immutable)

---

## 1. Decision identifier

**Proposed decision:** D43 - Phase 14 S2 Slice 3: Evidence-Grounded Generation and Evaluation Harness (implementation authorization)

**Status:** PROPOSED - NOT ADOPTED - NO IMPLEMENTATION AUTHORIZATION

**Purpose:** Propose authorization to implement the D42-adopted Phase 14 S2 Slice 3 design, and only that design, in a future implementation slice.

D43 is a proposal. It is not an adopted decision. No code, tests, dependencies, configuration, endpoints, persistence, frontend, roles, or execution capability may be added until D43 is formally adopted and the user explicitly authorizes implementation.

---

## 2. Decision statement

If adopted, D43 would authorize implementation of Phase 14 S2 Slice 3 strictly within the D42-adopted design and the exact scope defined in this proposal.

D43 would not:

- amend D38, D39, D40, D41, or D42;
- authorize any endpoint, persistence, frontend, role, or execution capability;
- authorize a second LLM, tool calling, RAG, vector database, agent runtime, or external provider;
- authorize implementation of any future slice;
- treat D42 as implementation authorization;
- convert proposed safeguards into already-implemented guarantees.

Design adoption (D42), implementation authorization (D43 if adopted), and implementation completion are three distinct states.

---

## 3. Context and rationale

D42 adopted the Slice 3 design on 2026-11-11. The design is the smallest internal, read-only capability that can generate candidate `EvidenceClaim` objects from an in-process `EvidencePacket`, validate them with the existing deterministic `ClaimValidator`, and evaluate generation quality offline and adversarially.

The implementation must reuse the existing boundaries rather than create new provider or validation mechanisms:

- `EvidencePacketAssembler` remains the sole packet producer.
- `LocalModelAdapter.infer(String)` remains the sole provider inference method.
- `ClaimValidator.validate(EvidencePacket, EvidenceClaim)` remains the sole deterministic claim validator.
- `ClaimWordingScreen.isForbidden(String)` remains the deterministic wording screen.

Slice 3 adds only internal prompt construction, strict parsing, generation orchestration, a transient result, and an offline evaluation harness.

---

## 4. Authoritative sources

D43 must be read together with:

1. `DECISIONS.md`, especially D8, D38, D39, D40, D41, and D42.
2. `docs/planning/phase-14/PHASE14_S2_SLICE3_PROPOSAL.md` (SHA-256 `DE5189CF7E15D23921B590759B9F26E9E5204BD0197B00CF225A8CBF1A0A6BB3`).
3. `docs/planning/phase-14/PHASE14_D42_ADOPTION_PROPOSAL.md`.
4. The actual repository implementation and tests.

If a conflict arises, D42 and the approved Slice 3 design proposal take precedence over this implementation authorization proposal. D43 may not expand scope.

---

## 5. Current implementation baseline

The repository already contains the required S1 and S2 implementations.

### 5.1 Evidence package

Package: `com.soubhagya.systivex.evidence`

Relevant existing APIs:

- `EvidencePacketAssembler` is an internal, deterministic, read-only `@Service`.
- `EvidencePacket` is an in-process record with `packetRef`, `mode`, `items`, `limits`, and `omissions`.
- `EvidenceClaim` is an in-process record with statement, category, `E<n>` references, qualification, limitations, packet reference, and mode.
- `EvidenceClaim.MAX_REFERENCES_PER_CLAIM = 20`.
- `ClaimValidator.validate(EvidencePacket, EvidenceClaim)` returns `ClaimValidationResult`.
- `ClaimWordingScreen.isForbidden(String)` performs the deterministic wording screen.
- `EvidenceLimits` defines all S1 structural limits, including `MAX_TEXT_LENGTH = 1000` and `MAX_LIMITATIONS = 100`.

These APIs are provider-independent and must remain so. The generation package must depend on `evidence`, not the reverse.

### 5.2 Local model package

Package: `com.soubhagya.systivex.localmodel`

Relevant existing APIs:

- `LocalModelAdapter.infer(String prompt)` is the only provider inference method and returns `String`.
- `LocalModelAdapter` enforces a single-flight semaphore, digest verification, and a 35-second overall deadline.
- `LocalModelAdapter` maps provider failures to `AnalysisBusyException`, `AnalysisProviderUnavailableException`, `AnalysisResponseTooLargeException`, `AnalysisDeadlineExceededException`, and `ModelIdentityMismatchException`.
- `LocalModelTransport.MAX_RESPONSE_BYTES = 65_536`.
- `LocalModelTransport` uses a direct-only `ProxySelector`, `HttpClient.Redirect.NEVER`, 5-second connect timeout, and 30-second read timeout.
- `OllamaEndpointValidator` permits only loopback HTTP IP literals with an explicit port and no path, query, fragment, or userinfo.
- `ModelDigest.validate(String)` enforces `sha256:<64 lowercase hex>`.
- `Deadline` and `MonotonicClock` are available for cooperative monotonic deadline checks.
- `LocalModelConfiguration` is conditional on `systivex.local.enabled=true`; the provider bean is absent by default.

### 5.3 Jackson

Jackson 3.1.5 is already resolved through Spring Boot 4.1.1. The repository already uses `tools.jackson.databind.ObjectMapper` in `EvidencePacketAssembler`.

Relevant verified APIs:

- `tools.jackson.core.StreamReadConstraints.Builder`
- `tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION`
- `tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`

### 5.4 Test conventions

Existing tests use JUnit 5 and AssertJ. Evidence and local-model tests are package-level and use deterministic fixtures. Full backend baseline after PR #27 is 622/622 tests passing; focused Slice 2 is 63/63 passing.

---

## 6. Proposed implementation scope

D43 authorizes only the following internal backend implementation if adopted and explicitly authorized.

### 6.1 Deterministic prompt construction

Implement an internal prompt builder that:

- accepts only a live in-process `EvidencePacket`;
- serializes the packet using a fixed, canonical, allowlisted representation;
- treats all evidence content as untrusted data, never instructions;
- preserves packet-local references, categories, qualifications, statuses, and limitations;
- excludes free-form proposal text, identity details, credentials, raw HTTP bodies, and unallowlisted metadata;
- excludes caller identity and authorization context;
- does not ask the model to approve, authorize, execute, or recommend action;
- keeps serialization deterministic and stable;
- measures the complete UTF-8 prompt immediately before inference;
- fails closed with `PROMPT_TOO_LARGE` when the completed prompt exceeds 32,768 UTF-8 bytes;
- never truncates, omits, subset-selects, or summarizes evidence to fit.

Prompt construction may reduce accidental instruction following. It does not prevent prompt injection.

### 6.2 Generation orchestration

Implement one synchronous, transient pipeline:

```text
EvidencePacket
  -> deterministic prompt construction
  -> existing LocalModelAdapter
  -> strict bounded parser
  -> candidate EvidenceClaims
  -> existing ClaimValidator
  -> transient generation result
```

Requirements:

- in-process `EvidencePacket` input only;
- no external packet ingestion;
- no endpoint;
- no persistence;
- no background job or queue;
- bounded synchronous execution;
- fixed internal resource constants;
- exactly one `LocalModelAdapter.infer(String)` call per generation;
- no retry, backoff, queue, or cloud fallback.

### 6.3 Strict output parser

Implement the reviewed Jackson 3.1.5 parsing design.

Preserve:

- `StreamReadFeature.STRICT_DUPLICATE_DETECTION`;
- `FAIL_ON_UNKNOWN_PROPERTIES`;
- bounded structural parsing;
- rejection of malformed JSON;
- rejection of missing, unknown, or null fields;
- maximum nesting depth of 8;
- maximum document length of 65,536 bytes;
- maximum string length of 1,000 characters;
- maximum property-name length of 64 characters;
- any numeric-length bound only if separately adopted; D40's deferred token policy remains deferred.
- safe failure categories;- no raw prompt or output logging.

Keep D40's 100-record structural parser limit distinct from D42's 25-generated-claim cap:

1. A response exceeding 100 claims is a violation of the existing D40 structural parser limit.
2. A response with 100 or fewer claims but more than 25 generated claims is a violation of the D42 generation cap.
3. Exceeding 25 generated claims rejects the entire response.
4. No silent truncation is permitted.
5. `ClaimValidator` does not enforce either count limit.

D40's deferred token-limit policy remains deferred. Do not introduce a tokenizer or invent token-count guarantees.

### 6.4 Existing deterministic validation

Pass parsed candidate claims through the existing `ClaimValidator` and `ClaimWordingScreen` contracts.

Do not weaken, replace, or fork their deterministic rules.

Candidate construction must inject `packet.packetRef()` and `packet.mode()` from the packet, never from model output.

Preserve:

- evidence references;
- category consistency;
- qualification propagation;
- limitation inheritance;
- mode-aware insufficiency;
- forbidden wording rejection.

Reference existence establishes provenance only. It does not establish semantic entailment.

### 6.5 Orchestration deadline

Implement D42's adopted 45-second total monotonic orchestration budget:

- maximum 5 seconds for deterministic prompt construction;
- maximum 35 seconds for the existing D41 inference deadline, unchanged;
- maximum 5 seconds for parsing and candidate validation.

Preserve D41's existing inference deadline and all network and transport guarantees unchanged.

Implement cooperative deadline checks throughout orchestration.

On expiry:

- stop subsequent processing;
- discard all partial candidate claims;
- return the defined safe deadline-exceeded failure;
- never return a partial result.

Do not claim unsupported preemptive cancellation of synchronous parsing. Do not assume D41's internal deadline persists after `infer()` returns.

### 6.6 Transient result

Return only an internal transient result containing the outcome and validated candidate information required by the adopted design.

No database, durable file, audit persistence, cache, or cross-request memory.

No ordinary user-facing response surface.

### 6.7 Offline evaluation harness

Implement the adopted offline evaluation methodology:

- human-labelled benchmark fixtures;
- evidence-category and generation-mode coverage;
- deterministic metric calculation;
- failure classification;
- transient raw-output access in memory only.

The evaluator must not:

- persist raw prompts or model outputs;
- log raw prompts or outputs;
- expose them in ordinary responses;
- approve claims;
- influence `ClaimValidator`;
- grant model or evaluator authority.

Do not implement second-LLM evaluation. Keep model-assisted diagnostic evaluation deferred unless a separate decision later authorizes it.

Do not invent statistical sample sizes, evaluation thresholds, or quality guarantees. Keep those deferred under D42.

---

## 7. Exact file and package scope

### 7.1 Recommended package

Recommended package: `com.soubhagya.systivex.generation`

Rationale: it is an internal top-level package that can depend on `evidence` and `localmodel` without creating a reverse dependency from either.

D42 deliberately deferred the final package name. D43 recommends this package as an implementation-planning choice, not as a decision made by D42. The recommendation is non-binding until D43 is adopted and implementation is authorized. Even after adoption, the exact package may be confirmed during implementation planning if actual code conventions justify a narrowly scoped adjustment. No new architectural module or public API is implied.
### 7.2 Proposed production files

1. `EvidenceGenerationService`
   - Visibility: `public final class`
   - Annotation: `@Service`
   - Responsibility: orchestrate prompt construction, inference, parsing, validation, and transient result creation.
   - Dependencies: `LocalModelAdapter`, `ObjectMapper`, and internal collaborators.
   - Bean wiring: the service must be conditional on the actual `LocalModelAdapter` bean, using `@ConditionalOnBean(LocalModelAdapter.class)` at the appropriate configuration boundary or an equivalent explicit availability check. It must not be instantiated with a null provider. A future context test must prove that no `LocalModelAdapter` bean and no `EvidenceGenerationService` bean exist when the provider is disabled, that no generation path can invoke inference when disabled, that enabling a valid provider makes the service available, and that invalid or unavailable provider configuration still fails closed.
   - Input: `EvidencePacket`.
   - Output: `GenerationResult`.
   - Failure behavior: returns safe failure statuses; never throws raw provider or parser content to callers.
2. `GenerationResult`
   - Visibility: `public record`
   - Proposed fields:
     - `EvidenceReference packetRef`
     - `EvidenceMode mode`
     - `GenerationStatus status`
     - `List<GeneratedClaimResult> claims`
     - `int claimsParsed`
     - `int claimsValid`
     - `int claimsInvalid`
   - Invariants:
     - `claims` is immutable;
     - failure results contain no candidates;
     - no partial candidate list can escape;
     - `SUCCEEDED` means deterministic validation completed, not human acceptance;
     - no raw prompt, raw model output, stack trace, credential, or sensitive provider detail.
   - No raw prompt, raw model output, or packet content.

3. `GeneratedClaimResult`
   - Visibility: `public record`
   - Proposed fields:
     - `EvidenceClaim claim`
     - `ClaimValidationResult validationResult`
   - The claim uses the existing D40 `EvidenceClaim` type exactly; no second or forked claim schema is permitted.
   - Preserve all existing `EvidenceClaim` fields and invariants: statement, category, evidence references, qualification, limitations, packet reference, and mode.
   - No raw model text.

4. `GenerationStatus`
   - Visibility: `public enum`
   - Values: `SUCCEEDED`, `NO_OUTPUT`, `PARSE_FAILED`, `PROVIDER_UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESPONSE_TOO_LARGE`, `BUSY`, `PROMPT_TOO_LARGE`, `INVALID_PACKET`.
   - Invariants: success and safe-failure statuses are mutually exclusive; every safe-failure status requires an empty candidate collection; success means deterministic validation completed, not human acceptance.
5. `EvidenceGenerationPromptBuilder`
   - Visibility: package-private final class.
   - Responsibility: deterministic prompt construction and UTF-8 size calculation.
   - Dependencies: `ObjectMapper`.
   - Input: `EvidencePacket`.
   - Output: `String`.
   - Failure behavior: throws only internal, non-leaking exceptions; size enforcement occurs in the service.

6. `StrictClaimResponseParser`
   - Visibility: package-private final class.
   - Responsibility: strict Jackson parsing and structural bound enforcement.
   - Dependencies: `ObjectMapper`.
   - Input: bounded response `String`.
   - Output: parsed candidate structures.
   - Failure behavior: maps malformed or oversized structures to `PARSE_FAILED`.

No new Spring AI starter, provider client, runtime dependency, database dependency, or utility library is authorized.

### 7.3 Proposed test files

1. `EvidenceGenerationServiceTest`
   - orchestration, deadline, failure mapping, no auto-acceptance, transient result.

2. `StrictClaimResponseParserTest`
   - malformed JSON, duplicate keys, unknown fields, structural bounds, 25/100 claim boundaries.

3. `EvidenceGenerationPromptBuilderTest`
   - deterministic output, packet-local references, UTF-8 size, no mutation.

4. `EvidenceGenerationEvaluationTest`
   - offline fixtures, metrics, transient raw-output access, no persistence.

Additional adversarial tests may be added within these classes. No separate production class is required for evaluation-only diagnostics.

### 7.4 Visibility and dependency rules

- The generation package may depend on `evidence` and `localmodel`.
- `evidence` and `localmodel` must not depend on the generation package.
- The service is internal and has no endpoint.
- Collaborators are package-private.
- `LocalModelAdapter` remains the only provider inference boundary.

---

## 8. Resource and safety limits

### 8.1 Inherited D41 limits (unchanged)

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

### 8.2 D42 adopted Slice 3 limits

| Limit | Value | Source |
|---|---|---|
| Prompt cap | 32,768 UTF-8 bytes | D42 |
| D40 structural parser claim limit | 100 claim records | D40, unchanged |
| Slice 3 generated-candidate claim cap | 25 claim records | D42 |
| Max references per claim | 1-20 | Existing `EvidenceClaim` |
| Max limitations per claim | 0-100, each 1-1,000 characters | Existing `EvidenceClaim` |
| Max statement length | 1,000 characters | Existing `EvidenceLimits.MAX_TEXT_LENGTH` |
| Max nesting depth | 8 (candidate) | D42 |
| Max JSON name length | 64 characters (candidate) | D42 |
| Max document length | 65,536 bytes | Inherited response cap |
| Total orchestration budget | 45 seconds | D42 |
| Raw output retention | None in runtime path; transient in-memory evaluation only | D42 |

### 8.3 Prompt fail-closed behavior

If the completed prompt exceeds 32,768 UTF-8 bytes:

- do not call the model;
- do not truncate evidence;
- do not silently omit evidence;
- do not select a smaller evidence subset;
- do not summarize evidence to fit;
- return `PROMPT_TOO_LARGE` with no candidate claims.

### 8.4 Claim-count boundary

D40's 100-record structural parser limit and D42's 25-claim generation cap are distinct:

- A response exceeding 100 claims violates the existing D40 structural parser limit.
- A response with more than 25 generated claims rejects the entire response.
- No silent truncation is permitted.
- `ClaimValidator` does not enforce either count.
- Malformed structure, parser-limit violations, and generation-cap violations must remain distinguishable in failure reporting.

### 8.5 Orchestration deadline

Implement D42's 45-second total monotonic orchestration budget:

- maximum 5 seconds for deterministic prompt construction;
- maximum 35 seconds for the existing D41 inference deadline, unchanged;
- maximum 5 seconds for parsing and candidate validation.

Cooperative deadline checks must occur:

- at orchestration entry;
- during prompt construction;
- immediately before inference;
- immediately after inference;
- while parsing tokens or records;
- during candidate validation;
- before returning the result.

On expiry:

- stop subsequent processing;
- discard all partial candidate claims;
- return `DEADLINE_EXCEEDED`;
- never return a partial result.

Do not claim unsupported preemptive cancellation of synchronous parsing. Do not assume D41's internal deadline persists after `infer()` returns.

### 8.6 Parser controls

Preserve:

- `StreamReadFeature.STRICT_DUPLICATE_DETECTION`;
- `FAIL_ON_UNKNOWN_PROPERTIES`;
- bounded structural parsing;
- explicit safe failure categories.

D40's deferred token-limit policy remains deferred. Byte caps are not token limits. Do not introduce a tokenizer or invent token-count guarantees.

---

## 9. Security and authority boundaries

D43 preserves all D38-D42 boundaries.

The generator and model have no authority. Evidence remains untrusted input. Prompt delimiters are not a security boundary. No semantic-entailment guarantee is made. No automatic claim acceptance is permitted. Human review remains mandatory.

No tools or function calling. No RAG, vector database, MCP, or agent runtime. No external provider or cloud fallback. No second-LLM authority. No endpoint, persistence, frontend, new roles, or execution capability.

The offline evaluator may inspect raw model output transiently in memory. It must not persist or log raw prompts or outputs, expose them in ordinary responses, or grant model or evaluator authority.

---

## 10. Acceptance criteria

The following are future implementation acceptance requirements. They are not claims that tests or implementation already exist.

1. Deterministic prompt output for identical packets.
2. Prompt UTF-8 exact-boundary and over-boundary tests.
3. `PROMPT_TOO_LARGE` prevents model invocation.
4. No evidence truncation, omission, subset selection, or summarization.
5. Parser rejects malformed JSON, duplicate keys, unknown fields, and structural violations.
6. D40's 100-record parser cap is independent of the 25-claim generation cap.
7. Responses containing 25 or fewer generated claims are eligible for deterministic `ClaimValidator` evaluation. Claims that pass deterministic validation remain untrusted candidates and are never automatically accepted. Human review remains mandatory.

8. More than 25 claims rejects the entire response.

9. `ClaimValidator` is not incorrectly treated as enforcing either count limit.10. Existing reference, category, qualification, limitation, and wording validation remains unchanged.
11. Prompt construction deadline enforcement.
12. D41 inference deadline and transport tests remain green.
13. Parsing and validation deadline expiry discards every partial claim and result.
14. No false preemptive cancellation guarantees.
15. Evaluator raw-output access is transient and cannot leak to logs, storage, or ordinary responses.
16. No endpoint, persistence, frontend, roles, or execution capability.
17. Existing backend tests remain green.

Tests must be separated into:

- unit tests;
- parser adversarial tests;
- orchestration tests;
- adapter interaction tests;
- offline evaluation tests;
- full regression tests.

---

## 11. Implementation sequencing

Recommended sequence:

1. Prompt contract and deterministic prompt builder.
2. Bounded strict parser.
3. Generation orchestration and deadline propagation.
4. Existing `ClaimValidator` integration.
5. Transient generation result.
6. Offline evaluation harness.
7. Adversarial and security regression tests.
8. Full regression and independent implementation review.

Dependencies:

- Prompt construction must be complete and deterministic before parser and orchestration work.
- Parser must be complete before orchestration can validate candidates.
- Deadline propagation must be implemented with orchestration, not after it.
- Evaluation harness depends on the generation result contract.
- Full regression must include all D39, D40, and D41 tests.

No future slice, endpoint, presentation surface, or execution feature may be included.

---

## 12. Risks and rollback

### 12.1 Risks

- Prompt injection cannot be fully prevented by prompt construction.
- Deterministic validation does not establish semantic entailment.
- Model output may be plausible but unsupported.
- Model digest verification is not per-request cryptographic identity (D41 G-4).
- Synchronous parsing has bounded cancellation limitations.
- Evaluation metrics may not predict real-world evidence distributions.
- Implementation may accidentally conflate the 100-record parser limit with the 25-claim generation cap.
- Implementation may accidentally expose raw prompt or model output.

### 12.2 Rollback and disable strategy

- The provider is disabled by default (`systivex.local.enabled=false`), so generation has no runtime effect unless the provider is explicitly enabled.
- The generation service must be conditional on the actual `LocalModelAdapter` bean, using `@ConditionalOnBean(LocalModelAdapter.class)` or an equivalent explicit availability check. It must not be instantiated with a null provider.

- Implementation should be isolated in the generation package.- Rollback can remove the generation package and its tests without changing S1, S2, endpoints, persistence, or roles.
- No database migration or schema change is authorized, so rollback does not require data migration.
- Existing S1 and S2 behavior must remain unchanged when generation is absent or disabled.

---

## 13. Deferred choices

D43 does not resolve these D42-deferred items:

- exact package name; D43 recommends `com.soubhagya.systivex.generation` as an implementation-planning choice, subject to D43 adoption and implementation review;- evaluation sample sizes and strata;
- whether model-assisted diagnostic evaluation will be used;
- future endpoint exposure;
- token-limit policy.

D43 does not invent thresholds, token counts, or sample sizes.

---

## 14. Governance status

D43 is a proposal. It is not adopted. It does not authorize implementation.

If D43 is later adopted, implementation still requires explicit user authorization and must follow this proposal's exact scope. Any scope expansion requires a new governance decision.

---

## 15. Relationship to D38-D42

D43 is compatible with D38, D39, D40, D41, and D42 and preserves them unchanged.

- D38 remains the evidence foundation and trust boundary.
- D39 remains the S1 assembler design and implementation.
- D40 remains the claim contract and deterministic validator.
- D41 remains the local model provider design and implementation.
- D42 remains the Slice 3 design adoption decision.
- D43, if adopted, would authorize implementation only within D42's adopted design.

D43 creates no execution, approval, proposal-lifecycle, authentication, or provider authority. It does not amend D8 or weaken D38-D42.

---

## 16. Final self-review

D43 does not contradict D42. D42 remains design-only adoption. D41 transport guarantees are unchanged. D40 claim validation is unchanged. D39 packet assembly is unchanged. No model authority or automatic claim acceptance is introduced. No endpoint, persistence, frontend, new roles, or execution capability is introduced. No unapproved dependency is introduced. No token-limit policy is invented. No evaluation thresholds or sample sizes are silently adopted. Implementation acceptance criteria match the D42 design exactly.

---

## 17. Governance note

This document proposes D43 for review. It does not adopt D43, does not modify `DECISIONS.md`, and does not authorize implementation. Adoption and implementation authorization require an explicit, dated decision following repository governance conventions.
