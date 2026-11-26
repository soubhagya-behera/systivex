# D48 Independent Security Review — Option 1F Fixture-Only Presentation

**Review date:** 2026-11-26  
**Reviewer:** ChatGPT — independent source-level reviewer  
**Review result:** No blocking security finding identified within the authorized D48 scope.

## Review scope and evidence

The review independently examined the supplied D48 source evidence packet, the complete candidate fixture and presentation source excerpts, the static-boundary and presentation tests, and the subsequent final-hardening diff and test report.

The final-hardening changes reviewed include:
- `frontend/src/App.jsx`
- `frontend/src/components/TokenGate.jsx`
- `frontend/src/test/candidateImportFailure.test.jsx`
- `frontend/src/test/candidateProductionBuild.test.js`

The fixture-only implementation reviewed includes:
- `frontend/src/preview/syntheticCandidates.js`
- `frontend/src/views/CandidatePresentation.jsx`
- `frontend/src/components/CandidateCard.jsx`
- `frontend/src/components/CandidateEvidence.jsx`
- `frontend/src/components/CandidateExcludedSummary.jsx`
- `frontend/src/styles/fixture.css`
- related candidate fixture, presentation, boundary, and production-build tests.

## Findings

1. **Synthetic-only data:** Fixture records are locally defined literals and are explicitly labelled synthetic. The reviewed fixture module does not import backend, generation, provider, or evidence services.

2. **Dependency and production boundary:** The candidate presentation's static import graph is limited to React, AppShell, the three candidate presentation components, and fixture CSS. The candidate loader retains the existing `canUsePreview` gate and the explicit DEV check before dynamic imports. The reported production-build exclusion test scans built output for fixture strings, component identifiers, CSS classes, and the entry control.

3. **No network or persistence path:** The reviewed fixture and presentation source contains no fetch, XMLHttpRequest, WebSocket, browser storage, cookie, history mutation, or URL persistence path. The runtime tests also report no fetch or storage/history side effects during preview use.

4. **Text-only rendering:** Candidate and evidence values are rendered as JSX text. Evidence content is serialized with `JSON.stringify` inside a code element. The reviewed source contains no `innerHTML`, `dangerouslySetInnerHTML`, `eval`, or dynamic HTML insertion. An adversarial fixture test checks that HTML/script-like strings remain text.

5. **Four-state and decision boundary:** Only illustrative S-A candidate and S-B deterministic-verdict presentation is represented. S-C human review and S-D acceptance/rejection are not modelled. The reviewed surface contains no accept, reject, approve, dismiss, edit, submit, promote, or execute affordance. Excluded summaries display only aggregate counts and fixed explanatory fields rather than rejected candidate text or validator details.

6. **Persistent notices and labels:** The synthetic-data banner, non-entailment notice, and candidate/evidence synthetic labels are rendered without dismiss controls.

7. **Import-failure handling:** The final hardening catches dynamic import failures, clears any candidate preview state, exposes only a generic text-only `role="alert"` message, and resets the loading state in `finally`. The new test exercises failure, alert announcement, retry availability, and subsequent successful loading. No exception details are exposed to the user.

8. **Temporary build-output cleanup:** The production-build test now checks that its unique output path is absent before allocating it, sets its ownership guard before invoking the build process, and removes only that exact allocated path in `finally`. Its additional test checks that the cleanup helper respects the ownership guard.

9. **Governance and scope:** The reviewed implementation does not add backend, provider, Slice 3, API, security-role, persistence, dependency, configuration, or workflow capabilities. D47 remains independently binding. This review grants no new authority.

## Review conclusion

No blocking security finding was identified in the reviewed D48 Option 1F implementation and final-hardening changes. The reviewed source and reported automated checks support the bounded fixture-only scope authorized by D48.

## Review limitations

- This is an independent source-level review based on the supplied source packet and final-hardening diff. The reviewer did not have direct access to the developer's local filesystem or execute the local test/build commands.
- Test counts and build success are recorded from the maintainer/OpenCode final verification report, not represented as independently executed by this reviewer.
- The reported browser accessibility checks cover responsive layout, keyboard/focus behavior, reduced-motion behavior, and 200% zoom. A manual NVDA or Windows Narrator listening session was not performed and is not claimed here.
- The static boundary test uses source-pattern inspection; the review also considered the actual listed import graph and the reported production-build scan.

This review is limited to D48 Option 1F fixture-only presentation. It does not authorize live evidence, provider use, Slice 3 invocation, backend changes, or any proposal approval or execution capability.
