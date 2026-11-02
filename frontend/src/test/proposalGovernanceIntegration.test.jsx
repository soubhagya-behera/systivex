import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TwinConsole } from '../components/TwinConsole.jsx';

const KEY = '11111111-1111-1111-1111-111111111111';
const EVENT_ID = '22222222-2222-2222-2222-222222222222';

const ENTITIES = [
  {
    id: 'e1',
    type: 'SERVICE',
    name: 'payments-api',
    externalRef: 'repo/apps/payments',
    environment: 'production',
    metadata: {},
  },
];

const RELATIONSHIPS = [];

const PROPOSAL = {
  proposalKey: KEY,
  revisionNumber: 2,
  status: 'AWAITING_APPROVAL',
  title: 'Change payment routing',
  description: 'Route checkout traffic through the new payment service.',
  operations: [{ action: 'UPDATE', target: 'payments-api' }],
  operationsHash: 'sha256:operations',
  assessment: { decision: 'ALLOW' },
  assessmentHash: 'sha256:assessment',
  policyDecision: 'ALLOW',
  policyVersion: '2026-01',
  riskTaxonomy: '2026-01',
  twinFingerprint: 'sha256:twin',
  snapshotObservedAt: '2026-10-01T09:55:00Z',
  proposerIssuer: 'https://issuer.example',
  proposerSubject: 'user-1',
  approverIssuer: null,
  approverSubject: null,
  reason: null,
  expiresAt: null,
  createdAt: '2026-10-01T09:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
};

const REVISION_ONE = {
  ...PROPOSAL,
  revisionNumber: 1,
  status: 'DRAFT',
  title: 'Initial payment routing',
};

const PAGE = { items: [PROPOSAL], page: 0, size: 20, total: 1 };

const AUDIT = {
  id: EVENT_ID,
  seq: 1,
  occurredAt: '2026-10-01T09:01:00Z',
  actorIssuer: 'https://issuer.example',
  actorSubject: 'user-1',
  action: 'CREATED',
  outcome: 'SUCCESS',
  proposalKey: KEY,
  revisionNumber: 1,
  operationsHash: 'sha256:operations',
  assessmentHash: null,
  reason: null,
  correlationId: 'correlation-1',
  prevHash: 'sha256:previous',
  eventHash: 'sha256:event',
};

const ok = (body) => ({ ok: true, status: 200, json: async () => body });

function router(url) {
  if (url === '/api/v1/twin/relationships') return ok(RELATIONSHIPS);
  if (url === '/api/v1/twin/entities') return ok(ENTITIES);
  if (url === '/api/v1/twin/proposals?page=0&size=20') return ok(PAGE);
  if (url === `/api/v1/twin/proposals/${KEY}`) return ok(PROPOSAL);
  if (url === `/api/v1/twin/proposals/${KEY}/revisions`) return ok([REVISION_ONE, PROPOSAL]);
  if (url === `/api/v1/twin/proposals/${KEY}/revisions/1`) return ok(REVISION_ONE);
  if (url === `/api/v1/twin/proposals/${KEY}/audit`) return ok([AUDIT]);
  throw new Error(`unexpected request ${url}`);
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

describe('Proposal Governance navigation', () => {
  it('loads only each approved resource on explicit navigation', async () => {
    const user = userEvent.setup();
    const fetchMock = stubFetch(router);
    render(<TwinConsole token="test-token" onDisconnect={() => {}} />);

    expect(await screen.findByText('payments-api')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);

    await user.click(screen.getByRole('button', { name: /proposals/i }));

    expect(await screen.findByText('Change payment routing')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[2][0]).toBe(
      '/api/v1/twin/proposals?page=0&size=20',
    );
    expect(screen.queryByRole('heading', { name: 'Latest revision' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Change payment routing/i }));

    expect(
      await screen.findByRole('heading', { name: 'Latest revision' }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock.mock.calls[3][0]).toBe(`/api/v1/twin/proposals/${KEY}`);

    await user.click(screen.getByRole('button', { name: /show revision history/i }));

    expect(
      await screen.findByRole('heading', { name: 'Revision history' }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(fetchMock.mock.calls[4][0]).toBe(
      `/api/v1/twin/proposals/${KEY}/revisions`,
    );

    await user.click(screen.getByRole('button', { name: /Revision 1/i }));

    expect(
      await screen.findByRole('heading', { name: 'Revision 1' }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(6);
    expect(fetchMock.mock.calls[5][0]).toBe(
      `/api/v1/twin/proposals/${KEY}/revisions/1`,
    );

    await user.click(screen.getByRole('button', { name: /show audit trail/i }));

    expect(
      await screen.findByRole('heading', { name: 'Audit trail' }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(7);
    expect(fetchMock.mock.calls[6][0]).toBe(`/api/v1/twin/proposals/${KEY}/audit`);

    for (const [url, options] of fetchMock.mock.calls) {
      expect(url.startsWith('/api/')).toBe(true);
      expect(url).not.toContain('test-token');
      expect(options.headers.Authorization).toBe('Bearer test-token');
      if (url.includes('/proposals')) {
        expect(options.method).toBe('GET');
      }
    }
    expect(document.body.textContent).not.toContain('test-token');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('test-token');
  });

  it('hides proposal governance in preview and makes no proposal request', async () => {
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
      screen.queryByRole('button', { name: /proposals/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Proposal Governance')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
