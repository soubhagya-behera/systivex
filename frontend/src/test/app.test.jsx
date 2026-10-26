import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App.jsx';

const ENTITIES = [
  {
    id: 'e1',
    type: 'SERVICE',
    name: 'payments-api',
    externalRef: 'repo/apps/payments',
    environment: 'production',
    metadata: {
      owner: 'team-payments',
      runtime: {
        managedBy: 'telemetry-connector',
        connectorVersion: 'phase6-v1',
        observedAt: '2026-02-10T09:30:00Z',
        health: { available: true, status: 'UP' },
        counters: { 'checkout.failure': 1, 'checkout.success': 4 },
        limitations: ['limitation-one'],
      },
    },
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-02-01T00:00:00Z',
  },
  {
    id: 'e2',
    type: 'DATABASE',
    name: 'ledger-db',
    externalRef: 'db/ledger',
    environment: 'production',
    metadata: {},
    createdAt: '2026-01-02T00:00:00Z',
    updatedAt: '2026-01-03T00:00:00Z',
  },
];

const RELATIONSHIPS = [
  { id: 'r1', sourceId: 'e1', targetId: 'e2', type: 'WRITES', createdAt: '2026-01-04T00:00:00Z' },
];

const ok = (body) => ({ ok: true, status: 200, json: async () => body });

function stubFetch(handler) {
  const mock = vi.fn(async (url, options) => handler(url, options));
  vi.stubGlobal('fetch', mock);
  return mock;
}

function dataRouter(url) {
  return url.includes('/relationships') ? ok(RELATIONSHIPS) : ok(ENTITIES);
}

