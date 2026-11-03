import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SimulationPolicy } from '../views/SimulationPolicy.jsx';
import { ApiError, ErrorKind, safeMessage } from '../api/errors.js';
import {
  ENTITIES,
  POLICY_RESPONSE,
  SIMULATION_RESPONSE,
} from './simulationFixtures.js';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

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
  const simulationApi = {
    runSimulation: vi.fn().mockResolvedValue(SIMULATION_RESPONSE),
    evaluatePolicy: vi.fn().mockResolvedValue(POLICY_RESPONSE),
    ...overrides,
  };
  render(<SimulationPolicy entities={ENTITIES} simulationApi={simulationApi} />);
  return simulationApi;
}

function rootSelect() {
  return screen.getByLabelText(/analysis root/i);
}

async function selectRoot(user, id = 'e1') {
  await user.selectOptions(rootSelect(), id);
}

async function addOperation(user, type) {
  await user.selectOptions(screen.getByLabelText(/new operation type/i), type);
  await user.click(screen.getByRole('button', { name: /add operation/i }));
}

function runSimulationButton() {
  return screen.getByRole('button', { name: /^run simulation$/i });
}

function evaluatePolicyButton() {
  return screen.getByRole('button', { name: /^evaluate policy$/i });
}

describe('SimulationPolicy workflow', () => {
  it('sends no request on entry, root selection, or operation editing', async () => {
    const user = userEvent.setup();
    const api = renderView();

    expect(screen.getByText('No simulation yet')).toBeInTheDocument();
    expect(screen.getByText('No policy evaluation yet')).toBeInTheDocument();
    expect(api.runSimulation).not.toHaveBeenCalled();
    expect(api.evaluatePolicy).not.toHaveBeenCalled();
    expect(runSimulationButton()).toBeDisabled();
    expect(evaluatePolicyButton()).toBeDisabled();

    await selectRoot(user);
    await addOperation(user, 'REMOVE_ENTITY');
    await user.type(
      screen.getByLabelText(/entity to remove for operation 1/i),
      'e2',
    );

    expect(api.runSimulation).not.toHaveBeenCalled();
    expect(api.evaluatePolicy).not.toHaveBeenCalled();
    expect(runSimulationButton()).toBeEnabled();
    expect(evaluatePolicyButton()).toBeEnabled();
  });

  it('sends an exact baseline-only request only after Run simulation', async () => {
    const user = userEvent.setup();
    const api = renderView();

    await selectRoot(user);
    await user.click(runSimulationButton());

    expect(api.runSimulation).toHaveBeenCalledTimes(1);
    expect(api.runSimulation).toHaveBeenCalledWith({
      from: ['e1'],
      operations: [],
    });
    expect(api.evaluatePolicy).not.toHaveBeenCalled();
    expect(
      await screen.findByRole('region', { name: /simulation result/i }),
    ).toBeInTheDocument();
  });

  it('serializes all three operation types with canonical camelCase fields', async () => {
    const user = userEvent.setup();
    const api = renderView();

    await selectRoot(user);

    await addOperation(user, 'REMOVE_ENTITY');
    await user.type(
      screen.getByLabelText(/entity to remove for operation 1/i),
      'e2',
    );

    await addOperation(user, 'ADD_RELATIONSHIP');
    await user.type(
      screen.getByLabelText(/source entity for operation 2/i),
      'db/ledger',
    );
    await user.type(
      screen.getByLabelText(/target entity for operation 2/i),
      'api/checkout',
    );

    await addOperation(user, 'REMOVE_RELATIONSHIP');
    await user.type(
      screen.getByLabelText(/source entity for operation 3/i),
      'repo/apps/payments',
    );
    await user.type(
      screen.getByLabelText(/target entity for operation 3/i),
      'db/ledger',
    );
    await user.selectOptions(
      screen.getByLabelText(/relationship type for operation 3/i),
      'READS',
    );

    await user.click(runSimulationButton());

    expect(api.runSimulation).toHaveBeenCalledWith({
      from: ['e1'],
      operations: [
        { type: 'REMOVE_ENTITY', entity: 'e2' },
        {
          type: 'ADD_RELATIONSHIP',
          source: 'db/ledger',
          target: 'api/checkout',
          relationshipType: 'CALLS',
        },
        {
          type: 'REMOVE_RELATIONSHIP',
          source: 'repo/apps/payments',
          target: 'db/ledger',
          relationshipType: 'READS',
        },
      ],
    });
  });

  it('blocks removing the selected analysis root with inline validation', async () => {
    const user = userEvent.setup();
    const api = renderView();

    await selectRoot(user);
    await addOperation(user, 'REMOVE_ENTITY');

    await user.type(
      screen.getByLabelText(/entity to remove for operation 1/i),
      'e1',
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      /removes the selected analysis root/i,
    );
    expect(runSimulationButton()).toBeDisabled();
    expect(evaluatePolicyButton()).toBeDisabled();

    await user.clear(screen.getByLabelText(/entity to remove for operation 1/i));
    await user.type(
      screen.getByLabelText(/entity to remove for operation 1/i),
      'e2',
    );

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(runSimulationButton()).toBeEnabled();
    expect(api.runSimulation).not.toHaveBeenCalled();
  });

  it('enforces the ten-operation frontend usability limit and explains it', async () => {
    const user = userEvent.setup();
    renderView();

    await selectRoot(user);
    for (let index = 0; index < 10; index += 1) {
      await addOperation(user, 'REMOVE_ENTITY');
    }

    expect(screen.getByText('Operations (10/10)')).toBeInTheDocument();
    expect(
      screen.getByText(/frontend usability limit only, not a backend validation or security limit/i),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /add operation/i })).toBeDisabled();
  });
});

