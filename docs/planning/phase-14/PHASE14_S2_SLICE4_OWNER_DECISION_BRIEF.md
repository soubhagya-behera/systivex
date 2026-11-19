# Phase 14 S2 Slice 4 — Owner Decision Brief

**Status: NON-BINDING DECISION PREPARATION. NOT A DECISION, NOT AN ADOPTION,
NOT AN IMPLEMENTATION AUTHORIZATION.**

Every recommendation in this document is labelled **NON-BINDING**. No option is
selected, adopted, or authorized by this document. The repository owner retains
decision authority over all nine items (AD-1 through AD-9).

| Field | Value |
| --- | --- |
| Date | 2026-11-19 |
| Governance review baseline | `619030a7ecc927ff9e7d787ee2b0143f2fc3f7b6` (merge of PR #45; current main at review time, not a claim about future main) |
| Original proposal baseline | `9df5721b5f0df9a3556b4f32a58fd48f18230b9b` (merge of PR #28) |
| Assessed proposal | `docs/planning/phase-14/PHASE14_S2_SLICE4_PROPOSAL.md` (protected; read-only; unchanged) |
| Governing decision | D47 — Phase 14 S2 Slice 3 Authorization Traceability and Interim Disposition |
| Target decision | D44 (reserved for Slice 4; not adopted by this document) |
| Scope | Documentation and architecture analysis only; no implementation |

## 0. Governance frame

- D42 adopted the Slice 3 **design only** (D42 §L) and D43 adopted the bounded
  implementation **scope and acceptance criteria** only (D43 §M). Neither
  authorized the existing Slice 3 implementation.
- D47 records that the Slice 3 implementation is present and preserved
  unchanged, that its presence does not establish authorization, that
  authorization traceability cannot currently be verified, and that any future
  use or continuation requires a separate explicit **prospective** authorization
  decision.
- D47 does not adopt D44, does not authorize Slice 4, and leaves the D42 §I.4 /
  D43 §L endpoint-exposure deferral unresolved.
- This brief must not be read as satisfying the D47 prospective authorization,
  as selecting any AD option, or as authorizing any code, endpoint, frontend,
  role, persistence, migration, dependency, configuration, or provider
  activation.

## 1. Verified facts at the review baseline

All file references below are to the review baseline `619030a7`. Line numbers
are from that tree. These are facts about code and adopted decisions, separated
from the proposal's proposed behavior and from unresolved assumptions.

### 1.1 Verified current facts

| Area | Verified fact | Source |
| --- | --- | --- |
| Packet identity | Every packet is built with `EvidenceReference.packet(1)` — the constant `P1`; item refs are assigned positionally `E1..En` after sorting by category ordinal then source. No run counter, nonce, or discriminator exists. | `evidence/EvidencePacketAssembler.java:274-276,304,307` |
| Reference semantics | `EvidenceReference` is documented as "transient, deterministic, and must never be treated as durable or global evidence identifiers"; format `[EP][1-9][0-9]*`. | `evidence/EvidenceReference.java:5-12` |
| Packet shape | `EvidencePacket(packetRef, mode, items, limits, omissions)`; limits defaults enumerate the adopted caps. | `evidence/EvidencePacket.java:9-14,81-104` |
| Limits | 10 roots, depth 1-10 (default 6), 500 impact nodes / 2,000 paths, 100 items per family, 1,000 total items, 262,144 serialized UTF-8 bytes, 100 audit events, 100 proposal operations, 1,000-char text, 100 limitations, 20 references per claim; `MAX_SIMULATION_OPERATIONS = SimulationLimits.MAX_OPERATIONS` = 100. | `evidence/EvidenceLimits.java:9-35`; `intelligence/SimulationLimits.java:13` |
| Assembly transaction | `@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)`; no global snapshot claim. | `evidence/EvidencePacketAssembler.java:84`, javadoc `:39-40` |
| Assembly failures | `serialize(...)` throws `EvidenceAssemblyException(PACKET_TOO_LARGE)` and `(SERIALIZATION_FAILED)`. | `evidence/EvidencePacketAssembler.java:191,197`; `EvidenceAssemblyException.java:5-6` |
| Request validation | `EvidencePacketRequest` validates mode, roots, depth, includeTypes, operation element non-nullness, BASELINE/operation conflict, and ≤100 operations; shape validation is in `SimulationProposalValidator.requireShape(...)`, reached from the assembler before `simulateResolved(...)`. Shape messages interpolate the operation type and now also server-authored field names; caller-supplied values are not interpolated by those checks. | `evidence/EvidencePacketRequest.java:21-84`; `intelligence/SimulationProposalValidator.java:123,207-247`; `evidence/EvidencePacketAssembler.java:221-227` |
| Claim validation | `ClaimValidator` enforces packet identity, reference existence, category consistency, qualification contribution (32-pair table, explicit worst-first precedence), limitation inheritance, mode-aware insufficiency, and the wording screen. `EvidenceClaim` constructor enforces statement ≤1,000 chars, ≤20 references, ≤100 limitations. | `evidence/ClaimValidator.java:11-120,123-148,150-159,201-211`; `evidence/EvidenceClaim.java:16,22,34,56` |
| Generation service | `@ConditionalOnBean(LocalModelAdapter.class)`; 32,768-byte prompt cap; deadlines 45 s total / 5 s prompt / 5 s parse+validate; injects `packet.packetRef()` and `packet.mode()` into candidates; returns no prompt or raw model output. | `generation/EvidenceGenerationService.java:21,25,27-29,138-139,158-205` |
| Generation contract | `GenerationResult(packetRef, mode, status, claims, claimsParsed, claimsValid, claimsInvalid)` with immutable claims; nine `GenerationStatus` values; parser generation cap 25, strict Jackson config with `STRICT_DUPLICATE_DETECTION`, `FAIL_ON_UNKNOWN_PROPERTIES`, nesting 8, document length 65,536 characters, string 1,000, name 64. | `generation/GenerationResult.java:7-24`; `GenerationStatus.java:3-13`; `StrictClaimResponseParser.java:20,30-39,74,93` |
| Provider boundary | `LocalModelAdapter` holds static `Semaphore(1, true)`, `tryAcquire(0, …)`, maps contention to `AnalysisBusyException`; digest verification runs before inference; `PullModelStrategy.NEVER`, `maxRetries(0)`, explicit `RetryTemplate(maxRetries(0))`; provider bean requires `systivex.local.enabled=true`, default `false`, base URL `http://127.0.0.1:11434`, expected digest required when enabled. | `localmodel/LocalModelAdapter.java:28,71-108,136-166,241-244`; `LocalModelConfiguration.java:10`; `LocalModelProperties.java:11,13,53-64` |
| Security | Default-deny `anyRequest().denyAll()`; no CORS rules; `TWIN_READ` gates deterministic GETs including impact/risk; `TWIN_ANALYZE` gates simulation/policy POSTs and proposal authoring; `TWIN_APPROVER` alone gates approve/reject; `TWIN_ADMIN` inherits READ/ANALYZE/OPERATOR but not APPROVER. | `security/SecurityConfig.java:39,117-144`; `JwtGrantedAuthoritiesConverter.java:28-35` |
| Errors | `RestExceptionHandler` maps 11 exception types and returns `ex.getMessage()` for 7 of them; no `EvidenceAssemblyException` mapping exists. `ErrorResponse(timestamp, status, error, message, path, fieldErrors)` includes a timestamp. No `spring.jackson.*` or `server.error.*` setting in `application.example.properties`. | `twin/api/RestExceptionHandler.java:27-150`; `twin/api/ErrorResponse.java:7-17`; `application.example.properties:77-83` |
| Frontend preview | `canUsePreview(env)` returns true only when `DEV === true && PROD !== true`; `App.jsx` loads `syntheticTwin.js` through a DEV-only dynamic import; token is React state only (no storage); API client attaches bearer token to `/api`; existing API modules validate response shapes; `ErrorPanel` and `useAsyncResource` provide explicit error states; no `dangerouslySetInnerHTML`, `innerHTML`, or `eval` in production frontend sources. | `preview/localPreview.js:1-11`; `App.jsx:5,11,18-19`; `auth/TokenContext.jsx:4-13`; `api/client.js:25-61`; `api/analysis.js:32`; `api/proposals.js:153`; `hooks/useAsyncResource.js:11-43`; `components/StatePanels.jsx:31` |
| Inventory | Generation package: 6 tracked production classes, 6 tracked test classes, 39 `@Test` methods. Evidence package: 38 tracked production files. Local model package: 9 tracked production files. | `git ls-tree` / `git grep` at baseline |

### 1.2 Proposed behavior (not present in code)

The following exist only in the protected proposal: `CandidateClaimController`,
`CandidateClaimService`, `CandidateClaimProjection`, the transport request DTO,
the `EvidenceAssemblyException` HTTP mapping, the `SecurityConfig` matcher, the
candidate response DTOs, the frontend view/components/styles/navigation entry,
and the four-state presentation rules. None of these exists on the review
baseline and none is authorized by this brief.

### 1.3 Unresolved assumptions and factual drift

- The proposal's §1/§2.1 attribute the Slice 3 implementation to "D42/D43"
  without qualification. This is inaccurate: D42 §L and D43 §M authorize no
  implementation; D47 records the interim hold and prospective-authorization
  requirement. A PR merge is not authorization.
- The proposal's baseline is `9df5721`; the review baseline is `619030a7`, 23
  commits and 81 files later (5,928 insertions, 245 deletions). D45, D46, and
  D47 were added after the proposal baseline.
- Proposal §2.3 fact 12 states that the only interpolated value in operation
  shape messages is the operation type enum. At the review baseline,
  `requireIdentifierLength(...)` also interpolates a server-authored field
  name. Caller-supplied values are still not interpolated by those checks.
- All other sampled `[ADOPTED-VERIFIED]` facts in §2.3 remain accurate at the
  review baseline. This is documentation analysis, not test or live
  verification.

## 2. Owner decisions AD-1 through AD-9

### AD-1 — Endpoint exposure

1. **Exact question being decided** (proposal §16.1):
   > **Endpoint exposure** | Whether Slice 4 may expose any endpoint at all. This resolves the D42 §I.4 / D43 §L deferral of *"future endpoint exposure"*. Without it there is no live surface; Option 1F becomes the whole of Slice 4.

2. **Options from the proposal:** expose exactly one stateless `POST` returning
   candidates and cited evidence in one response (Option 1), or expose no
   endpoint and make fixture-only presentation the whole of Slice 4 (Option 1F).

3. **Verified technical facts:** D42 §I.4 and D43 §L defer future endpoint
   exposure; D47 does not resolve it. No Slice 4 endpoint exists. The security
   chain is default-deny (`SecurityConfig.java:144`), so any endpoint needs an
   explicit matcher and a recorded security review. The provider is disabled by
   default and the generation service is conditional on the provider bean.

4. **Consequences:** A live endpoint makes local inference browser-triggerable
   and creates a new availability surface; it also creates the only egress path
   for model-derived and evidence-derived content. Fixture-only adds no HTTP
   surface, no role, and no inference reachability, but does not deliver a live
   review purpose. Neither option grants claim-acceptance authority.

5. **Dependencies:** AD-2 (role), AD-3 (abuse control), AD-4 (response bound),
   AD-5 (ordering), AD-8 (naming), and the D47 prospective authorization before
   any use of Slice 3.

6. **Recommendation — NON-BINDING:** treat Option 1F as the first step and make
   any live endpoint conditional on AD-2/AD-3/AD-4/AD-8 being recorded, a
   security review, and a separate prospective authorization under D47.
   *Justification:* it exercises the highest-risk presentation rules at zero
   backend authority while the Slice 3 authorization gap is unresolved, without
   foreclosing the live purpose later.

7. **What remains for the owner to approve:** whether any endpoint may be
   exposed at all, and the ordering relative to the fixture-only step.

### AD-2 — Role for the endpoint

1. **Exact question being decided:**
   > **Role for the endpoint** | `TWIN_READ` versus `TWIN_ANALYZE`, with the consequences in §12.2.2, the corrected factual basis in §12.2.1, and the non-binding recommendation in §12.2.3.

2. **Options from the proposal:** `TWIN_READ`, `TWIN_ANALYZE`; reuse of
   `TWIN_APPROVER` is rejected and no new role is proposed.

3. **Verified technical facts:** `SecurityConfig.java:122-127` assigns
   `TWIN_READ` to deterministic GETs including impact and risk;
   `:132-133` assigns `TWIN_ANALYZE` to simulation and policy POSTs.
   `:135-138` additionally assigns `TWIN_ANALYZE` to proposal creation
   (`POST /api/v1/twin/proposals`) and to withdrawal, assessment and submission
   (`POST /api/v1/twin/proposals/*/withdraw`, `.../assess`, `.../submit`);
   `:141` assigns `TWIN_ANALYZE` to proposal modification
   (`PATCH /api/v1/twin/proposals/*`). `:139-140` assigns approve/reject to
   `TWIN_APPROVER`. `JwtGrantedAuthoritiesConverter.java:28-35` enforces an
   allowlist and `TWIN_ADMIN` inheritance of READ/ANALYZE/OPERATOR but not
   APPROVER.

4. **Consequences and trade-off:**
   - `TWIN_READ` makes inference reachable to a broader read-only audience,
     including every holder of the least-privileged read role, widening both
     exposure to model-authored prose and the compute/contention surface.
   - `TWIN_ANALYZE` narrows inference access, but combines it with the existing
     proposal-authoring capabilities listed above: creation, withdrawal,
     assessment, submission and modification. A reviewer granted access through
     this role would also hold those permissions.
   - Neither role is dedicated exclusively to candidate generation.
     `TWIN_READ` covers ordinary deterministic reads; `TWIN_ANALYZE` covers
     simulation, policy and proposal authoring as well.
   - Neither option provides human-review acceptance or approval authority.
     Approval remains solely with `TWIN_APPROVER`, and the proposed surface
     records no review outcome, so D29 separation of duties is neither engaged
     nor satisfied by this choice.
   - `TWIN_ANALYZE` also creates an asymmetry for `TWIN_READ`-only console
     operators.

5. **Dependencies:** AD-1; AD-3 if the principal is used for rate control.

6. **Recommendation — NON-BINDING:** `TWIN_ANALYZE` remains the non-binding
   preference, on a narrower rationale than the proposal's original wording:
   the proposed operation constructs a derived artifact from caller parameters
   and would be the only surface invoking an untrusted local model, so limiting
   inference reachability to the smaller analysis-role population is preferable
   to exposing it to every read-only token holder. This is a preference about
   the breadth of inference access, **not** a claim that `TWIN_ANALYZE` is
   reserved exclusively for derived artifacts — the verified mapping shows it
   also carries proposal creation, withdrawal, assessment, submission and
   modification permissions. The owner must weigh that bundle against the
   wider exposure under `TWIN_READ`; this brief does not select or adopt either
   role.

7. **What remains for the owner to approve:** the chosen role, the reasoning,
   and acceptance of the resulting compute-exposure consequence. No role
   definition, allowlist, or inheritance change is authorized.

### AD-3 — Rate and abuse posture

1. **Exact question being decided:**
   > **Rate and abuse posture for inference-triggering requests** | See §16.1.1 below.

2. **Options from the proposal (§16.1.1):** R-A no additional control beyond
   the D41 single-flight semaphore; R-B per-principal serialization and
   cooldown; R-C global concurrency/throughput bound at the endpoint; R-D
   deployment-level control outside the application; R-E no live endpoint
   (Option 1F only). No numeric threshold is proposed.

3. **Verified technical facts:** `LocalModelAdapter.java:28` uses
   `new Semaphore(1, true)`; `:79` uses `tryAcquire(0, …)`; `:86` throws
   `AnalysisBusyException`, mapped to `BUSY`. This bounds concurrency only.
   The generation budget is 45 s with a 35 s inference window. No rate limiter
   exists in the codebase.

4. **Consequences:** R-A leaves sequential starvation unsolved: one authorized
   principal can hold the permit for successive 35 s windows and deny others.
   R-B needs transient in-memory limiter state and principal access at the
   boundary; R-C needs a non-durable limiter that must not replace the D41
   semaphore; R-D does not protect against an authorized local user; R-E
   removes the exposure but not the live purpose.

5. **Dependencies:** AD-1, AD-2, AD-5, AD-8; any numeric threshold is an
   implementation-time decision subordinate to the owner's posture choice.

6. **Recommendation — NON-BINDING:** while D47's hold is unresolved, R-E; if a
   live endpoint is later approved, R-B combined with R-D, with transient
   non-durable state and the existing semaphore preserved. *Justification:*
   R-B addresses the specific unmitigated starvation vector using an existing
   trusted principal, and R-D adds a deployment layer without touching the D41
   boundary. **No numeric threshold is selected or recommended here.**

7. **What remains for the owner to approve:** the posture, the accepted
   residual, any numeric threshold with its basis, and the recorded security
   review.

### AD-4 — HTTP response-size ceiling and bounded serialization

1. **Exact question being decided:**
   > **Response-size ceiling and bounded serialization contract** | Whether a concrete numeric ceiling in **bytes** for the serialized HTTP response body is adopted, and the bounded serialization contract that accompanies it. The decision must record: the numeric ceiling and its measurement method; the JSON escaping behaviour and whether model-derived strings are bounded on their escaped form as well as their character form; the per-item evidence metadata budget and whether the projection shape is bounded relative to the packet shape; the response envelope overhead including field names; the per-candidate metadata budget, including repeated limitation strings and repeated `E<n>` reference strings; the candidate count the ceiling is stated against; whether repeated per-candidate metadata is deduplicated or explicitly accepted as repeated; and the testable criterion that fails when the ceiling is exceeded. See §7.7.2–§7.7.4, §14 AC-14 and §16 R12. **Until this decision is recorded, no numeric response bound is claimed by this proposal, and the adopted upstream limits A–D are not treated as such a bound.**

2. **Options from the proposal:** adopt a concrete byte ceiling plus the
   bounded contract, or leave the ceiling unadopted (which blocks AC-14).

3. **Verified technical facts:** the adopted limits are 262,144 packet bytes,
   65,536 raw model-response bytes, parser document length measured in
   characters at 65,536, and 1,000-character string/field caps. None measures
   the serialized HTTP response. Only evidence items are deduplicated;
   per-candidate limitations and reference strings repeat. The generation cap
   is 25 claims. `StrictClaimResponseParser.java:25` records that Jackson
   counts document length in characters for this textual parser.

4. **Consequences:** without a ceiling the response is not bounded; with one,
   the owner must choose a number and a measurement method, accept that valid
   large results may fail, and maintain an allowlisted projection whose escaped
   form is also bounded.

5. **Dependencies:** AD-1, AD-5, AD-6, AD-8, and acceptance criterion AC-14.

6. **Recommendation — NON-BINDING:** adopt a measurable contract: enforce a
   byte ceiling on the **actual serialized response body** during serialization
   (not a sum of component sizes), bound model-derived strings on their escaped
   form, keep evidence deduplicated and packet-scoped, state the candidate count
   the ceiling is measured against, and add a build-failing test at the
   boundary. Fixed internal constants only, with no caller override (D42 §G).
   *Justification:* this is the only approach that bounds the real artifact;
   the upstream limits provably do not. **This brief does not select a numeric
   limit.**

7. **What remains for the owner to approve:** the numeric ceiling, measurement
   method, escaping behavior, metadata/envelope budgets, candidate count,
   deduplication choice, and testable failure criterion.

### AD-5 — Fixture-only presentation ordering

1. **Exact question being decided:**
   > **Whether a fixture-only presentation (Option 1F) precedes, accompanies, or replaces the live surface** | Option 1F is strictly smaller on every axis and validates the highest-risk presentation rules at zero backend cost; it does not validate the endpoint, the role, the projection, the real evidence, the rate posture, or the error contract. See §10.3.

2. **Options from the proposal:** precede, accompany, or replace the live
   surface.

3. **Verified technical facts:** `localPreview.js:9-10` gates on
   `DEV === true && PROD !== true`; `App.jsx:18-19` uses a DEV-only dynamic
   import; `frontend/src/test/preview.test.jsx` asserts the gate; the preview
   makes no backend request and introduces no role or inference reachability.

4. **Consequences:** fixture-only validates wording, labeling, four-state
   separation, accessibility, and text-only rendering with zero new backend
   surface or authority, but validates nothing about the endpoint, projection,
   real evidence, rate posture, or error contract.

5. **Dependencies:** AD-1 and AD-8.

6. **Recommendation — NON-BINDING:** precede or accompany the live surface; do
   not treat it as a replacement if the live review purpose is retained.
   *Justification:* it validates the presentation rules that carry the most
   governance risk while D47's hold is unresolved, at no backend cost.

7. **What remains for the owner to approve:** whether the fixture-only step
   precedes, accompanies, or replaces the live surface.

### AD-6 — Rejected candidate statement display

1. **Exact question being decided:**
   > **Whether rejected candidate statements are displayed** | Default in this proposal: not displayed (§7.3, §7.6 rule 5). Alternative: display them in a clearly separated, non-reviewable section — which increases exposure of text that failed the wording screen.

2. **Options from the proposal:** do not display rejected statements (default),
   or display them in a clearly separated non-reviewable section.

3. **Verified technical facts:** `ClaimValidator` records `ClaimFailure` and
   `detail` and rejects on wording among other checks
   (`ClaimValidator.java:114-118`); `ClaimValidationResult.detail` is internal
   free text and can embed evidence limitation strings. The generation result
   carries counts but no statement text for failures. If rejected statements
   are shown, their cited evidence must join the same deduplicated evidence
   collection.

4. **Consequences:** displaying rejected text risks a reader encountering an
   assertion the deterministic screen already forbade and increases response
   size; hiding it reduces diagnostic transparency but retains counts by
   failure class and fixed explanations.

5. **Dependencies:** AD-1 and AD-4 (size budget).

6. **Recommendation — NON-BINDING:** keep the default — counts by failure class
   plus fixed server-authored explanations, no rejected statement text.
   *Justification:* it avoids rendering text already found forbidden by the
   wording screen and keeps the response smaller and simpler to bound.

7. **What remains for the owner to approve:** display or non-display, and the
   consequence for the evidence collection if display is chosen.

### AD-7 — Durable evidence identity

1. **Exact question being decided:**
   > **Durable evidence identity** | Whether durable evidence identity is ever to exist, and therefore whether Option 3 can ever be unblocked. Requires its own decision (§10.5).

2. **Options from the proposal:** retain packet-local identity only (Options 1
   and 1F), or ever introduce durable evidence identity (which would unblock
   consideration of Option 3).

3. **Verified technical facts:** `EvidenceReference.java:5-9` declares
   references transient and non-durable; `EvidencePacketAssembler.java:307`
   emits constant `P1`; `:304` emits positional `E1..En`; D38 §B forbids
   inventing durable IDs where existing APIs do not provide them; D42 §L and
   D43 §O exclude persistence.

4. **Consequences:** retaining packet-local identity means no review record can
   be persisted or resolved against a later packet, and cross-run transcription
   confusion cannot be prevented by the system. Introducing durable identity
   requires a separate decision, persistence, retention rules, and new
   identity-handling surface.

5. **Dependencies:** AD-1 (single-response scope) and AD-6; it gates any future
   Options 2/3 consideration.

6. **Recommendation — NON-BINDING:** do not introduce durable evidence identity
   in this slice; keep a single response with no review records. *Justification:*
   D38's prohibition and the `P1`/positional-`E<n>` facts make a stored record
   unresolvable, and persistence is excluded by D42/D43/D47.

7. **What remains for the owner to approve:** whether durable evidence identity
   is ever to exist, in a separate decision, if that question is to be opened.

### AD-8 — Component and naming set

1. **Exact question being decided:**
   > **Adopted component and naming set** | The endpoint path, package name, and class names as *final* implementation names, including the requirement that machine-side vocabulary must not contain "review" (§5.2). Working names in this document are illustrative only.

2. **Options from the proposal:** adopt the working names as final (non-binding
   suggestions: `POST /api/v1/twin/analysis/claims` and
   `com.soubhagya.systivex.generation.candidates`), or choose others, subject to
   the constraint that machine-side vocabulary must not contain "review".

3. **Verified technical facts:** no Slice 4 files exist at the review baseline;
   `SecurityConfig.java:144` is default-deny, so an explicit matcher is needed
   for any path; the generation package already provides the boundary the
   proposal would read from.

4. **Consequences:** naming determines the public path and the machine-side
   vocabulary. Using "review" in service/record/DTO names would imply a
   capability the design explicitly disclaims; path choice must be reviewed as
   part of the security matcher.

5. **Dependencies:** AD-1, AD-2, AD-4.

6. **Recommendation — NON-BINDING:** if the owner opens this question, adopt the
   proposed working names as final while enforcing the no-"review" constraint
   for machine-side artifacts. *Justification:* they align with the existing
   `generation` package and make the explicit default-deny matcher easy to
   review. These remain illustrative until the owner adopts them.

7. **What remains for the owner to approve:** the final path, package, and
   class names; no naming choice is adopted by this brief.

### AD-9 — Model/version disclosure

1. **Exact question being decided:**
   > **Whether the model and version are disclosed to the reviewer** | Requires the model pinning decision (D40 §I) to be resolved first. See §16.3.

2. **Options from the proposal:** disclose the model identity to the reviewer,
   or do not; disclosure is blocked until model pinning is resolved.

3. **Verified technical facts:** D40 §I defers model/version selection and
   pinning; `LocalModelProperties.java:53-64` requires a model name and
   expected digest only when the provider is enabled (default false);
   `LocalModelAdapter.java:74,136-166` verifies the installed digest before
   inference, which D41 G-4 records as not a per-request cryptographic
   identity.

4. **Consequences:** displaying an unpinned model identity could imply a
   guarantee that is not established; pinning would require a new decision and
   an operational commitment.

5. **Dependencies:** the deferred D40 §I pinning decision and AD-1.

6. **Recommendation — NON-BINDING:** do not display model identity until
   pinning is decided; if later displayed, label the digest as an
   installation-time check, not a per-request guarantee. *Justification:* it
   avoids presenting an unverified identity as assurance and respects D40 §I.

7. **What remains for the owner to approve:** whether disclosure is wanted, and
   separately the model-pinning decision that gates it.

## 3. Cross-cutting architectural risks

### 3.1 Endpoint and provider authority

Fixture-only presentation (AD-5, Option 1F) and a live inference-triggering
endpoint (AD-1, Option 1) are not equivalent, even though neither mutates
persisted control-plane state. A `POST` that assembles evidence, builds a
prompt, and invokes an untrusted local model performs material compute and
creates a new availability and egress surface; "read-only" must not be used to
imply harmlessness. D47 requires a separate, explicit, prospective
authorization before any future use or continuation of the existing Slice 3
implementation. Selecting a live endpoint here would bypass that gate; this
brief does not do so.

### 3.2 Rate and abuse posture

The D41 semaphore (`Semaphore(1, true)` with `tryAcquire(0, …)`) bounds
concurrency, not sequential volume. One authorized principal can issue
back-to-back requests and deny all others for as long as it continues. This
brief records the R-A…R-E options and their consequences without selecting a
mechanism or inventing a numeric threshold; any threshold and its basis are for
the owner under AD-3.

### 3.3 Response boundedness

The upstream limits A–D do not prove an HTTP serialized-response ceiling:

- JSON escaping expands model-derived strings beyond their character count;
  `StrictClaimResponseParser.java:25` records that document length is counted
  in characters, not bytes.
- Each evidence item repeats `ref`, `category`, `source`, `scope`,
  `sourceVersion`, timestamps, limitations, qualification, status, and counts.
- The response envelope and nested field names add overhead measured by none of
  A–D.
- Only evidence items are deduplicated; each candidate repeats limitations,
  reference strings, category, and both qualifications.
- Candidate count is bounded by generation limits (25), not by a response
  limit.

The recommended measurable contract is a byte ceiling on the actual serialized
body, enforced during serialization, with escaped-form bounds, a defined
candidate-count basis, deduplicated evidence, and a build-failing test. The
numeric value is deliberately left to the owner (AD-4).

### 3.4 Evidence identity

D38 §B prohibits inventing durable evidence identifiers. The verified identity
is constant `P1` with positional `E1..En`. Consequences: references cannot
distinguish runs, cannot be stored and resolved later, and cannot be used as a
review record. The mitigations that actually work are single-response scope,
stale-payload clearing, one-run labeling, and no exportable artifact — not the
packet reference.

### 3.5 Human review

Generated claims remain untrusted candidates. Passing `ClaimValidator`
establishes structure, provenance, category, qualification, limitation
inheritance, mode-aware insufficiency, and wording compliance — not semantic
support. No state representing "human-reviewed" or "accepted" exists or is
proposed; rejected candidates must never become accepted claims automatically.
No claim path may reach proposal mutation, approval, execution, tools, or
target services. D29 separation of duties is untouched and is not claimed to
be provided by a surface that records no decision.

## 4. Reminder — design adoption is not implementation authorization

A future D44 design adoption would be documentation only. It would not
authorize Slice 4 code, an endpoint, a frontend, a role, persistence, a
migration, a dependency, a configuration change, provider activation, Slice 3
use, or Slice 4 implementation. Under the proposal's Gate B, implementation
requires a separate explicit authorization after adoption, and Gate C requires
completion verification and a recorded security review. D47's prospective
authorization requirement applies independently to any future use or
continuation of Slice 3. This brief authorizes none of those things.

All nine AD items remain **UNRESOLVED** pending explicit owner decisions.
