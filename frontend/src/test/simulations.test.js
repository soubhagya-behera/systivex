import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  POLICY_EVALUATIONS,
  SIMULATIONS,
  createSimulationApi,
} from '../api/simulations.js';
import { createApiClient } from '../api/client.js';
import { ApiError, ErrorKind, safeMessage } from '../api/errors.js';
import {
  POLICY_RESPONSE,
  SIMULATION_RESPONSE,
} from './simulationFixtures.js';

const ROOT = '11111111-1111-1111-1111-111111111111';

const REQUESTS = [
  {
    label: 'baseline only',
    request: { from: [ROOT], operations: [] },
  },
  {
    label: 'REMOVE_ENTITY',
    request: {
      from: [ROOT],
      operations: [
        {
          type: 'REMOVE_ENTITY',
          entity: '22222222-2222-2222-2222-222222222222',
        },
      ],
    },
  },
  {
    label: 'ADD_RELATIONSHIP',
    request: {
      from: [ROOT],
      operations: [
        {
          type: 'ADD_RELATIONSHIP',
          source: 'source-ref',
          target: 'target-ref',
          relationshipType: 'CALLS',
        },
      ],
    },
  },
  {
    label: 'REMOVE_RELATIONSHIP',
    request: {
      from: [ROOT],
      operations: [
        {
          type: 'REMOVE_RELATIONSHIP',
          source: 'source-ref',
          target: 'target-ref',
          relationshipType: 'DEPENDS_ON',
        },
      ],
    },
  },
];

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function ok(body) {
  return { ok: true, status: 200, json: async () => body };
}

function failure(status) {
  return {
    ok: false,
    status,
    json: async () => ({ detail: 'test-token sensitive-response-body' }),
  };
}

afterEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('createSimulationApi requests', () => {
  it('uses only the two approved POST paths and exact canonical request JSON', async () => {
    const calls = [];
    const api = createSimulationApi({
      request: async (path, options) => {
        calls.push({ path, options });
        return path === SIMULATIONS ? SIMULATION_RESPONSE : POLICY_RESPONSE;
      },
    });

    for (const { request } of REQUESTS) {
      await api.runSimulation(request);
    }
    await api.evaluatePolicy(REQUESTS[0].request);

    expect(calls).toHaveLength(5);
    for (const [index, call] of calls.entries()) {
      expect(call.options.method).toBe('POST');
      expect(call.options.headers).toEqual({
        'Content-Type': 'application/json',
      });
      const expected =
        index === calls.length - 1
          ? REQUESTS[0].request
          : REQUESTS[index].request;
      expect(JSON.parse(call.options.body)).toEqual(expected);
      expect(call.path).toBe(
        index === calls.length - 1 ? POLICY_EVALUATIONS : SIMULATIONS,
      );
    }

    expect(SIMULATIONS).toBe('/api/v1/twin/simulations');
    expect(POLICY_EVALUATIONS).toBe('/api/v1/twin/policy-evaluations');
  });

  it('uses the shared client bearer and JSON headers without persisting the token', async () => {
    const fetchImpl = vi.fn(async (url) =>
      ok(url.endsWith('/simulations') ? SIMULATION_RESPONSE : POLICY_RESPONSE),
    );
    const api = createSimulationApi(
      createApiClient({ getToken: () => 'test-token', fetchImpl }),
    );

    await api.runSimulation(REQUESTS[0].request);
    await api.evaluatePolicy(REQUESTS[0].request);

    expect(fetchImpl).toHaveBeenCalledTimes(2);
    for (const [url, options] of fetchImpl.mock.calls) {
      expect(url.startsWith('/api/')).toBe(true);
      expect(url).not.toContain('test-token');
      expect(options.headers.Accept).toBe('application/json');
      expect(options.headers.Authorization).toBe('Bearer test-token');
      expect(options.headers['Content-Type']).toBe('application/json');
      expect(options.method).toBe('POST');
    }
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('test-token');
  });

  it('sends no request without a token', async () => {
    const fetchImpl = vi.fn();
    const api = createSimulationApi(
      createApiClient({ getToken: () => null, fetchImpl }),
    );

    await expect(api.runSimulation(REQUESTS[0].request)).rejects.toMatchObject({
      kind: ErrorKind.NO_TOKEN,
    });
    await expect(api.evaluatePolicy(REQUESTS[0].request)).rejects.toMatchObject({
      kind: ErrorKind.NO_TOKEN,
    });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it.each([
    ['HTTP 400', 400, ErrorKind.BAD_REQUEST],
    ['HTTP 401', 401, ErrorKind.UNAUTHORIZED],
    ['HTTP 403', 403, ErrorKind.FORBIDDEN],
    ['HTTP 404', 404, ErrorKind.NOT_FOUND],
    ['HTTP 409', 409, ErrorKind.CONFLICT],
    ['HTTP 500', 500, ErrorKind.SERVER],
    ['HTTP 503', 503, ErrorKind.SERVER],
  ])('maps %s safely', async (_label, status, kind) => {
    const fetchImpl = vi.fn().mockResolvedValue(failure(status));
    const api = createSimulationApi(
      createApiClient({ getToken: () => 'test-token', fetchImpl }),
    );

    const error = await api.runSimulation(REQUESTS[0].request).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe(kind);
    expect(error.status).toBe(status);
    expect(error.message).toBe(safeMessage(kind));
    expect(error.message).not.toContain('test-token');
    expect(error.message).not.toContain('sensitive-response-body');
  });

  it('maps network failure safely', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('offline'));
    const api = createSimulationApi(
      createApiClient({ getToken: () => 'test-token', fetchImpl }),
    );

    const error = await api.runSimulation(REQUESTS[0].request).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe(ErrorKind.NETWORK);
    expect(error.message).toBe(safeMessage(ErrorKind.NETWORK));
  });
});

