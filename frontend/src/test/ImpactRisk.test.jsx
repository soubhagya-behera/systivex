import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ImpactRisk } from '../views/ImpactRisk.jsx';
import { createAnalysisApi } from '../api/analysis.js';
import { createApiClient } from '../api/client.js';
import { ApiError, ErrorKind, safeMessage } from '../api/errors.js';

const ENTITIES = [
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

const ENTITY_TYPES = ['SERVICE', 'DATABASE', 'API'];

const IMPACT = {
  startedFrom: [
    {
      id: 'e1',
      type: 'SERVICE',
      name: 'payments-api',
      externalRef: 'repo/apps/payments',
      environment: 'production',
    },
  ],
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
        'repo/apps/payments calls api/checkout: a change in repo/apps/payments may affect api/checkout.',
    },
  ],
  maxDepth: 6,
  nodeCount: 3,
  pathCount: 2,
  truncated: false,
  truncationReason: null,
  cycleEncountered: false,
  snapshotObservedAt: '2026-10-28T12:00:00Z',
  entityCount: 10,
  relationshipCount: 12,
  limits: { maxStarts: 10, maxDepthAllowed: 10, maxNodes: 500, maxPaths: 2000 },
  note:
    'Potentially affected components derived from stored twin relationships; downstream dependencies are structural reachability, not confirmed breakage.',
};

const RISK = {
  startedFrom: IMPACT.startedFrom,
  risks: [
    {
      id: 'e2',
      type: 'DATABASE',
      name: 'ledger-db',
      externalRef: 'db/ledger',
      depth: 1,
      viaStart: 'repo/apps/payments',
      level: 'HIGH',
      reasons: [
        'directly connected to the change (depth 1)',
        '5 independent evidence paths reach this component',
      ],
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
      level: 'UNKNOWN',
      reasons: [
        'transitively reachable (depth 2)',
        'runtime evidence stale (older than 30m); severe signals retained',
      ],
      uncertain: true,
      stale: true,
    },
  ],
  maxDepth: 6,
  taxonomyVersion: 'risk-v1',
  snapshotObservedAt: '2026-10-28T12:00:05Z',
  entityCount: 10,
  relationshipCount: 12,
  note:
    'Advisory risk classification (taxonomy risk-v1): potentially affected does not mean confirmed breakage.',
};

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function renderView(overrides = {}) {
  const analysisApi = {
    getImpact: vi.fn().mockResolvedValue(IMPACT),
    getRisk: vi.fn().mockResolvedValue(RISK),
    ...overrides,
  };
  render(
    <ImpactRisk
      entities={ENTITIES}
      entityTypes={ENTITY_TYPES}
      analysisApi={analysisApi}
    />,
  );
  return analysisApi;
}

function rootList() {
  return screen.getByRole('list', { name: /root entity candidates/i });
}

async function selectRoot(user, name) {
  await user.click(within(rootList()).getByRole('button', { name: new RegExp(name, 'i') }));
}

function analyzeButton() {
  return screen.getByRole('button', { name: /analyze impact and risk/i });
}

async function analyze(user) {
  await user.click(analyzeButton());
}

describe('ImpactRisk root picker', () => {
  it('filters roots and enables Analyze without issuing requests', async () => {
    const user = userEvent.setup();
    const api = renderView();

    expect(analyzeButton()).toBeDisabled();
    expect(screen.getByText('No analysis yet')).toBeInTheDocument();

    await user.type(screen.getByLabelText(/search root entities/i), 'ledger');
    expect(
      within(rootList()).queryByRole('button', { name: /payments-api/i }),
    ).not.toBeInTheDocument();
    expect(
      within(rootList()).getByRole('button', { name: /ledger-db/i }),
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/search root entities/i));
    const filter = screen.getByRole('group', {
      name: /filter root entities by type/i,
    });
    await user.click(within(filter).getByRole('button', { name: 'DATABASE' }));
    expect(
      within(rootList()).queryByRole('button', { name: /payments-api/i }),
    ).not.toBeInTheDocument();

    await selectRoot(user, 'ledger-db');

    expect(analyzeButton()).toBeEnabled();
    expect(screen.getByText('Selected root')).toBeInTheDocument();
    expect(api.getImpact).not.toHaveBeenCalled();
    expect(api.getRisk).not.toHaveBeenCalled();
  });

  it('clears results and selection when the root changes', async () => {
    const user = userEvent.setup();
    const api = renderView();

    await selectRoot(user, 'payments-api');
    await analyze(user);
    expect(
      await screen.findByRole('region', { name: /impact findings/i }),
    ).toBeInTheDocument();
    expect(api.getImpact).toHaveBeenCalledTimes(1);
    expect(api.getRisk).toHaveBeenCalledTimes(1);

    await selectRoot(user, 'ledger-db');

    expect(
      screen.queryByRole('region', { name: /impact findings/i }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('No analysis yet')).toBeInTheDocument();
    expect(analyzeButton()).toBeEnabled();
    expect(api.getImpact).toHaveBeenCalledTimes(1);
    expect(api.getRisk).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: /clear selection/i }));

    expect(analyzeButton()).toBeDisabled();
    expect(screen.queryByText('Selected root')).not.toBeInTheDocument();
  });
});

