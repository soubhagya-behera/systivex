export const ENTITIES = [
  {
    id: 'e1',
    type: 'SERVICE',
    name: 'payments-api',
    externalRef: 'repo/apps/payments',
    environment: 'production',
  },
  {
    id: 'e2',
    type: 'DATABASE',
    name: 'ledger-db',
    externalRef: 'db/ledger',
    environment: 'production',
  },
  {
    id: 'e3',
    type: 'API',
    name: 'checkout-api',
    externalRef: 'api/checkout',
    environment: 'staging',
  },
];

const STARTS = [
  {
    id: 'e1',
    type: 'SERVICE',
    name: 'payments-api',
    externalRef: 'repo/apps/payments',
    environment: 'production',
  },
];

const SETTINGS = {
  maxDepth: 6,
  includeTypes: ['SERVICE', 'API', 'DATABASE', 'TABLE'],
  rootCount: 1,
};

export const GRAPH = {
  baselineEntityCount: 3,
  hypotheticalEntityCount: 2,
  baselineRelationshipCount: 3,
  hypotheticalRelationshipCount: 2,
  removedEntityCount: 1,
  addedRelationshipCount: 0,
  removedRelationshipCount: 1,
};

export const DELTA = {
  beforeImpactedCount: 2,
  afterImpactedCount: 1,
  impactedAddedCount: 0,
  impactedRemovedCount: 1,
  impactedAdded: [],
  impactedRemoved: ['db/ledger'],
  beforeRiskCount: 2,
  afterRiskCount: 1,
  riskAddedCount: 0,
  riskRemovedCount: 1,
  riskLevelChanges: [
    { entityRef: 'db/ledger', before: 'LOW', after: 'HIGH' },
    { entityRef: 'api/checkout', before: 'HIGH', after: 'MEDIUM' },
  ],
  uncertainBeforeCount: 1,
  uncertainAfterCount: 0,
  truncationChanged: false,
};

const LIMITATIONS = [
  'Hypothetical only: the persisted twin and target systems are unchanged.',
  'Policy and risk results are advisory, not authorization or execution evidence.',
];

const IMPACT_BEFORE = {
  startedFrom: STARTS,
  impacted: [
    {
      id: 'e2',
      type: 'DATABASE',
      name: 'ledger-db',
      externalRef: 'db/ledger',
      depth: 1,
      viaStart: 'repo/apps/payments',
    },
    {
      id: 'e3',
      type: 'API',
      name: 'checkout-api',
      externalRef: 'api/checkout',
      depth: 2,
      viaStart: 'repo/apps/payments',
    },
  ],
  paths: [
    {
      targetId: 'e2',
      targetExternalRef: 'db/ledger',
      depth: 1,
      steps: [
        {
          sourceId: 'e1',
          sourceExternalRef: 'repo/apps/payments',
          targetId: 'e2',
          targetExternalRef: 'db/ledger',
          type: 'WRITES',
          direction: 'FORWARD',
        },
      ],
      explanation:
        'repo/apps/payments writes db/ledger: db/ledger is written by repo/apps/payments.',
    },
    {
      targetId: 'e3',
      targetExternalRef: 'api/checkout',
      depth: 2,
      steps: [
        {
          sourceId: 'e1',
          sourceExternalRef: 'repo/apps/payments',
          targetId: 'e3',
          targetExternalRef: 'api/checkout',
          type: 'CALLS',
          direction: 'REVERSE',
        },
      ],
      explanation:
        'repo/apps/payments calls api/checkout: a change may affect api/checkout.',
    },
  ],
  maxDepth: 6,
  nodeCount: 3,
  pathCount: 2,
  truncated: false,
  truncationReason: null,
  cycleEncountered: false,
  snapshotObservedAt: '2026-11-03T08:00:00Z',
  entityCount: 3,
  relationshipCount: 3,
  limits: { maxStarts: 10, maxDepthAllowed: 10, maxNodes: 500, maxPaths: 2000 },
  note:
    'Potentially affected components derived from stored relationships; not confirmed breakage.',
};

const IMPACT_AFTER = {
  ...IMPACT_BEFORE,
  impacted: [IMPACT_BEFORE.impacted[1]],
  paths: [IMPACT_BEFORE.paths[1]],
  nodeCount: 2,
  pathCount: 1,
  snapshotObservedAt: '2026-11-03T08:00:01Z',
  entityCount: 2,
  relationshipCount: 2,
  note:
    'Hypothetical potentially affected components after applying the in-memory proposal.',
};

const RISK_BEFORE = {
  startedFrom: STARTS,
  risks: [
    {
      id: 'e2',
      type: 'DATABASE',
      name: 'ledger-db',
      externalRef: 'db/ledger',
      depth: 1,
      viaStart: 'repo/apps/payments',
      level: 'MEDIUM',
      reasons: ['directly connected to the change (depth 1)'],
      uncertain: false,
      stale: false,
    },
    {
      id: 'e3',
      type: 'API',
      name: 'checkout-api',
      externalRef: 'api/checkout',
      depth: 2,
      viaStart: 'repo/apps/payments',
      level: 'HIGH',
      reasons: ['transitively reachable (depth 2)'],
      uncertain: true,
      stale: true,
    },
  ],
  maxDepth: 6,
  taxonomyVersion: 'risk-v1',
  snapshotObservedAt: '2026-11-03T08:00:02Z',
  entityCount: 3,
  relationshipCount: 3,
  note: 'Advisory risk classification before the hypothetical change.',
};

const RISK_AFTER = {
  ...RISK_BEFORE,
  risks: [
    {
      ...RISK_BEFORE.risks[0],
      level: 'HIGH',
      reasons: ['hypothetical risk raised by the proposal'],
    },
  ],
  snapshotObservedAt: '2026-11-03T08:00:03Z',
  entityCount: 2,
  relationshipCount: 2,
  note: 'Advisory risk classification for the hypothetical working set.',
};

export const SIMULATION_RESPONSE = {
  simulated: true,
  advisory: 'SIMULATED — NOT EXECUTED',
  settings: SETTINGS,
  before: { impact: IMPACT_BEFORE, risk: RISK_BEFORE },
  after: { impact: IMPACT_AFTER, risk: RISK_AFTER },
  graph: GRAPH,
  delta: DELTA,
  limitations: LIMITATIONS,
};

export const POLICY_RESPONSE = {
  decision: 'REVIEW',
  policyVersion: 'policy-v1',
  advisory: 'ADVISORY POLICY EVALUATION — NOT AN AUTHORIZATION; NOT EXECUTED',
  settings: SETTINGS,
  rulesFired: [
    {
      code: 'RISK_INCREASED',
      outcome: 'REVIEW',
      detail: 'Hypothetical risk rises: 1 newly impacted risk appears.',
    },
    {
      code: 'UNKNOWN_RISK_PRESENT',
      outcome: 'REVIEW',
      detail: '1 hypothetical impacted component carries UNKNOWN risk.',
    },
  ],
  simulation: {
    graph: GRAPH,
    delta: DELTA,
    limitations: LIMITATIONS,
  },
  note:
    'Advisory policy-v1 decision over a server-computed simulation; nothing was authorized, approved, executed, or persisted.',
};