describe('simulation response validation', () => {
  it('accepts the complete contract and preserves unknown forward-compatible strings', async () => {
    const response = clone(SIMULATION_RESPONSE);
    response.after.impact.impacted[0].type = 'FUTURE_COMPONENT';
    response.after.impact.paths[0].steps[0].type = 'FUTURE_RELATIONSHIP';
    response.after.impact.paths[0].steps[0].direction = 'SIDEWAYS';
    response.after.risk.risks[0].level = 'CRITICAL';
    const api = createSimulationApi({
      request: async () => response,
    });

    await expect(api.runSimulation(REQUESTS[0].request)).resolves.toEqual(response);
  });

  it.each([
    ['null body', null],
    ['array body', []],
    ['missing simulated', (() => {
      const value = clone(SIMULATION_RESPONSE);
      delete value.simulated;
      return value;
    })()],
    ['advisory wrong type', { ...clone(SIMULATION_RESPONSE), advisory: 7 }],
    ['settings missing', (() => {
      const value = clone(SIMULATION_RESPONSE);
      delete value.settings;
      return value;
    })()],
    ['before scope missing', (() => {
      const value = clone(SIMULATION_RESPONSE);
      delete value.before;
      return value;
    })()],
    ['impact paths not an array', (() => {
      const value = clone(SIMULATION_RESPONSE);
      value.before.impact.paths = {};
      return value;
    })()],
    ['impact path steps not an array', (() => {
      const value = clone(SIMULATION_RESPONSE);
      value.before.impact.paths[0].steps = null;
      return value;
    })()],
    ['risk level wrong type', (() => {
      const value = clone(SIMULATION_RESPONSE);
      value.before.risk.risks[0].level = 3;
      return value;
    })()],
    ['graph count wrong type', (() => {
      const value = clone(SIMULATION_RESPONSE);
      value.graph.baselineEntityCount = '3';
      return value;
    })()],
    ['delta changes not an array', (() => {
      const value = clone(SIMULATION_RESPONSE);
      value.delta.riskLevelChanges = {};
      return value;
    })()],
    ['limitations not an array', (() => {
      const value = clone(SIMULATION_RESPONSE);
      value.limitations = 'none';
      return value;
    })()],
  ])('rejects %s as UNEXPECTED without leaking content', async (_label, body) => {
    const api = createSimulationApi({ request: async () => body });

    const error = await api.runSimulation(REQUESTS[0].request).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe(ErrorKind.UNEXPECTED);
    expect(error.message).toBe(safeMessage(ErrorKind.UNEXPECTED));
    expect(error.message).not.toContain('test-token');
    expect(error.message).not.toContain('sensitive-response-body');
  });
});

describe('policy response validation', () => {
  it('accepts the complete bounded contract and unknown decisions', async () => {
    const response = clone(POLICY_RESPONSE);
    response.decision = 'ESCALATE';
    response.rulesFired[0].outcome = 'FUTURE_OUTCOME';
    const api = createSimulationApi({ request: async () => response });

    await expect(api.evaluatePolicy(REQUESTS[0].request)).resolves.toEqual(response);
  });

  it.each([
    ['null body', null],
    ['missing decision', (() => {
      const value = clone(POLICY_RESPONSE);
      delete value.decision;
      return value;
    })()],
    ['policy version wrong type', { ...clone(POLICY_RESPONSE), policyVersion: 1 }],
    ['rulesFired not an array', { ...clone(POLICY_RESPONSE), rulesFired: {} }],
    ['rule missing detail', (() => {
      const value = clone(POLICY_RESPONSE);
      delete value.rulesFired[0].detail;
      return value;
    })()],
    ['simulation missing graph', (() => {
      const value = clone(POLICY_RESPONSE);
      delete value.simulation.graph;
      return value;
    })()],
    ['simulation delta invalid', (() => {
      const value = clone(POLICY_RESPONSE);
      value.simulation.delta.impactedAdded = 'not-a-list';
      return value;
    })()],
    ['note wrong type', { ...clone(POLICY_RESPONSE), note: null }],
  ])('rejects %s as UNEXPECTED', async (_label, body) => {
    const api = createSimulationApi({ request: async () => body });

    const error = await api.evaluatePolicy(REQUESTS[0].request).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe(ErrorKind.UNEXPECTED);
    expect(error.message).not.toContain('sensitive-response-body');
  });
});
