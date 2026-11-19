# D44 Design — Phase 14 S2 Slice 4 Fixture-Only Presentation

**Status: ADOPTED DESIGN — IMPLEMENTATION AUTHORIZED SEPARATELY UNDER D48; NOT
YET PERFORMED.**

D44 design adoption became effective on 2026-11-19 through PR #47, merged as
commit `48279bfdf1107dd5aa33b1863993eb07bd2dcb63`. This document is the adopted
design of record and itself grants no implementation authority. D48 was adopted
on 2026-11-19 to authorize only the D44 Option 1F fixture-only frontend
implementation, subject to all 14 criteria below. No implementation is claimed
complete.

| Field | Value |
| --- | --- |
| Governance baseline | `48279bfdf1107dd5aa33b1863993eb07bd2dcb63` (merge of PR #47, which adopted D44) |
| Adopted via | PR #47, merged 2026-11-19 |
| Source design | `docs/planning/phase-14/PHASE14_S2_SLICE4_PROPOSAL.md` (protected; read-only; unchanged) |
| Adopted option | **Option 1F only** — fixture-only presentation, no endpoint (proposal §10.3) |
| Decision | D44 (adopted design only; no implementation authorization) |
| Governing independent gate | D47 — Slice 3 authorization traceability and interim hold |
| Scope | Frontend development-only presentation rehearsal using synthetic fixtures |

This document adopts only the Option 1F presentation design verified in the
source proposal. It does not invent a substitute design, an API contract, a
numeric threshold, a model identity, or a role change.

## 1. Verification that Option 1F is self-contained

The source proposal establishes Option 1F's independence explicitly:

- §10.3: "No endpoint, no controller, no internal service, no evidence
  projection contract, no backend change of any kind."
- §10.3: "No new HTTP surface, no new role, no inference reachable from a
  browser, no real data rendered."
- §10.3: "Endpoint requirements: **None.** This is the defining property."
- §10.3: "Role and permission requirements: **None**, because the preview is
  development-only and makes no backend request."
- §10.3: "renders the real candidate components with synthetic fixtures in the
  real console" and validates "the wording, the labelling, the four-state
  separation, the accessibility behaviour and the text-only rendering rule".
- §6.6.7: if the live addition is declined, the design "reduces to **Option 1F**
  … which delivers the presentation rules with **no** new HTTP surface, **no**
  new internal service and **no** new authority".

The repository pattern the proposal names exists at the governance baseline:

- `frontend/src/preview/localPreview.js:9-10` gates preview on
  `DEV === true && PROD !== true`.
- `frontend/src/App.jsx:18-19` loads `syntheticTwin.js` through a
  `import.meta.env.DEV`-only dynamic import.
- `frontend/src/preview/syntheticTwin.js:4` states that nothing came from a
  backend, a real environment, or a credential store; `:8` defines
  `PREVIEW_BANNER = 'LOCAL PREVIEW — SYNTHETIC DATA — NOT CONNECTED TO BACKEND'`.
- `frontend/src/test/preview.test.jsx` asserts the DEV gate and banner behavior.

Therefore Option 1F can operate entirely on locally defined synthetic frontend
fixtures. It requires no Slice 3 runtime invocation, no `LocalModelAdapter`
activation, no generation service usage, and no backend API call. There is no
blocker to adopting this design.

## 2. Owner dispositions recorded for D44

These are the nine dispositions proposed for recording in D44. They are design
dispositions only; none authorizes implementation.

| AD | D44 disposition |
| --- | --- |
| AD-1 | Option 1F only. No live endpoint in this Slice 4. |
| AD-2 | Deferred / not applicable without an endpoint. `TWIN_ANALYZE` remains a future non-binding preference, not an adopted role. |
| AD-3 | R-E for this slice: no live inference endpoint. R-B/R-D are not adopted. |
| AD-4 | Deferred: no HTTP generation response exists in fixture-only scope. A numeric serialized-response ceiling is mandatory before any future live endpoint design can be adopted. |
| AD-5 | Option 1F constitutes the entire current Slice 4 scope. A live surface requires a separate future decision. |
| AD-6 | Do not display rejected candidate statement text. Synthetic fixtures may show counts and fixed explanatory labels. |
| AD-7 | No durable evidence identity or persisted review record. Retain only clearly labelled synthetic fixture references. |
| AD-8 | Defer live endpoint, backend package and transport class naming. Preserve the no-"review" machine-side naming constraint. |
| AD-9 | No model/version disclosure in fixture-only scope. Model pinning remains deferred under D40 §I. |

No numeric threshold, model identity, API contract, or role change is created by
these dispositions.

## 3. Adopted scope

### 3.1 Synthetic, locally defined fixture data

- All displayed data is defined locally in the development preview module, in
  the pattern of `frontend/src/preview/syntheticTwin.js`.
- Fixtures are fabricated for presentation rehearsal only. They are not
  produced by `EvidencePacketAssembler`, `EvidenceGenerationService`, a packet,
  a claim validator, a local model, or any backend endpoint.
- Fixture shapes may mirror the proposal's candidate/evidence contract
  (`statement`, `category`, `evidenceReferences`, `claimedQualification`,
  `computedQualification`, `limitations`, deterministic verdict; and evidence
  item `ref`, `category`, `source`, `scope`, `sourceVersion`, `timestamps`,
  `content`, `limitations`, `qualification`, `status`, `includedCount`,
  `omittedCount`) so that presentation rules can be exercised. Mirroring the
  *shape* is not a claim that the content is real.
- Fixture references may use `P<n>` / `E<n>` forms only when explicitly labelled
  as synthetic fixture references. They are never presented as packet identity
  or as durable evidence identifiers (AD-7).

### 3.2 Explicit synthetic-data labelling

- A persistent, non-dismissible banner equivalent to the existing
  `PREVIEW_BANNER` must be visible whenever fixture candidates are displayed,
  stating that the data is synthetic, is not live evidence, and is not connected
  to the backend or a model provider.
- Each fixture candidate and each fixture evidence item must carry a visible
  text label identifying it as synthetic. A colour or icon is not sufficient.
- No wording may imply that a fixture was generated by a real model, validated
  by a live `ClaimValidator`, assembled from real evidence, or reviewed by a
  human.
- The source proposal's non-entailment notice and endorsement-vocabulary
  prohibition (§5.3, §7.1 of the proposal) remain in force: the words
  "approved", "verified", "confirmed", "correct", and "safe" must not be used
  as descriptions of a candidate.

### 3.3 Four-state presentation boundaries

The proposal's four states (§5) are preserved, including the two states that do
not exist in this design:

| State | In fixture-only scope | Representation |
| --- | --- | --- |
| S-A Generated candidate | Illustrative only | Synthetic fixture candidate, labelled synthetic; not a real model result. |
| S-B Deterministic validation verdict | Illustrative only | Fixed, server-authored-style label in the fixture; labelled synthetic; does not establish semantic support. |
| S-C Human-reviewed claim | **Not representable** | No field, control, or label may express that a human reviewed a fixture candidate. |
| S-D Human acceptance / rejection | **Not representable** | No field, control, or label may express acceptance or rejection by a human. |

`SUCCEEDED`-style status language, if used in a fixture, must be rendered as
"deterministic validation completed" semantics and never as "claims accepted"
or "analysis verified". Human review remains mandatory for any real claim and
is not simulated or recorded by this surface.

### 3.4 No automatic acceptance, no review or approval controls

- No control may accept, reject, approve, dismiss, edit, submit, promote, or
  execute a candidate.
- No transition from S-A or S-B to S-C or S-D exists in the data contract or the
  view.
- Fixture candidates are never presented as accepted claims. A candidate that
  would fail deterministic validation is not displayed as a candidate at all
  (see §3.5).
- This surface provides no D29 separation-of-duties guarantee; it records no
  decision and captures no reviewer identity.

### 3.5 No rejected candidate statement display

- The fixture may show total counts and counts by failure class, with fixed
  explanatory labels.
- The statement text, evidence references, and validator detail of a rejected
  candidate are not displayed.
- If a fixture demonstrates a failure class, it does so through a fixed,
  server-authored-style label only, never through the rejected text itself.

### 3.6 No network access

- The fixture-only view makes no HTTP request to the control plane and no
  request of any kind to a model provider.
- It does not import, call, or instantiate `EvidencePacketAssembler`,
  `EvidenceGenerationService`, `EvidenceGenerationPromptBuilder`,
  `StrictClaimResponseParser`, `LocalModelAdapter`, `LocalModelTransport`, or
  any generation/package provider class.
- It does not call `SimulationService`, `PolicyService`, or any other backend
  service through a client module.
- No endpoint, controller, security matcher, role, or HTTP contract is part of
  this design.

### 3.7 No persistence or durable identity

- No browser storage is used: no `localStorage`, `sessionStorage`, IndexedDB,
  cookies, service-worker cache, or URL parameter carries fixture content.
- No review decision, reviewer identity, or candidate state is stored, in the
  browser or on the server.
- No table, entity, repository, Flyway migration, or retention policy is part
  of this design.
- D38's prohibition on inventing durable evidence identifiers is preserved.
  `P1` and positional `E1..En` semantics are not promoted to durable IDs; in
  fixture-only scope they are merely labelled synthetic presentation tokens.

### 3.8 Safe text-only rendering

- Model-influenced and fixture text, including statements, limitations,
  evidence content, names, external references, notes, reasons, and timestamps,
  is rendered as text content only.
- No `dangerouslySetInnerHTML`, `innerHTML`, `eval`, dynamic markup
  construction, or dynamic CSS/URL construction from response or fixture
  content.
- Text-only rendering applies to evidence-side text as well as candidate text,
  consistent with the source proposal's XSS finding.
- The API module that would validate a live response is not needed; fixture
  shape must nonetheless be validated locally so malformed fixture data fails
  loudly rather than rendering as trusted content.

### 3.9 Development-only preview gating

- The fixture view is reachable only through the existing development-only
  preview pattern: `canUsePreview(import.meta.env)` from
  `frontend/src/preview/localPreview.js`, with DEV true and PROD false.
- Fixture data is loaded through a DEV-only dynamic import, so production
  builds cannot serve it.
- Preview is not a backend fake, an identity provider, or an authentication
  bypass.
- If preview is unavailable, no fixture entry point is offered and no fixture
  content is rendered.

### 3.10 Accessibility, loading, empty, and error presentation

Where applicable to a local, synchronous fixture experience:

- Every state (candidate, evidence category, qualification, source status,
  validation verdict, synthetic label) is conveyed by text and programmatic
  name, not by colour or icon alone.
- Controls are keyboard reachable with a visible focus indicator; selecting a
  reference moves focus to its fixture evidence item and announces the
  relationship.
- Status and error changes are announced through a polite live region; reduced
  motion is honoured; text remains readable at 200% zoom and narrow widths.
- Existing `LoadingPanel`, `EmptyPanel`, and `ErrorPanel` patterns from
  `frontend/src/components/StatePanels.jsx` and `useAsyncResource` are reused
  where a loading, empty, or failure state is exercised by a fixture.
- A fixture failure or empty fixture set is never rendered as "no problems
  found"; the failure or absence is explicit.
- Only one fixture set is displayed at a time. There is no comparison,
  history, overlay, or merge of two sets, and no stale payload may remain after
  a new fixture is selected.

## 4. Dispositions that keep D47 independent

D47 remains binding and is not modified or satisfied by this design:

- No Slice 3 runtime invocation or continuation.
- No `LocalModelAdapter` activation for this feature.
- No generation service usage.
- No backend API call.
- No retrospective authorization.
- Any future live use of Slice 3 requires a separate explicit prospective
  authorization decision before that use begins.

The fixture-only UI is entirely disconnected from the generation package and
the provider. This design adopts presentation rules only; it does not authorize
any use of Slice 3.

## 5. Exclusions

This design adopts none of the following: a live endpoint, a controller, an
internal backend service, an evidence projection contract, a transport DTO, an
exception mapping, a role or security matcher, a backend change, a model
provider, a model download or identity, a real evidence packet, persistence, a
migration, a dependency, configuration, proposal mutation, approval, execution,
a second model, tools, RAG, MCP, or a numeric response-size ceiling. D44 was
adopted on main through PR #47; this document is that adopted design, not a
proposal for a live endpoint or for implementation.

## 6. Acceptance criteria for a separately authorized implementation

These are implementation requirements, not claims that tests or code exist.
This document and D44 grant no implementation authority; D48 separately
authorizes implementation of this adopted design, with these criteria as gating
tests. No implementation is claimed complete.

1. **Synthetic data:** all displayed candidates and evidence are defined in a
   local development fixture module; a test asserts that no production module
   imports the fixture module or the fixture view.
2. **Labelling:** a persistent banner states that the data is synthetic, not
   live evidence, and not connected to the backend or a model provider; each
   candidate and evidence item carries a visible synthetic label.
3. **No Slice 3 or provider path:** a static dependency test asserts that the
   fixture view imports no `generation`, `localmodel`, `evidence`, `api/*`,
   `SimulationService`, or `PolicyService` module and contains no inference or
   fetch call.
4. **No network:** a test with a stubbed `fetch` asserts that zero network
   requests occur when the fixture view is opened, switched, or closed.
5. **Four states:** S-A and S-B are illustrated with synthetic labels; tests
   assert that no text, control, or field expresses S-C or S-D.
6. **No acceptance or review action:** tests assert the absence of
   accept/reject/approve/dismiss/submit controls and that no review state can be
   produced.
7. **Rejected statements:** fixture counts and fixed failure-class labels may
   appear; a test asserts that no rejected statement text, evidence reference,
   or validator detail is rendered.
8. **No persistence:** tests assert that no browser storage, URL parameter, or
   server write occurs, and that no durable evidence identifier is created.
9. **Text-only rendering:** adversarial fixture strings containing HTML or
   script are rendered as text; tests assert no `innerHTML`, no
   `dangerouslySetInnerHTML`, and no executable markup.
10. **DEV gating:** tests assert preview is unavailable when DEV is false or
    PROD is true, that no fixture entry point is rendered in that state, and
    that a production build cannot serve fixture data.
11. **Accessibility and states:** keyboard access, focus visibility, live
    region announcements, text labels for every state, reduced motion, and
    loading/empty/error presentation are covered by tests where applicable.
12. **Single fixture set:** tests assert only one fixture set is displayed at a
    time and no stale content survives a switch.
13. **No new surface:** the change set contains no Java, backend, endpoint,
    security, role, schema, migration, dependency, workflow, or configuration
    change.
14. **Regression:** existing frontend tests remain green and unmodified.

## 7. Governance gates

- **D44 design adoption** became effective on 2026-11-19 when PR #47 was merged
  into main (`48279bfdf1107dd5aa33b1863993eb07bd2dcb63`). It is documentation
  only and grants no implementation authority.
- **Implementation** was authorized separately under D48 on 2026-11-19, with the
  §6 criteria as gating tests. D44 itself grants no implementation authority,
  and no implementation is claimed complete.
- **D47** requires a separate prospective authorization before any future use
  or continuation of Slice 3; this design does not provide it.
- **Any future live endpoint** requires a new decision resolving AD-2, AD-3,
  AD-4, AD-8, and AD-9 as applicable, plus a recorded security review. No such
  decision is made here.
