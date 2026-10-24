import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App.jsx';

const ENTITIES = [
  {
    id: 'e1',
    type: 'SERVICE',
    name: 'payments-api',
    externalRef: 'repo/apps/payments',
    environment: 'production',
    metadata: { owner: 'team-payments' },
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

  it('loads the twin after connecting and only attaches the bearer header to /api', async () => {
    const user = userEvent.setup();
    const fetchMock = stubFetch(dataRouter);
    render(<App />);

    await connect(user);

    expect(await screen.findByText('payments-api')).toBeInTheDocument();
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(
      expect.arrayContaining(['/api/v1/twin/entities', '/api/v1/twin/relationships']),
    );
    for (const [url, options] of fetchMock.mock.calls) {
      expect(url.startsWith('/api/')).toBe(true);
      expect(options.headers.Authorization).toBe('Bearer test-token');
    }
  });

  it('opens entity detail with metadata when an entity is selected', async () => {
    const user = userEvent.setup();
    stubFetch(dataRouter);
    render(<App />);

    await connect(user);
    await user.click(await screen.findByRole('button', { name: /payments-api/i }));

    expect(
      await screen.findByRole('heading', { name: 'payments-api' }),
    ).toBeInTheDocument();
    expect(screen.getByText(/team-payments/)).toBeInTheDocument();
  });

  it('renders an empty state when the twin has no data', async () => {
    const user = userEvent.setup();
    stubFetch(() => ok([]));
    render(<App />);

    await connect(user);
    expect((await screen.findAllByText(/no entities/i)).length).toBeGreaterThan(0);
  });

  it('surfaces a safe access-denied message without leaking the token', async () => {
    const user = userEvent.setup();
    stubFetch(() => ({ ok: false, status: 403, json: async () => ({ detail: 'test-token' }) }));
    render(<App />);

    await connect(user);

    expect(await screen.findByRole('alert')).toHaveTextContent(/access denied/i);
    expect(screen.queryByText(/test-token/)).not.toBeInTheDocument();
  });

  it('renders an accessible dependency graph', async () => {
    const user = userEvent.setup();
    stubFetch(dataRouter);
    render(<App />);

    await connect(user);
    await user.click(screen.getByRole('button', { name: /graph/i }));

    expect(
      await screen.findByRole('img', { name: /dependency graph/i }),
    ).toBeInTheDocument();
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