describe('ImpactRisk results', () => {
  it('renders impact entities, evidence paths, directions, and separate summary metadata', async () => {
    const user = userEvent.setup();
    renderView();

    await selectRoot(user, 'payments-api');
    await analyze(user);

    const impact = await screen.findByRole('region', { name: /impact findings/i });
    expect(within(impact).getByText('ledger-db')).toBeInTheDocument();
    expect(within(impact).getByText('checkout-api')).toBeInTheDocument();
    expect(within(impact).getByText('db/ledger')).toBeInTheDocument();
    expect(within(impact).getByText('api/checkout')).toBeInTheDocument();
    expect(within(impact).getAllByText('repo/apps/payments').length).toBeGreaterThan(0);
    expect(within(impact).getByText('Impact computed at')).toBeInTheDocument();
    expect(within(impact).getByText('2026-10-28 12:00:00 UTC')).toBeInTheDocument();
    expect(within(impact).getByText('Twin entities read')).toBeInTheDocument();
    expect(within(impact).getByText('Twin relationships read')).toBeInTheDocument();
    expect(within(impact).getByText('No truncation reported')).toBeInTheDocument();
    expect(
      within(impact).getByText(/no completeness is implied/i),
    ).toBeInTheDocument();
    expect(within(impact).getByText('No cycle reported')).toBeInTheDocument();
    expect(within(impact).getByText(/nodes 500/i)).toBeInTheDocument();
    expect(within(impact).getByText(/paths 2000/i)).toBeInTheDocument();
    expect(
      within(impact).getByText(/not confirmed breakage/i),
    ).toBeInTheDocument();
    expect(within(impact).getByText('Analysis starts (1)')).toBeInTheDocument();

    const ledgerEvidence = within(impact).getByRole('button', {
      name: /show evidence paths for ledger-db/i,
    });
    expect(ledgerEvidence).toHaveAttribute('aria-expanded', 'false');
    expect(
      within(impact).queryByText(/repo\/apps\/payments writes db\/ledger/i),
    ).not.toBeInTheDocument();

    await user.click(ledgerEvidence);
    expect(ledgerEvidence).toHaveAttribute('aria-expanded', 'true');
    expect(
      within(impact).getByText(/repo\/apps\/payments writes db\/ledger/i),
    ).toBeInTheDocument();
    const firstSteps = within(impact).getByRole('list', {
      name: /ordered steps for evidence path 1/i,
    });
    expect(within(firstSteps).getAllByRole('listitem')).toHaveLength(1);
    expect(within(firstSteps).getByText('Writes')).toBeInTheDocument();
    expect(within(firstSteps).getByText('FORWARD')).toBeInTheDocument();
    expect(
      within(impact).getByText(/follows the stored source to target direction/i),
    ).toBeInTheDocument();

    const checkoutEvidence = within(impact).getByRole('button', {
      name: /show evidence paths for checkout-api/i,
    });
    checkoutEvidence.focus();
    await user.keyboard('{Enter}');
    expect(checkoutEvidence).toHaveAttribute('aria-expanded', 'true');
    const checkoutDisclosure = checkoutEvidence.closest('.analysis-disclosure');
    const checkoutStep = checkoutDisclosure.querySelector('.analysis-step__meta');
    expect(within(checkoutStep).getByText('REVERSE')).toBeInTheDocument();
    expect(
      within(checkoutDisclosure).getByText(/walks against the stored direction/i),
    ).toBeInTheDocument();
    expect(
      within(checkoutDisclosure).getByText(/repo\/apps\/payments calls api\/checkout/i),
    ).toBeInTheDocument();

    const risk = screen.getByRole('region', { name: /risk findings/i });
    expect(within(risk).getByText('Risk computed at')).toBeInTheDocument();
    expect(within(risk).getByText('2026-10-28 12:00:05 UTC')).toBeInTheDocument();
    expect(within(risk).getByText('risk-v1')).toBeInTheDocument();
    expect(within(risk).getByText(/no truncation or cycle fields/i)).toBeInTheDocument();
    expect(
      within(risk).getByText(/older than 30 minutes, or absent for a service/i),
    ).toBeInTheDocument();

    const risks = within(risk).getByRole('list', {
      name: /risk entries in backend severity order/i,
    });
    const entries = [...risks.children];
    expect(entries).toHaveLength(2);
    expect(entries[0]).toHaveTextContent('ledger-db');
    expect(entries[0]).toHaveTextContent('HIGH');
    expect(entries[1]).toHaveTextContent('checkout-api');
    expect(entries[1]).toHaveTextContent('UNKNOWN');
    expect(
      within(entries[0]).getByText('directly connected to the change (depth 1)'),
    ).toBeInTheDocument();
    expect(
      within(entries[1]).getByText(/runtime evidence stale/i),
    ).toBeInTheDocument();
    expect(
      within(entries[0]).getByText('Uncertain').closest('.analysis-fact'),
    ).toHaveTextContent('No');
    expect(
      within(entries[0]).getByText('Stale').closest('.analysis-fact'),
    ).toHaveTextContent('No');
    expect(
      within(entries[1]).getByText('Uncertain').closest('.analysis-fact'),
    ).toHaveTextContent('Yes');
    expect(
      within(entries[1]).getByText('Stale').closest('.analysis-fact'),
    ).toHaveTextContent('Yes');
    expect(within(entries[1]).getAllByRole('listitem')).toHaveLength(2);
  });

  it('shows truncation, cycle, limits, and empty result states', async () => {
    const user = userEvent.setup();
    const emptyImpact = {
      ...IMPACT,
      startedFrom: [],
      impacted: [],
      paths: [],
      nodeCount: 1,
      pathCount: 0,
      truncated: true,
      truncationReason: 'path-cap',
      cycleEncountered: true,
    };
    const emptyRisk = {
      ...RISK,
      startedFrom: [],
      risks: [],
    };
    renderView({
      getImpact: vi.fn().mockResolvedValue(emptyImpact),
      getRisk: vi.fn().mockResolvedValue(emptyRisk),
    });

    await selectRoot(user, 'payments-api');
    await analyze(user);

    const impact = await screen.findByRole('region', { name: /impact findings/i });
    expect(within(impact).getByText('Truncated')).toBeInTheDocument();
    expect(
      within(impact).getByText(/evidence path cap reached/i),
    ).toBeInTheDocument();
    expect(within(impact).getByText('Cycle encountered')).toBeInTheDocument();
    expect(within(impact).getByText('No impacted entities')).toBeInTheDocument();
    expect(within(impact).getByText(/no analysis starts/i)).toBeInTheDocument();

    const risk = screen.getByRole('region', { name: /risk findings/i });
    expect(within(risk).getByText('No risk entries')).toBeInTheDocument();
  });
});

