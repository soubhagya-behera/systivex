# Phase 14 S2 Slice 4 — Gate Assessment

**Document status:** Governance gate assessment. Not a decision, not an adoption,
not an implementation authorization, and not an answer to any owner decision.

| Field | Value |
| --- | --- |
| Date | 2026-11-19 |
| Governance baseline | `868eeef8b92cdf28e885752feed713df4c2cc5a0` (PR #46 merge; current main at the time of this proposal). Earlier governance review baseline: `619030a7ecc927ff9e7d787ee2b0143f2fc3f7b6` (PR #45 merge). Neither is a claim about future main. |
| Branch status | Proposed design-only D44 for Option 1F on `docs/phase14-d44-fixture-only-adoption`; not adopted on main until the adopting PR is merged |
| Assessed proposal | `docs/planning/phase-14/PHASE14_S2_SLICE4_PROPOSAL.md` (protected; read-only; unchanged) |
| Proposal status | `DRAFT — NOT ADOPTED — DESIGN ONLY` |
| Governing decision | D47 — Phase 14 S2 Slice 3 Authorization Traceability and Interim Disposition |
| Verdict | On main: Slice 4 is blocked pending governance and proposal-gate resolution. On the review branch: design-only D44 proposed for fixture-only Option 1F; no implementation authorized |

This document reviews the protected local proposal without modifying it. It
records objective facts and the questions that remain for the repository owner.
On main it does not choose among the options the proposal presents. On the
review branch, proposed D44 records owner dispositions for independent review;
they are not effective on main until the adopting PR is merged.

## Proposed D44 disposition on the review branch

Branch `docs/phase14-d44-fixture-only-adoption` proposes a **design-only** D44
adopting only verified Option 1F. The design is
`docs/planning/phase-14/PHASE14_S2_SLICE4_FIXTURE_ONLY_DESIGN.md`. The proposed
dispositions, recorded in draft D44 §B, are:

| AD | Proposed disposition (branch only) |
| --- | --- |
| AD-1 | Option 1F only; no live endpoint in this Slice 4. |
| AD-2 | Deferred / not applicable without an endpoint; `TWIN_ANALYZE` remains a future non-binding preference, not an adopted role. |
| AD-3 | R-E for this slice; R-B/R-D not adopted. |
| AD-4 | Deferred; no HTTP generation response in fixture scope, and a numeric serialized-response ceiling is mandatory before any future live endpoint design. |
| AD-5 | Option 1F is the entire current Slice 4 scope; live surface needs a separate decision. |
| AD-6 | No rejected candidate statement text; synthetic fixtures may show counts and fixed labels. |
| AD-7 | No durable evidence identity or persisted review record; only clearly labelled synthetic fixture references. |
| AD-8 | Defer live endpoint, backend package and transport class naming; keep the no-"review" machine-side constraint. |
| AD-9 | No model/version disclosure; model pinning remains deferred under D40 §I. |

D47 remains an independent gate: no Slice 3 invocation, no provider activation,
no generation service use, no backend API call, and no retrospective
authorization. Any future live use of Slice 3 requires a separate prospective
authorization decision. These dispositions are proposed, not adopted, until
merge.

## 1. Baseline and current-main difference

The proposal declares its design base as `origin/main` at
`9df5721b5f0df9a3556b4f32a58fd48f18230b9b` (merge of PR #28). The governance
review baseline is `619030a7ecc927ff9e7d787ee2b0143f2fc3f7b6`, the merge commit
of PR #45 and current main at the time of this review. That SHA records the
reviewed baseline; it is not a claim that main will remain at that commit.

- The review baseline is **23 commits ahead** of the proposal's declared base.
- The difference is **81 changed files** (5,928 insertions, 245 deletions).
- Decisions added after the base include D45 (checkout idempotency), D46
  (frontend CI separation), and D47 (Slice 3 authorization traceability and
  interim hold); `PROJECT_STATUS.md` was corrected by PR #37 to record the
  Slice 3 authorization-traceability finding.
- Source files the proposal marks `[ADOPTED-VERIFIED]` changed after the base,
  including `EvidencePacketAssembler`, `EvidenceLimits`,
  `SimulationProposalValidator`, `SimulationRequest`, `SimulationService`,
  `ImpactQueryValidator`, `ImpactAnalysisService`, and `localmodel/Deadline`.
- Selected facts were re-checked against current main and still hold: the
  packet reference is the constant `P1`; `MAX_SIMULATION_OPERATIONS` remains
  100; `LocalModelAdapter` retains the process-wide single-flight semaphore;
  `EvidenceGenerationService` remains `@ConditionalOnBean(LocalModelAdapter.class)`;
  `RestExceptionHandler` still has no `EvidenceAssemblyException` mapping and
  still returns `ex.getMessage()` for several exception types.
- The `[ADOPTED-VERIFIED]` annotations were re-verified against the review
  baseline for this assessment; the results are recorded in
  `PHASE14_S2_SLICE4_OWNER_DECISION_BRIEF.md` §1. That re-verification is
  documentation analysis, not test or live verification.

## 2. Inaccurate D42/D43 implementation-authorization attribution

The proposal's purpose and context sections describe Slice 3 as "D42/D43,
merged via PR #28" and list its decision as "D42, D43 — Implemented (PR #28)".
That wording attributes the implementation to D42 and D43 without qualification.

The adopted record says otherwise:

- D42 §L: "D42 adopts the Slice 3 design only", authorizes no Java
  implementation, tests, dependencies, configuration, endpoints, persistence,
  frontend, or roles; implementation "requires a separate subsequent
  architectural decision and explicit user authorization".
- D43 §M: "D43 does not authorize implementation. No source-code, test,
  dependency, configuration, endpoint, persistence, frontend, role, or
  execution-capability change may begin until the user separately and
  explicitly authorizes Slice 3 implementation after D43 adoption."
- D43 §N/O: no future endpoint, presentation surface, persistence, or
  execution capability.

A pull-request merge establishes that code entered the repository. It does not
establish the governance authorization that D42 and D43 require. The proposal
must be revised so that it does not present D42/D43 as the authorization for
the implementation it relies on. D47 adds the interim disposition: the existing
implementation is preserved unchanged, its presence does not establish
authorization, authorization traceability cannot currently be verified, and
any future use or continuation requires a separate explicit prospective
authorization decision.

## 3. Current governance status under D47

D47 records a governance-only finding and interim hold:

- Slice 3 implementation exists and is preserved unchanged.
- Authorization traceability for that implementation **cannot currently be
  verified** from the recorded governance history.
- No historical authorization is inferred from commits, tests, implementation
  artifacts, or pull-request merges; equally, it is **not asserted that no
  approval ever occurred**, because the available record cannot establish that
  negative.
- No retrospective authorization is granted; D42 and D43 are unchanged.
- No new runtime invocation, provider activation for generation, endpoint,
  presentation surface, persistence, role, or proposal mutation is authorized;
  the default-off provider configuration is unchanged; no source or test change
  is authorized.
- Human review remains mandatory; any future use or continuation requires a
  separate explicit **prospective** authorization decision.
- D47 does not adopt D44 and does not authorize Slice 4. D44 remains reserved
  for Slice 4; D45 and D46 are unchanged.

The proposal predates D47 and has no section describing this status. Its Gate A
cannot be exercised while its factual premise omits the governing finding.

## 4. Owner decision register (AD-1 through AD-9)

The descriptions below are quoted verbatim from proposal §16.1. "Objective
facts" are drawn from the proposal and the current adopted record; they do not
resolve the decision. Every item remains an explicit owner decision.

### AD-1 — Endpoint exposure

> **Endpoint exposure** | Whether Slice 4 may expose any endpoint at all. This resolves the D42 §I.4 / D43 §L deferral of *"future endpoint exposure"*. Without it there is no live surface; Option 1F becomes the whole of Slice 4.

- **Objective facts:** D42 §I.4 and D43 §L defer "future endpoint exposure"
  and are not resolved by D42 or D43. No Slice 4 endpoint exists today.
  D40 §B requires separate authorization for Slice 4. D47 does not adopt D44.
- **Requires explicit owner approval:** whether any endpoint may be exposed at
  all, and therefore whether the live surface or Option 1F is in scope.

### AD-2 — Role for the endpoint

> **Role for the endpoint** | `TWIN_READ` versus `TWIN_ANALYZE`, with the consequences in §12.2.2, the corrected factual basis in §12.2.1, and the non-binding recommendation in §12.2.3.

- **Objective facts:** the security chain is default-deny; `TWIN_READ` gates
  deterministic GETs including impact and risk; `TWIN_ANALYZE` gates the
  simulation and policy POSTs; `TWIN_ADMIN` inherits both; no new role,
  `TWIN_AGENT`, or `TWIN_APPROVER` reuse is proposed. The proposal records a
  non-binding preference for `TWIN_ANALYZE` and states that `TWIN_READ` is
  defensible.
- **Requires explicit owner approval:** the chosen role, the reasoning, and
  acceptance of the resulting compute-exposure consequence. The preference is
  not a decision.

### AD-3 — Rate and abuse posture for inference-triggering requests

> **Rate and abuse posture for inference-triggering requests** | See §16.1.1 below.

- **Objective facts:** `LocalModelAdapter` holds a static
  `Semaphore(1, true)` acquired with `tryAcquire(0, ...)`, which bounds
  concurrency only; a failure maps to `BUSY`. No rate limiter exists. The
  proposal lists options R-A through R-E and proposes no numeric threshold.
- **Requires explicit owner approval:** the adopted posture, its accepted
  residual, and any numeric threshold with its basis. No option is selected
  here.

### AD-4 — Response-size ceiling and bounded serialization contract

> **Response-size ceiling and bounded serialization contract** | Whether a concrete numeric ceiling in **bytes** for the serialized HTTP response body is adopted, and the bounded serialization contract that accompanies it. The decision must record: the numeric ceiling and its measurement method; the JSON escaping behaviour and whether model-derived strings are bounded on their escaped form as well as their character form; the per-item evidence metadata budget and whether the projection shape is bounded relative to the packet shape; the response envelope overhead including field names; the per-candidate metadata budget, including repeated limitation strings and repeated `E<n>` reference strings; the candidate count the ceiling is stated against; whether repeated per-candidate metadata is deduplicated or explicitly accepted as repeated; and the testable criterion that fails when the ceiling is exceeded. See §7.7.2–§7.7.4, §14 AC-14 and §16 R12. **Until this decision is recorded, no numeric response bound is claimed by this proposal, and the adopted upstream limits A–D are not treated as such a bound.**

- **Objective facts:** the adopted limits are the 262,144-byte packet ceiling,
  the 65,536-byte raw model response cap, the parser document length
  (characters), and the 1,000-character per-string cap. None bounds the
  serialized HTTP response. The proposal correctly declines to invent a bound.
- **Requires explicit owner approval:** the numeric byte ceiling, the
  measurement method, the escaping behaviour, the metadata and envelope
  budgets, the candidate count, and the deduplication choice. No value is
  supplied here.

### AD-5 — Whether a fixture-only presentation (Option 1F) precedes, accompanies, or replaces the live surface

> **Whether a fixture-only presentation (Option 1F) precedes, accompanies, or replaces the live surface** | Option 1F is strictly smaller on every axis and validates the highest-risk presentation rules at zero backend cost; it does not validate the endpoint, the role, the projection, the real evidence, the rate posture, or the error contract. See §10.3.

- **Objective facts:** Option 1F uses the existing development-only preview
  pattern and adds no HTTP surface, role, or inference reachability. It does
  not exercise the live pipeline. The proposal states both properties.
- **Requires explicit owner approval:** ordering and scope — whether the
  fixture-only step precedes, accompanies, or replaces the live surface.

### AD-6 — Whether rejected candidate statements are displayed

> **Whether rejected candidate statements are displayed** | Default in this proposal: not displayed (§7.3, §7.6 rule 5). Alternative: display them in a clearly separated, non-reviewable section — which increases exposure of text that failed the wording screen.

- **Objective facts:** rejected candidates are those that failed deterministic
  validation, including the wording screen; the proposal's default response
  carries counts by failure class and no statement text. If statements are
  displayed, their cited evidence must be included in the same deduplicated
  evidence collection.
- **Requires explicit owner approval:** display or non-display. The stated
  default is a proposal default, not an adopted decision.

### AD-7 — Durable evidence identity

> **Durable evidence identity** | Whether durable evidence identity is ever to exist, and therefore whether Option 3 can ever be unblocked. Requires its own decision (§10.5).

- **Objective facts:** the packet reference is the constant `P1` and item
  references are positional `E1..En`; `EvidenceReference` is documented as
  transient and non-durable; D38 §B forbids inventing durable IDs where the
  source APIs do not provide them. Option 3 cannot resolve a stored review
  record against a later packet.
- **Requires explicit owner approval:** whether durable evidence identity is
  ever introduced. This assessment does not resolve it.

### AD-8 — Adopted component and naming set

> **Adopted component and naming set** | The endpoint path, package name, and class names as *final* implementation names, including the requirement that machine-side vocabulary must not contain "review" (§5.2). Working names in this document are illustrative only.

- **Objective facts:** all names in the proposal are explicitly working names.
  The proposal states a constraint (machine-side vocabulary must not contain
  "review") and offers non-binding recommendations for a package and path.
- **Requires explicit owner approval:** the final endpoint path, package name,
  and class names. The recommendations are not adopted values.

### AD-9 — Whether the model and version are disclosed to the reviewer

> **Whether the model and version are disclosed to the reviewer** | Requires the model pinning decision (D40 §I) to be resolved first. See §16.3.

- **Objective facts:** D40 §I defers model/version selection and pinning; no
  adopted model identity exists; the proposal therefore cannot display one.
- **Requires explicit owner approval:** disclosure, preceded by resolution of
  the deferred model-pinning decision. No model identity is supplied here.

## 5. Specific boundedness gaps

1. **Missing adopted HTTP response-size ceiling (AD-4 / R12 / AC-14).** The
   adopted limits measure the packet, the raw model response, and parser
   fields; none measures the serialized HTTP response. JSON escaping can
   expand model-derived strings beyond their character count; per-item evidence
   metadata and the response envelope are not measured by any adopted limit;
   per-candidate metadata (limitations, reference strings, category,
   qualifications) repeats across candidates; and the candidate count is
   bounded by generation limits, not by a response limit. The proposal adopts
   no numeric ceiling, and AC-14 is expressly conditional on an owner decision.
2. **Sequential starvation (AD-3 / R10 / T-S4-12).** The existing D41
   single-flight semaphore bounds concurrency only. A single authorized
   principal can issue requests back to back, each holding the permit for up to
   the 35-second inference window, denying other reviewers for as long as it
   continues. The proposal states that no existing mechanism solves this and
   that the semaphore must not be relied upon as the solution. Exposing
   inference to a browser creates this availability surface; no posture or
   threshold is adopted.
3. **Practical generation envelope.** The prompt ceiling is 32,768 UTF-8 bytes
   while the packet ceiling is 262,144 bytes; a large packet fails closed with
   `PROMPT_TOO_LARGE` and no candidates. This is honest and documented, but it
   means the live surface may frequently have nothing to present. The
   `PACKET_TOO_LARGE` (HTTP 400, assembly-time) and `PROMPT_TOO_LARGE`
   (HTTP 200 status, post-assembly) failures must remain distinct (AC-9.3).
4. **Conditional acceptance criteria.** AC-12 and AC-14 cannot be satisfied
   until AD-3 and AD-4 are resolved. A D44 adoption that leaves them deferred
   would adopt criteria that are not yet gating.

## 6. Required steps before the proposal is ready for D44 design adoption

1. Re-base the design on `ca2428cb` (or a later main) and re-verify every
   `[ADOPTED-VERIFIED]` fact against that baseline; update factual claims that
   changed after `9df5721`.
2. Correct the D42/D43 attribution: state explicitly that D42 adopted the
   design only and D43 adopted scope only, that neither authorized the existing
   implementation, and that a PR merge is not authorization.
3. Add the D47 finding and interim hold to the proposal's context and gating
   sections, including the constraint that Slice 4 depends on a Slice 3
   implementation whose authorization cannot currently be verified.
4. Record the outcome of AD-1 through AD-9 at Gate A. Do not treat the
   proposal's defaults, recommendations, or option lists as recorded outcomes.
5. Resolve AD-1 explicitly so that the D42 §I.4 / D43 §L endpoint-exposure
   deferral is no longer open.
6. Adopt a numeric HTTP response-size ceiling and the accompanying bounded
   serialization contract, with measurement method and testable failure
   criterion (AD-4 / AC-14).
7. Adopt a rate and abuse posture, its accepted residual, and any threshold
   basis (AD-3 / AC-12).
8. Confirm the final component and naming set and preserve the constraint that
   machine-side vocabulary must not contain "review" (AD-8).
9. Confirm that AC-1 through AC-14 can be adopted as gating criteria without
   remaining conditional, and that AC-11's no-authority checks hold.
10. Only then submit the revised proposal for D44 design adoption, with D44
    remaining a design-only decision and D45/D46 untouched.

No answer to any unresolved owner decision is provided by this document.

## 7. Reminder — design adoption does not authorize implementation

A D44 design adoption, if it ever occurs, would be a documentation-only
decision. It would not authorize any Slice 4 code, endpoint, controller,
projection, transport DTO, exception mapping, frontend component, role,
configuration, persistence, migration, or test change. Under the proposal's own
Gate B, implementation requires a separate explicit authorization after
adoption, and Gate C requires completion verification and a recorded security
review before the change is treated as complete. Separately, D47 requires a
distinct prospective authorization before any future use or continuation of the
Slice 3 implementation. This assessment authorizes none of those things.