async function connect(user) {
  await user.type(screen.getByLabelText(/bearer token/i), 'test-token');
  await user.click(screen.getByRole('button', { name: /^connect$/i }));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('System Twin Explorer', () => {
  it('shows the token gate and sends no request before a token exists', () => {
    const fetchMock = stubFetch(() => ok([]));
    render(<App />);

    expect(
      screen.getByRole('heading', { name: /connect to the control plane/i }),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('does not mark access confirmed before a protected read succeeds', async () => {
    const user = userEvent.setup();
    stubFetch(() => new Promise(() => {}));
    render(<App />);

    await connect(user);

    expect(await screen.findByText('Token supplied')).toBeInTheDocument();
    expect(screen.queryByText('Access confirmed')).not.toBeInTheDocument();
    expect(screen.getByText(/loading snapshot/i)).toBeInTheDocument();
  });

  it('loads the twin after connecting and only attaches the bearer header to /api', async () => {
    const user = userEvent.setup();
    const fetchMock = stubFetch(dataRouter);
    render(<App />);

    await connect(user);

    expect(await screen.findByText('payments-api')).toBeInTheDocument();
    expect(
      screen.getByRole('status', { name: /access confirmed/i }),
    ).toHaveTextContent('Access confirmed');
    expect(await screen.findByText('Snapshot loaded')).toBeInTheDocument();
    expect(screen.getByText(/just now/)).toBeInTheDocument();
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(
      expect.arrayContaining(['/api/v1/twin/entities', '/api/v1/twin/relationships']),
    );
    for (const [url, options] of fetchMock.mock.calls) {
      expect(url.startsWith('/api/')).toBe(true);
      expect(options.headers.Authorization).toBe('Bearer test-token');
    }
  });

  it('opens entity detail with distinct record, snapshot, and observation times', async () => {
    const user = userEvent.setup();
    stubFetch(dataRouter);
    render(<App />);

    await connect(user);
    await user.click(await screen.findByRole('button', { name: /payments-api/i }));

    expect(
      await screen.findByRole('heading', { name: 'payments-api' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Snapshot loaded')).toBeInTheDocument();
    expect(screen.getByText('Twin record updated')).toBeInTheDocument();
    expect(screen.getByText(/2026-02-01 00:00:00 UTC/)).toBeInTheDocument();
    expect(screen.getByText('Observed at')).toBeInTheDocument();
    expect(screen.getByText(/2026-02-10 09:30:00 UTC/)).toBeInTheDocument();
    expect(screen.getByText('telemetry-connector')).toBeInTheDocument();
    expect(screen.getByText('UP')).toBeInTheDocument();
    expect(screen.queryByText(/last sync/i)).not.toBeInTheDocument();

    const raw = screen.getByText('Raw metadata (JSON)');
    expect(raw.closest('details')).not.toHaveAttribute('open');
    await user.click(raw);
    expect(raw.closest('details')).toHaveAttribute('open');
    expect(screen.getByLabelText('Entity metadata (JSON)')).toHaveTextContent(
      'team-payments',
    );
  });

  it('renders an empty state when the twin has no data', async () => {
    const user = userEvent.setup();
    stubFetch(() => ok([]));
    render(<App />);

    await connect(user);
    expect((await screen.findAllByText(/no entities/i)).length).toBeGreaterThan(0);
  });

  it('rejects access without rendering or logging the token or response body', async () => {
    const user = userEvent.setup();
    const consoleSpies = ['log', 'info', 'warn', 'error', 'debug'].map((method) =>
      vi.spyOn(console, method).mockImplementation(() => {}),
    );
    stubFetch(() => ({
      ok: false,
      status: 403,
      json: async () => ({ detail: 'test-token sensitive-response-body' }),
    }));
    render(<App />);

    await connect(user);

    expect(
      await screen.findByRole('status', { name: /access rejected/i }),
    ).toHaveTextContent('Access rejected');
    expect(await screen.findByRole('alert')).toHaveTextContent(/access denied/i);
    expect(document.body.textContent).not.toContain('test-token');
    expect(document.body.textContent).not.toContain('sensitive-response-body');
    for (const spy of consoleSpies) {
      expect(spy).not.toHaveBeenCalled();
    }
  });

  it('reports HTTP 401 as rejected access', async () => {
    const user = userEvent.setup();
    stubFetch(() => ({ ok: false, status: 401, json: async () => ({}) }));
    render(<App />);

    await connect(user);

    expect(
      await screen.findByRole('status', { name: /access rejected/i }),
    ).toHaveTextContent('Access rejected');
    expect(await screen.findByRole('alert')).toHaveTextContent(
      /authentication required/i,
    );
  });

  it('reports network and server failures as an unavailable connection', async () => {
    const networkUser = userEvent.setup();
    stubFetch(() => Promise.reject(new Error('offline')));
    const first = render(<App />);

    await connect(networkUser);
    expect(
      await screen.findByRole('status', { name: /connection unavailable/i }),
    ).toHaveTextContent('Connection unavailable');
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach/i);
    first.unmount();
    vi.unstubAllGlobals();

    const serverUser = userEvent.setup();
    stubFetch(() => ({ ok: false, status: 503, json: async () => ({}) }));
    render(<App />);

    await connect(serverUser);
    expect(
      await screen.findByRole('status', { name: /connection unavailable/i }),
    ).toHaveTextContent('Connection unavailable');
    expect(await screen.findByRole('alert')).toHaveTextContent(/server error/i);
  });

  it('renders a truthful legend, accessible tables, and keyboard graph nodes', async () => {
    const user = userEvent.setup();
    stubFetch(dataRouter);
    render(<App />);

    await connect(user);
    await user.click(screen.getByRole('button', { name: /graph/i }));

    const graph = await screen.findByRole('group', {
      name: /modeled relationship graph/i,
    });
    expect(graph).toHaveAccessibleDescription(
      /relationship lines and labels are visual/i,
    );
    expect(graph.querySelector('.graph-edge')).toHaveAttribute(
      'aria-hidden',
      'true',
    );
    expect(
      screen.getByText(/arrow direction is stored relationship direction/i),
    ).toBeInTheDocument();
    const types = screen.getByRole('list', {
      name: /supported modeled relationship types/i,
    });
    expect(within(types).getAllByRole('listitem')).toHaveLength(8);
    expect(within(types).getByText('CONFIGURES')).toBeInTheDocument();

    await user.click(
      screen.getByText(/node and edge table \(accessible alternative\)/i),
    );
    const entityTable = screen.getByRole('table', {
      name: /entities in this graph/i,
    });
    expect(within(entityTable).getByText('payments-api')).toBeInTheDocument();
    const relationshipTable = screen.getByRole('table', {
      name: /relationships in this graph/i,
    });
    expect(within(relationshipTable).getByText('WRITES')).toBeInTheDocument();
    expect(within(relationshipTable).getByText('ledger-db')).toBeInTheDocument();

    const node = within(graph).getByRole('button', {
      name: /payments-api, service/i,
    });
    node.focus();
    expect(node).toHaveFocus();
    await user.keyboard('{Enter}');
    const heading = await screen.findByRole('heading', { name: 'payments-api' });
    await waitFor(() => expect(heading).toHaveFocus());
  });

  it('never persists the token to browser storage', async () => {
    const user = userEvent.setup();
    stubFetch(dataRouter);
    render(<App />);

    await connect(user);
    await screen.findByText('payments-api');

    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });
});
