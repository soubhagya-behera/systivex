/**
 * Locally defined fictional fixtures for the D48 Option 1F presentation
 * rehearsal. Nothing here came from a backend, a provider, a real packet, or a
 * real environment. No module in this file imports generation, local-model,
 * evidence, or API code, and no network, storage, or URL API is used.
 *
 * P<n>/E<n> forms are synthetic presentation tokens only. They are not packet
 * identities and are not durable evidence identifiers.
 */

export const FIXTURE_BANNER =
  'LOCAL PREVIEW — SYNTHETIC FIXTURE DATA — NOT LIVE EVIDENCE — NOT CONNECTED TO BACKEND OR MODEL PROVIDER';

export const SYNTHETIC_REFERENCE_NOTICE =
  'Synthetic fixture reference — not a packet identity — not durable evidence id';

const EXCLUDED_NOTICE =
  'Fabricated presentation counts only. No candidate statement, evidence reference, or validator detail is represented.';

const FIXTURE_SETS = [
  {
    id: 'illustrative',
    label: 'Synthetic fixture set — illustrative candidate and evidence presentation',
    banner: FIXTURE_BANNER,
    referenceNotice: SYNTHETIC_REFERENCE_NOTICE,
    candidates: [
      {
        statement:
          'The fictional aurora gateway is recorded as depending on the fictional aurora orders service in this fixture.',
        category: 'OBSERVED_FACT',
        evidenceReferences: ['E1'],
        claimedQualification: 'CURRENT',
        computedQualification: 'CURRENT',
        limitations: [
          'Synthetic presentation fixture; not observed from any real environment.',
        ],
        deterministicVerdict: 'PASSED',
      },
    ],
    evidence: [
      {
        ref: 'E1',
        category: 'OBSERVED_FACT',
        source: 'fixture.twin.relationship',
        scope: 'fictional-aurora-orders',
        sourceVersion: 'fixture-v1',
        timestamps: [{ name: 'observedAt', value: '2026-11-19T09:00:00Z' }],
        content: {
          relationship: 'DEPENDS_ON',
          from: 'fictional-aurora-gateway',
          to: 'fictional-aurora-orders',
        },
        limitations: ['Synthetic fixture content; no backend source.'],
        qualification: 'CURRENT',
        status: 'AVAILABLE',
        includedCount: 1,
        omittedCount: 0,
      },
    ],
    excludedSummary: {
      total: 2,
      byFailureClass: [
        {
          failureClass: 'REFERENCE_EXISTENCE',
          count: 1,
          explanation:
            'Deterministic validation did not present this candidate: a cited reference did not resolve in the fixture.',
        },
        {
          failureClass: 'QUALIFICATION',
          count: 1,
          explanation:
            'Deterministic validation did not present this candidate: the computed qualification differed from the asserted one.',
        },
      ],
      notice: EXCLUDED_NOTICE,
    },
  },
  {
    id: 'adversarial-text',
    label: 'Synthetic fixture set — text rendering rehearsal',
    banner: FIXTURE_BANNER,
    referenceNotice: SYNTHETIC_REFERENCE_NOTICE,
    candidates: [
      {
        statement:
          'Synthetic fixture text: <img src=x onerror="fixture-marker"> and <script>fixture-marker</script> remain text in this rehearsal.',
        category: 'OBSERVED_FACT',
        evidenceReferences: ['E1'],
        claimedQualification: 'CURRENT',
        computedQualification: 'CURRENT',
        limitations: ['Synthetic fixture text; no real source data.'],
        deterministicVerdict: 'PASSED',
      },
    ],
    evidence: [
      {
        ref: 'E1',
        category: 'OBSERVED_FACT',
        source: 'fixture.twin.entity',
        scope: 'fictional-aurora-gateway',
        sourceVersion: 'fixture-v1',
        timestamps: [{ name: 'observedAt', value: '2026-11-19T09:05:00Z' }],
        content: { note: '<b>fixture-marker</b>', label: 'synthetic fixture item' },
        limitations: ['Synthetic fixture content; no backend source.'],
        qualification: 'CURRENT',
        status: 'AVAILABLE',
        includedCount: 1,
        omittedCount: 0,
      },
    ],
    excludedSummary: {
      total: 1,
      byFailureClass: [
        {
          failureClass: 'WORDING',
          count: 1,
          explanation:
            'Deterministic validation did not present this candidate: the fixture text exercises the wording screen.',
        },
      ],
      notice: EXCLUDED_NOTICE,
    },
  },
  {
    id: 'empty',
    label: 'Synthetic fixture set — empty presentation',
    banner: FIXTURE_BANNER,
    referenceNotice: SYNTHETIC_REFERENCE_NOTICE,
    candidates: [],
    evidence: [],
    excludedSummary: {
      total: 0,
      byFailureClass: [],
      notice: EXCLUDED_NOTICE,
    },
  },
];

function deepClone(value) {
  if (Array.isArray(value)) return value.map(deepClone);
  if (value !== null && typeof value === 'object') {
    const copy = {};
    for (const [key, item] of Object.entries(value)) {
      copy[key] = deepClone(item);
    }
    return copy;
  }
  return value;
}

export function loadSyntheticCandidateFixtures() {
  return deepClone(FIXTURE_SETS);
}