describe('ImpactRisk async states', () => {
  it('shows no partial result while either analysis is still loading', async () => {
    const user = userEvent.setup();
    const impact = deferred();
    const risk = deferred();
    renderView({
      getImpact: vi.fn(() => impact.promise),
      getRisk: vi.fn(() => risk.promise),
    });

    await selectRoot(user, 'payments-api');
    await analyze(user);

    expect(
      await screen.findByRole('status', {
        name: /running impact and risk analysis/i,
      }),
    ).toBeInTheDocument();

    await act(async () => {
      impact.resolve(IMPACT);
    });

    expect(
      screen.getByRole('status', { name: /running impact and risk analysis/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('region', { name: /impact findings/i }),
    ).not.toBeInTheDocument();

    await act(async () => {
      risk.resolve(RISK);
    });

    const impactRegion = await screen.findByRole('region', {
      name: /impact findings/i,
    });
    expect(within(impactRegion).getByText('ledger-db')).toBeInTheDocument();
  });

  it('presents an all-or-nothing error and repeats both requests on retry', async () => {
    const user = userEvent.setup();
    const getImpact = vi.fn().mockResolvedValue(IMPACT);
    const getRisk = vi.fn().mockRejectedValue(new ApiError(ErrorKind.SERVER));
    renderView({ getImpact, getRisk });

    await selectRoot(user, 'payments-api');
    await analyze(user);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/server error/i);
    expect(alert).toHaveTextContent(/both analyses must succeed/i);
    expect(
      screen.queryByRole('region', { name: /impact findings/i }),
    ).not.toBeInTheDocument();
    expect(getImpact).toHaveBeenCalledTimes(1);
    expect(getRisk).toHaveBeenCalledTimes(1);

    await user.click(within(alert).getByRole('button', { name: /retry/i }));

    await waitFor(() => expect(getImpact).toHaveBeenCalledTimes(2));
    expect(getRisk).toHaveBeenCalledTimes(2);
  });

  it('ignores an older result after the selected root changes', async () => {
    const user = userEvent.setup();
    const impacts = [];
    const risks = [];
    renderView({
      getImpact: vi.fn(() => {
        const request = deferred();
        impacts.push(request);
        return request.promise;
      }),
      getRisk: vi.fn(() => {
        const request = deferred();
        risks.push(request);
        return request.promise;
      }),
    });

    await selectRoot(user, 'payments-api');
    await analyze(user);
    await waitFor(() => expect(impacts).toHaveLength(1));

    await selectRoot(user, 'ledger-db');
    await analyze(user);
    await waitFor(() => expect(impacts).toHaveLength(2));

    await act(async () => {
      impacts[0].resolve({
        ...IMPACT,
        impacted: [{ ...IMPACT.impacted[0], name: 'stale-impact' }],
        paths: [],
      });
      risks[0].resolve(RISK);
    });

    expect(screen.queryByText('stale-impact')).not.toBeInTheDocument();
    expect(
      screen.getByRole('status', { name: /running impact and risk analysis/i }),
    ).toBeInTheDocument();

    await act(async () => {
      impacts[1].resolve({
        ...IMPACT,
        impacted: [{ ...IMPACT.impacted[0], name: 'fresh-impact' }],
        paths: [],
      });
      risks[1].resolve(RISK);
    });

    expect(await screen.findByText('fresh-impact')).toBeInTheDocument();
    expect(screen.queryByText('stale-impact')).not.toBeInTheDocument();
  });
});

describe('ImpactRisk error handling', () => {
  const failure = (status) => ({
    ok: false,
    status,
    json: async () => ({ detail: 'test-token sensitive-response-body' }),
  });
  const malformed = { ok: true, status: 200, json: async () => ({}) };

  it.each([
    ['HTTP 400', () => failure(400), /invalid \(400\)/i, /selected root uuid/i],
    ['HTTP 401', () => failure(401), /authentication required/i, /both analyses/i],
    ['HTTP 403', () => failure(403), /access denied/i, /both analyses/i],
    ['HTTP 404', () => failure(404), /not found \(404\)/i, /may no longer exist/i],
    ['HTTP 503', () => failure(503), /server error/i, /both analyses/i],
    [
      'network failure',
      () => Promise.reject(new Error('offline')),
      /could not reach/i,
      /both analyses/i,
    ],
    ['malformed response', () => malformed, /failed unexpectedly/i, /malformed/i],
  ])('handles %s safely', async (_label, responder, message, hint) => {
    const user = userEvent.setup();
    const fetchImpl = vi.fn(responder);
    const analysisApi = createAnalysisApi(
      createApiClient({ getToken: () => 'test-token', fetchImpl }),
    );
    render(
      <ImpactRisk
        entities={ENTITIES}
        entityTypes={ENTITY_TYPES}
        analysisApi={analysisApi}
      />,
    );

    await selectRoot(user, 'payments-api');
    await analyze(user);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(message);
    expect(alert).toHaveTextContent(hint);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    expect(document.body.textContent).not.toContain('test-token');
    expect(document.body.textContent).not.toContain('sensitive-response-body');
  });

  it('sends no request without a token and reports the safe no-token error', async () => {
    const user = userEvent.setup();
    const fetchImpl = vi.fn();
    const analysisApi = createAnalysisApi(
      createApiClient({ getToken: () => null, fetchImpl }),
    );
    render(
      <ImpactRisk
        entities={ENTITIES}
        entityTypes={ENTITY_TYPES}
        analysisApi={analysisApi}
      />,
    );

    await selectRoot(user, 'payments-api');
    await analyze(user);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      safeMessage(ErrorKind.NO_TOKEN),
    );
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});