describe('SimulationResult presentation', () => {
  it('renders the complete simulation response and separate timestamps', async () => {
    const user = userEvent.setup();
    renderView();

    await selectRoot(user);
    await user.click(runSimulationButton());

    const region = await screen.findByRole('region', {
      name: /simulation result/i,
    });
    expect(within(region).getByText('SIMULATED — NOT EXECUTED')).toBeInTheDocument();
    expect(within(region).getByText('Simulated flag: true')).toBeInTheDocument();
    expect(within(region).getByText('Max depth')).toBeInTheDocument();
    expect(within(region).getByText('Root count')).toBeInTheDocument();
    expect(within(region).getByText('SERVICE, API, DATABASE, TABLE')).toBeInTheDocument();

    expect(within(region).getByText('Baseline entity count')).toBeInTheDocument();
    expect(
      within(region).getByText('Hypothetical entity count'),
    ).toBeInTheDocument();
    expect(
      within(region).getByText('Baseline relationship count'),
    ).toBeInTheDocument();
    expect(
      within(region).getByText('Hypothetical relationship count'),
    ).toBeInTheDocument();
    expect(within(region).getByText('Removed entity count')).toBeInTheDocument();
    expect(within(region).getByText('Added relationship count')).toBeInTheDocument();
    expect(within(region).getByText('Removed relationship count')).toBeInTheDocument();
    expect(
      within(region).getByText(/whole baseline\/hypothetical twin working-set counts/i),
    ).toBeInTheDocument();

    expect(within(region).getByText('Before scope')).toBeInTheDocument();
    expect(within(region).getByText('After scope')).toBeInTheDocument();
    expect(
      within(region).getByText('Before impact snapshot observed at'),
    ).toBeInTheDocument();
    expect(
      within(region).getByText('Before risk snapshot observed at'),
    ).toBeInTheDocument();
    expect(
      within(region).getByText('After impact snapshot observed at'),
    ).toBeInTheDocument();
    expect(
      within(region).getByText('After risk snapshot observed at'),
    ).toBeInTheDocument();
    expect(within(region).getByText('2026-11-03 08:00:00 UTC')).toBeInTheDocument();
    expect(within(region).getByText('2026-11-03 08:00:02 UTC')).toBeInTheDocument();
    expect(within(region).getByText('2026-11-03 08:00:01 UTC')).toBeInTheDocument();
    expect(within(region).getByText('2026-11-03 08:00:03 UTC')).toBeInTheDocument();

    expect(within(region).getAllByText('Truncation reason')).toHaveLength(2);
    expect(within(region).getAllByText('Cycle encountered')).toHaveLength(2);
    expect(within(region).getAllByText('Traversal nodes')).toHaveLength(2);
    expect(within(region).getAllByText('Max starts')).toHaveLength(2);
    expect(
      within(region).getAllByText(/Evidence path 1/i).length,
    ).toBeGreaterThan(0);
    expect(within(region).getAllByText('FORWARD').length).toBeGreaterThan(0);
    expect(within(region).getAllByText('REVERSE').length).toBeGreaterThan(0);
    expect(
      within(region).getAllByText(/repo\/apps\/payments writes db\/ledger/i)
        .length,
    ).toBeGreaterThan(0);

    expect(within(region).getByText('Before/after delta')).toBeInTheDocument();
    expect(within(region).getByText('Before impacted count')).toBeInTheDocument();
    expect(within(region).getByText('After impacted count')).toBeInTheDocument();
    expect(within(region).getByText('Impacted added count')).toBeInTheDocument();
    expect(within(region).getByText('Impacted removed count')).toBeInTheDocument();
    expect(within(region).getByText('Risk added count')).toBeInTheDocument();
    expect(within(region).getByText('Risk removed count')).toBeInTheDocument();
    expect(within(region).getByText('Uncertain before count')).toBeInTheDocument();
    expect(within(region).getByText('Uncertain after count')).toBeInTheDocument();
    expect(within(region).getByText('Truncation changed')).toBeInTheDocument();
    expect(within(region).getAllByText('db/ledger').length).toBeGreaterThan(0);
    expect(
      within(region).getByText(/Risk added\/removed counts are reported without reference lists/i),
    ).toBeInTheDocument();

    const changes = within(region).getByRole('list', {
      name: /risk level changes/i,
    });
    const entries = within(changes).getAllByRole('listitem');
    expect(entries).toHaveLength(2);
    expect(entries[0]).toHaveTextContent('db/ledger');
    expect(entries[0]).toHaveTextContent('LOW -> HIGH');
    expect(entries[1]).toHaveTextContent('api/checkout');
    expect(entries[1]).toHaveTextContent('HIGH -> MEDIUM');

    expect(within(region).getAllByText('risk-v1')).toHaveLength(2);
    expect(within(region).getAllByText('Reasons').length).toBeGreaterThan(0);
    expect(within(region).getAllByText('Uncertain').length).toBeGreaterThan(0);
    expect(within(region).getAllByText('Stale').length).toBeGreaterThan(0);
    expect(
      within(region).getByText(/Hypothetical only: the persisted twin/i),
    ).toBeInTheDocument();
  });
});

