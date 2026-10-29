import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TwinConsole } from '../components/TwinConsole.jsx';

const ENTITIES = [
  {
    id: 'e1',
    type: 'SERVICE',
    name: 'payments-api',
    externalRef: 'repo/apps/payments',
    environment: 'production',
    metadata: {},
  },
  {
    id: 'e2',
    type: 'DATABASE',
    name: 'ledger-db',
    externalRef: 'db/ledger',
    environment: 'production',
    metadata: {},
  },
];

const RELATIONSHIPS = [
  { id: 'r1', sourceId: 'e1', targetId: 'e2', type: 'WRITES' },
];

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
  ],
  maxDepth: 6,
  nodeCount: 2,
  pathCount: 1,
  truncated: false,
  truncationReason: null,
  cycleEncountered: false,
  snapshotObservedAt: '2026-10-28T12:00:00Z',
  entityCount: 2,
  relationshipCount: 1,
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
      level: 'MEDIUM',
      reasons: ['directly connected to the change (depth 1)'],
      uncertain: false,
      stale: false,
    },
  ],
  maxDepth: 6,
  taxonomyVersion: 'risk-v1',
  snapshotObservedAt: '2026-10-28T12:00:02Z',
  entityCount: 2,
  relationshipCount: 1,
  note:
    'Advisory risk classification (taxonomy risk-v1): potentially affected does not mean confirmed breakage.',
};

const ok = (body) => ({ ok: true, status: 200, json: async () => body });

function router(url) {
  if (url.includes('/impact')) return ok(IMPACT);
  if (url.includes('/risk')) return ok(RISK);
  if (url.includes('/relationships')) return ok(RELATIONSHIPS);
  return ok(ENTITIES);
}

function stubFetch(handler) {
  const mock = vi.fn(async (url, options) => handler(url, options));
  vi.stubGlobal('fetch', mock);
  return mock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('Impact & Risk navigation', () => {
  it('sends exactly the two approved analysis GETs only after Analyze', async () => {
    const user = userEvent.setup();
    const fetchMock = stubFetch(router);
    render(<TwinConsole token="test-token" onDisconnect={() => {}} />);

    expect(await screen.findByText('payments-api')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);

    await user.click(screen.getByRole('button', { name: /impact & risk/i }));
    const rootList = screen.getByRole('list', { name: /root entity candidates/i });
    await user.click(
      within(rootList).getByRole('button', { name: /payments-api/i }),
    );

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const analyze = screen.getByRole('button', {
      name: /analyze impact and risk/i,
    });
    expect(analyze).toBeEnabled();

    await user.click(analyze);
    const impact = await screen.findByRole('region', { name: /impact findings/i });
    expect(within(impact).getByText('ledger-db')).toBeInTheDocument();

    expect(fetchMock).toHaveBeenCalledTimes(4);
    const calls = fetchMock.mock.calls.map(([url]) => url);
    expect(
      calls.filter((url) => url.includes('/impact') || url.includes('/risk')),
    ).toEqual([
      '/api/v1/twin/impact?from=e1',
      '/api/v1/twin/risk?from=e1',
    ]);
    for (const [url, options] of fetchMock.mock.calls) {
      expect(url.startsWith('/api/')).toBe(true);
      expect(url).not.toContain('test-token');
      expect(options.headers.Authorization).toBe('Bearer test-token');
    }
    expect(document.body.textContent).not.toContain('test-token');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('test-token');
  });

  it('hides the feature in preview and makes no analysis calls', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(
      <TwinConsole
        preview
        previewData={{
          entities: ENTITIES,
          relationships: RELATIONSHIPS,
          snapshotLoadedAt: Date.now(),
          banner: 'Synthetic preview banner',
        }}
        onDisconnect={() => {}}
      />,
    );

    expect(await screen.findByText('payments-api')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /impact & risk/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Impact & Risk')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /graph/i })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
