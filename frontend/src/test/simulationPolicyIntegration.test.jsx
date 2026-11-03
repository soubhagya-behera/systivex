import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TwinConsole } from '../components/TwinConsole.jsx';
import {
  ENTITIES,
  POLICY_RESPONSE,
  SIMULATION_RESPONSE,
} from './simulationFixtures.js';

const RELATIONSHIPS = [
  {
    id: 'r1',
    sourceId: 'e1',
    targetId: 'e2',
    type: 'WRITES',
    createdAt: '2026-11-01T00:00:00Z',
  },
];

const ok = (body) => ({ ok: true, status: 200, json: async () => body });

function router(url, options) {
  if (url === '/api/v1/twin/relationships') return ok(RELATIONSHIPS);
  if (url === '/api/v1/twin/entities') return ok(ENTITIES);
  if (url === '/api/v1/twin/simulations' && options.method === 'POST') {
    return ok(SIMULATION_RESPONSE);
  }
  if (
    url === '/api/v1/twin/policy-evaluations' &&
    options.method === 'POST'
  ) {
    return ok(POLICY_RESPONSE);
  }
  throw new Error(`unexpected request ${url}`);
}

function stubFetch() {
  const mock = vi.fn(async (url, options = {}) => router(url, options));
  vi.stubGlobal('fetch', mock);
  return mock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('Simulation & Policy console integration', () => {
  it('sends no POST on navigation or root selection and posts only each explicit action', async () => {
    const user = userEvent.setup();
    const fetchMock = stubFetch();
    render(<TwinConsole token="test-token" onDisconnect={() => {}} />);

    expect(await screen.findByText('payments-api')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls.every(([, options]) => options.method !== 'POST')).toBe(
      true,
    );

    await user.click(
      screen.getByRole('button', { name: /simulation & policy/i }),
    );

    expect(
      await screen.findByRole('heading', { name: /simulation & policy/i }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);

    await user.selectOptions(screen.getByLabelText(/analysis root/i), 'e1');
    expect(fetchMock).toHaveBeenCalledTimes(2);

    await user.click(screen.getByRole('button', { name: /^run simulation$/i }));

    expect(
      await screen.findByRole('region', { name: /simulation result/i }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const [simulationUrl, simulationOptions] = fetchMock.mock.calls[2];
    expect(simulationUrl).toBe('/api/v1/twin/simulations');
    expect(simulationOptions.method).toBe('POST');
    expect(simulationOptions.headers.Authorization).toBe('Bearer test-token');
    expect(simulationOptions.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(simulationOptions.body)).toEqual({
      from: ['e1'],
      operations: [],
    });

    await user.click(screen.getByRole('button', { name: /^evaluate policy$/i }));

    expect(
      await screen.findByRole('region', { name: /policy evaluation result/i }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(4);
    const [policyUrl, policyOptions] = fetchMock.mock.calls[3];
    expect(policyUrl).toBe('/api/v1/twin/policy-evaluations');
    expect(policyOptions.method).toBe('POST');
    expect(policyOptions.headers.Authorization).toBe('Bearer test-token');
    expect(policyOptions.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(policyOptions.body)).toEqual({
      from: ['e1'],
      operations: [],
    });

    expect(document.body.textContent).not.toContain('test-token');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('test-token');
  });

  it('hides Simulation & Policy in preview and sends no simulation or policy request', async () => {
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
      screen.queryByRole('button', { name: /simulation & policy/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Simulation & Policy')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