describe('PolicyResult presentation', () => {
  it('renders decision, ordered rules, summary, and the bounded-summary limitation', async () => {
    const user = userEvent.setup();
    renderView();

    await selectRoot(user);
    await user.click(evaluatePolicyButton());

    const region = await screen.findByRole('region', {
      name: /policy evaluation result/i,
    });
    expect(
      within(region).getByText(
        'ADVISORY POLICY EVALUATION — NOT AN AUTHORIZATION; NOT EXECUTED',
      ),
    ).toBeInTheDocument();
    expect(within(region).getByText('Decision')).toBeInTheDocument();
    expect(region.querySelector('.policy-decision')).toHaveTextContent('REVIEW');
    expect(within(region).getByText('policy-v1')).toBeInTheDocument();

    const rules = within(region).getByRole('list', {
      name: /policy rules in server order/i,
    });
    const fired = within(rules).getAllByRole('listitem');
    expect(fired).toHaveLength(2);
    expect(fired[0]).toHaveTextContent('RISK_INCREASED');
    expect(fired[1]).toHaveTextContent('UNKNOWN_RISK_PRESENT');
    expect(fired[0]).toHaveTextContent(/Hypothetical risk rises/i);

    expect(within(region).getByText('Twin working-set summary')).toBeInTheDocument();
    expect(within(region).getByText('Before/after delta')).toBeInTheDocument();
    expect(within(region).getByText('Limitations')).toBeInTheDocument();
    expect(
      within(region).getByText(/Advisory policy-v1 decision over a server-computed simulation/i),
    ).toBeInTheDocument();
    expect(
      within(region).getByText(/does not include before\/after scopes or server timestamps/i),
    ).toBeInTheDocument();
    expect(within(region).queryByText('Before scope')).not.toBeInTheDocument();
    expect(
      within(region).queryByText(/snapshot observed at/i),
    ).not.toBeInTheDocument();
  });

  it('shows an explicit empty rules state', async () => {
    const user = userEvent.setup();
    const response = clone(POLICY_RESPONSE);
    response.rulesFired = [];
    renderView({ evaluatePolicy: vi.fn().mockResolvedValue(response) });

    await selectRoot(user);
    await user.click(evaluatePolicyButton());

    const region = await screen.findByRole('region', {
      name: /policy evaluation result/i,
    });
    expect(within(region).getByText('No rules fired')).toBeInTheDocument();
  });

  it('retains simulation and policy results independently', async () => {
    const user = userEvent.setup();
    const api = renderView();

    await selectRoot(user);
    await user.click(runSimulationButton());
    await screen.findByRole('region', { name: /simulation result/i });

    await user.click(evaluatePolicyButton());

    expect(
      await screen.findByRole('region', { name: /policy evaluation result/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('region', { name: /simulation result/i }),
    ).toBeInTheDocument();
    expect(api.runSimulation).toHaveBeenCalledTimes(1);
    expect(api.evaluatePolicy).toHaveBeenCalledTimes(1);
    expect(api.evaluatePolicy).toHaveBeenCalledWith({
      from: ['e1'],
      operations: [],
    });
  });

  it('preserves unknown forward-compatible values and displays them exactly', async () => {
    const user = userEvent.setup();
    const response = clone(POLICY_RESPONSE);
    response.decision = 'ESCALATE';
    response.rulesFired[0].outcome = 'FUTURE_OUTCOME';
    renderView({ evaluatePolicy: vi.fn().mockResolvedValue(response) });

    await selectRoot(user);
    await user.click(evaluatePolicyButton());

    const region = await screen.findByRole('region', {
      name: /policy evaluation result/i,
    });
    expect(within(region).getByText('ESCALATE')).toBeInTheDocument();
    expect(within(region).getByText('FUTURE_OUTCOME')).toBeInTheDocument();
  });
});

describe('SimulationPolicy invalidation and stale responses', () => {
  it('invalidates results when the root or operations change', async () => {
    const user = userEvent.setup();
    renderView();

    await selectRoot(user);
    await user.click(runSimulationButton());
    await screen.findByRole('region', { name: /simulation result/i });

    await selectRoot(user, 'e2');
    expect(
      screen.queryByRole('region', { name: /simulation result/i }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('No simulation yet')).toBeInTheDocument();

    await addOperation(user, 'REMOVE_ENTITY');
    await user.type(
      screen.getByLabelText(/entity to remove for operation 1/i),
      'e3',
    );
    await user.click(evaluatePolicyButton());
    await screen.findByRole('region', { name: /policy evaluation result/i });

    await user.type(
      screen.getByLabelText(/entity to remove for operation 1/i),
      '4',
    );
    expect(
      screen.queryByRole('region', { name: /policy evaluation result/i }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('No policy evaluation yet')).toBeInTheDocument();
  });

  it('does not let an old response overwrite a newer action or invalidation', async () => {
    const user = userEvent.setup();
    const first = deferred();
    const second = deferred();
    const runSimulation = vi
      .fn()
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise);
    renderView({ runSimulation });

    await selectRoot(user, 'e1');
    await user.click(runSimulationButton());
    await waitFor(() => expect(runSimulation).toHaveBeenCalledTimes(1));

    await selectRoot(user, 'e2');
    await user.click(runSimulationButton());
    await waitFor(() => expect(runSimulation).toHaveBeenCalledTimes(2));

    const stale = clone(SIMULATION_RESPONSE);
    stale.advisory = 'SIMULATED — STALE';
    await act(async () => {
      first.resolve(stale);
    });

    expect(screen.queryByText('SIMULATED — STALE')).not.toBeInTheDocument();
    expect(
      screen.getByRole('status', { name: /running hypothetical simulation/i }),
    ).toBeInTheDocument();

    await act(async () => {
      second.resolve(SIMULATION_RESPONSE);
    });

    expect(
      await screen.findByRole('region', { name: /simulation result/i }),
    ).toBeInTheDocument();
    expect(screen.queryByText('SIMULATED — STALE')).not.toBeInTheDocument();
  });
});

describe('SimulationPolicy errors and retries', () => {
  it('retries only the failed action', async () => {
    const user = userEvent.setup();
    const runSimulation = vi
      .fn()
      .mockRejectedValue(new ApiError(ErrorKind.SERVER));
    const evaluatePolicy = vi.fn().mockResolvedValue(POLICY_RESPONSE);
    renderView({ runSimulation, evaluatePolicy });

    await selectRoot(user);
    await user.click(runSimulationButton());

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/server error/i);
    expect(runSimulation).toHaveBeenCalledTimes(1);
    expect(evaluatePolicy).not.toHaveBeenCalled();

    await user.click(within(alert).getByRole('button', { name: /retry/i }));

    await waitFor(() => expect(runSimulation).toHaveBeenCalledTimes(2));
    expect(evaluatePolicy).not.toHaveBeenCalled();
  });

  it.each([
    ['400', ErrorKind.BAD_REQUEST, /invalid \(400\)/i, /server rejected the request shape/i],
    ['401', ErrorKind.UNAUTHORIZED, /authentication required/i, /not authenticated/i],
    ['403', ErrorKind.FORBIDDEN, /access denied/i, /TWIN_ANALYZE/],
    ['404', ErrorKind.NOT_FOUND, /not found \(404\)/i, /no longer resolves/i],
    ['409', ErrorKind.CONFLICT, /conflicts with the current twin state \(409\)/i, /authoritative/i],
    ['503', ErrorKind.SERVER, /server error/i, /Only the failed action/i],
    ['network', ErrorKind.NETWORK, /could not reach/i, /Only the failed action/i],
    ['malformed', ErrorKind.UNEXPECTED, /failed unexpectedly/i, /malformed/i],
    ['no token', ErrorKind.NO_TOKEN, /Enter a bearer token/i, /No authenticated/i],
  ])('handles %s safely with fixed text', async (_label, kind, message, hint) => {
    const user = userEvent.setup();
    const error = new ApiError(kind);
    renderView({ runSimulation: vi.fn().mockRejectedValue(error) });

    await selectRoot(user);
    await user.click(runSimulationButton());

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(message);
    expect(alert).toHaveTextContent(hint);
    expect(alert).toHaveTextContent(safeMessage(kind));
  });
});
