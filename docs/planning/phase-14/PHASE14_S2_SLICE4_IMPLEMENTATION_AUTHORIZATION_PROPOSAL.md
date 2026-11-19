# D48 Proposal — Phase 14 S2 Slice 4 Fixture-Only Implementation Authorization

**Status: PROPOSED — NOT ADOPTED — NO IMPLEMENTATION AUTHORIZATION.**

This is a proposed governance document only. It is not an entry in canonical
`DECISIONS.md`, does not adopt D48, and does not authorize any code, test,
dependency, configuration, endpoint, role, provider, or runtime change.
No implementation may begin on the strength of this document.

| Field | Value |
| --- | --- |
| Date | 2026-11-19 |
| Governance baseline | `48279bfdf1107dd5aa33b1863993eb07bd2dcb63` (PR #47 merge; D44 adopted) |
| Adopted design | D44 — Phase 14 S2 Slice 4 fixture-only design (design adoption only) |
| Design of record | `docs/planning/phase-14/PHASE14_S2_SLICE4_FIXTURE_ONLY_DESIGN.md` |
| Proposed decision | D48 — explicit authorization for D44 Option 1F fixture-only frontend implementation |
| Governing independent gate | D47 — Slice 3 authorization traceability and interim hold |
| Scope | Frontend development-only presentation rehearsal using locally defined synthetic fixtures |

## 1. Purpose

D44 adopted the Option 1F fixture-only presentation design on 2026-11-19
through PR #47. D44 is design-only and authorizes no implementation. This
proposal asks the repository owner to consider a **separate, explicit,
prospective** authorization for exactly that design and nothing more.

Option 1F was verified in the source proposal (§10.3, §6.6.7) and in the
adopted design document (§1) to require no endpoint, no backend request, no
Slice 3 invocation, no provider activation, and no real evidence. The proposed
implementation is a frontend development-only rehearsal of presentation rules
using synthetic fixtures.

## 2. Exact proposed scope

Authorization, if granted, would cover only the following:

1. **Local synthetic fixture data only.** All candidate and evidence data is
   defined locally in a development fixture module, in the pattern of the
   existing `frontend/src/preview/syntheticTwin.js`. It is fabricated content,
   not observed evidence and not a model result.
2. **Existing React + Vite + JavaScript frontend.** The work uses the existing
   console conventions (React, Vite, JavaScript; no TypeScript introduction)
   and existing presentation patterns.
3. **Existing development-only preview pattern.** Preview availability is
   gated by `canUsePreview(import.meta.env)` from
   `frontend/src/preview/localPreview.js` (`DEV === true && PROD !== true`),
   and fixture data is loaded through a DEV-only dynamic import so production
   builds cannot serve it.
4. **Persistent synthetic-data banner.** A non-dismissible banner, equivalent
   to the existing `PREVIEW_BANNER`, states that the data is synthetic, is not
   live evidence, and is not connected to the backend or a model provider.
5. **Visible synthetic labels on each candidate and evidence item.** Every
   rendered candidate and every rendered evidence item carries a text label
   identifying it as synthetic; colour or icon alone is insufficient.
6. **Only illustrative S-A and S-B states.** S-A (generated candidate) and S-B
   (deterministic validation verdict) are illustrated with synthetic fixtures
   and explicit synthetic labels. No fixture is presented as a real model or
   validator result.
7. **S-C and S-D remain unrepresentable.** No field, control, or label may
   express a human-reviewed claim (S-C) or human acceptance/rejection (S-D).
   These states do not exist in the data contract or the view.
8. **No accept/reject/approve/review/submit controls.** No action control of
   any kind appears. Human review remains a process outside this surface.
9. **No rejected candidate statement text.** Synthetic fixtures may show
   counts and fixed explanatory labels by failure class; the statement text,
   evidence references, and validator detail of a rejected candidate are never
   rendered.
10. **No persistence or durable evidence identity.** No browser storage, URL
    parameter, review record, reviewer identity, or durable `P<n>`/`E<n>`
    identity is created. D38's prohibition on invented durable evidence
    identifiers is preserved.

The design boundaries and acceptance criteria remain those of the adopted D44
design document; this proposal does not widen, reinterpret, or weaken them.

## 3. Explicit exclusions

Authorization, if granted, would **not** include any of the following:

- No backend Java changes of any kind.
- No endpoint, controller, DTO, service, security matcher, or role change.
- No Slice 3 invocation or continuation, and no retrospective authorization.
- No `EvidencePacketAssembler` or `EvidenceGenerationService` usage.
- No prompt builder, parser, `LocalModelAdapter`, or provider usage.
- No provider activation or model download.
- No backend requests or model-provider network requests of any kind.
- No browser storage, URL persistence, migrations, dependencies, or schema
  changes.
- No proposal mutation, approval, execution, tools, RAG, or MCP.
- No real evidence packet, no live claim, no model identity disclosure, no
  numeric response-size ceiling, and no live endpoint.

## 4. Mandatory acceptance criteria

All 14 criteria from the adopted D44 fixture-only design §6 are adopted as
gating requirements. Each is stated here as a testable obligation. No criterion
is satisfied by prose or by intent alone.

1. **Synthetic data.** A test asserts all displayed candidates and evidence
   originate in a local development fixture module, and a static test asserts
   no production module imports the fixture module or the fixture view.
2. **Labelling.** A test asserts a persistent banner states the data is
   synthetic, not live evidence, and not connected to the backend or a model
   provider, and a test asserts every candidate and evidence item has a visible
   synthetic text label.
3. **No Slice 3 or provider path.** A static dependency test asserts the
   fixture view imports no `generation`, `localmodel`, `evidence`, `api/*`,
   `SimulationService`, or `PolicyService` module and contains no inference or
   `fetch` call.
4. **No network.** A test with a stubbed `fetch` asserts **zero network
   requests** when the fixture view is opened, when the fixture set is
   switched, and when the view is closed.
5. **Four states.** Tests assert S-A and S-B are illustrated with synthetic
   labels and that no text, control, field, or attribute expresses S-C or S-D.
6. **No acceptance or review action.** Tests assert the absence of
   accept/reject/approve/dismiss/review/submit controls and that no acceptance,
   review, or approval state can be produced.
7. **Rejected statements.** A test asserts fixture counts and fixed
   failure-class labels may appear, while no rejected statement text, evidence
   reference, or validator detail is rendered.
8. **No persistence.** Tests assert no browser storage, URL parameter, or
   server write occurs, and that no durable evidence identifier is created.
9. **Text-only rendering.** Adversarial fixture strings containing HTML,
   script, event handlers, and markup are rendered as text; tests assert no
   `innerHTML`, no `dangerouslySetInnerHTML`, no `eval`, and no executable
   markup in the DOM.
10. **DEV gating and production-build exclusion.** Tests assert preview is
    unavailable when DEV is false or PROD is true, that no fixture entry point
    is rendered in that state, and that a production build contains no fixture
    data or fixture module.
11. **Accessibility and states.** Tests cover keyboard access, visible focus,
    live-region announcements, text labels for every state, reduced motion,
    and loading/empty/error presentation where applicable to a local fixture
    experience; a failure or empty fixture set is never shown as "no problems
    found".
12. **Single fixture set and stale-data clearing.** Tests assert only one
    fixture set is displayed at a time, that selecting a new fixture clears the
    previous payload before the new one renders, and that no stale candidate
    or evidence content survives a switch or close.
13. **No new surface.** A diff-scope check asserts the change set contains no
    Java, backend, endpoint, security, role, schema, migration, dependency,
    workflow, or configuration change.
14. **Regression.** All existing frontend tests remain intact and pass
    unmodified.

The criteria are gating: implementation is not complete if any one fails.

## 5. Verification and review

Before the implementation may be considered complete:

1. **Tests and build.** The full frontend test suite and the production build
   must pass, with the new tests from §4 present and passing.
2. **Static dependency-boundary check.** A documented check must show the
   fixture view has no import path to backend, generation, local-model,
   evidence, or API modules and makes no network call.
3. **Diff review.** A complete diff review must demonstrate that no backend or
   governance boundary was crossed: no endpoint, no Java, no security or role
   change, no persistence, no dependency, no configuration, and no modification
   of protected source proposals.
4. **Independent security review.** A recorded independent security review must
   cover synthetic-data labelling, the four-state boundary, text-only XSS
   handling, DEV-only gating and production exclusion, zero network access, and
   the absence of acceptance/approval affordances.
5. **No premature claim.** No implementation, test, build, or review result may
   be reported as complete until the work and that review have actually been
   performed. This proposal records no such result.

## 6. Explicit governance

- D44 is an **adopted design only**; it authorizes no implementation.
- D48 remains a **proposal** until the repository owner explicitly approves it.
- No implementation may begin before that approval.
- D47 remains independent and unchanged: no Slice 3 runtime invocation or
  continuation, no provider activation for this feature, no generation service
  usage, no backend API call, and no retrospective authorization. Any future
  live use of Slice 3 requires a separate prospective authorization decision
  before use.
- Any future live endpoint requires a new decision resolving AD-2, AD-3, AD-4,
  AD-8, and AD-9 as applicable, together with a recorded security review.
- No live endpoint, role, numeric threshold, model identity, or API contract is
  authorized or created by this proposal.

## 7. Exact approval statement for the repository owner

The repository owner may authorize only this fixture-only scope by recording
the following statement verbatim in the governance record:

> **D48 — Phase 14 S2 Slice 4 Fixture-Only Implementation Authorization.** I
> authorize implementation of only the D44 Option 1F fixture-only frontend
> design, as specified in
> `docs/planning/phase-14/PHASE14_S2_SLICE4_FIXTURE_ONLY_DESIGN.md` and proposed
> in `docs/planning/phase-14/PHASE14_S2_SLICE4_IMPLEMENTATION_AUTHORIZATION_PROPOSAL.md`,
> subject to all 14 acceptance criteria and the verification and review
> requirements in that proposal. This authorization covers locally defined
> synthetic fixtures, the existing development-only preview pattern, synthetic
> labelling, and presentation rules only. It does **not** authorize any backend
> Java change; any endpoint, controller, DTO, service, security matcher, or
> role change; any Slice 3 invocation or continuation; any use of
> `EvidencePacketAssembler`, `EvidenceGenerationService`, prompt builder,
> parser, `LocalModelAdapter`, or provider; any provider activation or model
> download; any backend or provider network request; any browser storage, URL
> persistence, migration, dependency, or schema change; any proposal mutation,
> approval, execution, tool, RAG, or MCP capability; any live endpoint; any
> numeric threshold or model identity; or any persistence or durable evidence
> identity. D47 remains independently binding; any future live use of Slice 3
> requires separate prospective authorization before use.

Until such a statement is recorded, D48 is not adopted and implementation is
not authorized. This document must not be treated as that statement.
