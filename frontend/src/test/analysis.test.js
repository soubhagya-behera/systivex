import { afterEach, describe, expect, it, vi } from 'vitest';
import { createAnalysisApi } from '../api/analysis.js';
import { createApiClient } from '../api/client.js';
import { ApiError, ErrorKind } from '../api/errors.js';

const FROM = '11111111-1111-1111-1111-111111111111';

const IMPACT = {
  startedFrom: [],
  impacted: [],
  paths: [],
};

const RISK = {
  startedFrom: [],
  risks: [],
};

function ok(body) {
  return { ok: true, status: 200, json: async () => body };
}

function responseFor(path) {
  return path.includes('/impact') ? IMPACT : RISK;
}

afterEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('createAnalysisApi', () => {
  it('calls only the two read-only analysis endpoints with one from parameter', async () => {
    const calls = [];
    const api = createAnalysisApi({
      request: async (path) => {
        calls.push(path);
        return responseFor(path);
      },
    });

    await api.getImpact({ from: FROM });
    await api.getRisk({ from: FROM });

    expect(calls).toEqual([
      `/api/v1/twin/impact?from=${FROM}`,
      `/api/v1/twin/risk?from=${FROM}`,
    ]);

    for (const call of calls) {
      const url = new URL(call, 'http://localhost');
      expect([...url.searchParams.keys()]).toEqual(['from']);
      expect(url.searchParams.getAll('from')).toEqual([FROM]);
    }
  });

  it('encodes from with URLSearchParams and adds no other parameters', async () => {
    const calls = [];
    const from = 'target-service:order service';
    const api = createAnalysisApi({
      request: async (path) => {
        calls.push(path);
        return IMPACT;
      },
    });

    await api.getImpact({ from });

    expect(calls[0]).toBe(
      '/api/v1/twin/impact?from=target-service%3Aorder+service',
    );
    const url = new URL(calls[0], 'http://localhost');
    expect(url.searchParams.getAll('from')).toEqual([from]);
    expect([...url.searchParams.keys()]).toEqual(['from']);
  });

  it('accepts the verified minimum response shapes', async () => {
    const api = createAnalysisApi({ request: async (path) => responseFor(path) });

    await expect(api.getImpact({ from: FROM })).resolves.toEqual(IMPACT);
    await expect(api.getRisk({ from: FROM })).resolves.toEqual(RISK);
  });

  it('uses the central client bearer behavior and local /api paths', async () => {
    const fetchImpl = vi.fn(async (url) => ok(responseFor(url)));
    const api = createAnalysisApi(
      createApiClient({ getToken: () => 'test-token', fetchImpl }),
    );

    await api.getImpact({ from: FROM });
    await api.getRisk({ from: FROM });

    expect(fetchImpl).toHaveBeenCalledTimes(2);
    for (const [url, options] of fetchImpl.mock.calls) {
      expect(url.startsWith('/api/')).toBe(true);
      expect(url).not.toContain('test-token');
      expect(options.headers.Accept).toBe('application/json');
      expect(options.headers.Authorization).toBe('Bearer test-token');
    }
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('test-token');
  });

  it('sends no request when no token is supplied', async () => {
    const fetchImpl = vi.fn();
    const api = createAnalysisApi(
      createApiClient({ getToken: () => null, fetchImpl }),
    );

    await expect(api.getImpact({ from: FROM })).rejects.toMatchObject({
      kind: ErrorKind.NO_TOKEN,
    });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it.each([
    ['impact response not an object', 'getImpact', null],
    ['impact response is an array', 'getImpact', []],
    ['impact missing startedFrom', 'getImpact', { impacted: [], paths: [] }],
    [
      'impact startedFrom is not an array',
      'getImpact',
      { startedFrom: {}, impacted: [], paths: [] },
    ],
    ['impact missing paths', 'getImpact', { startedFrom: [], impacted: [] }],
    [
      'impact impacted is not an array',
      'getImpact',
      { startedFrom: [], impacted: {}, paths: [] },
    ],
    [
      'impact paths is not an array',
      'getImpact',
      { startedFrom: [], impacted: [], paths: {} },
    ],
    ['risk response not an object', 'getRisk', null],
    ['risk missing startedFrom', 'getRisk', { risks: [] }],
    [
      'risk startedFrom is not an array',
      'getRisk',
      { startedFrom: {}, risks: [] },
    ],
    ['risk missing risks', 'getRisk', { startedFrom: [] }],
    ['risk risks is not an array', 'getRisk', { startedFrom: [], risks: null }],
  ])('rejects %s with a safe UNEXPECTED error', async (_label, method, body) => {
    const api = createAnalysisApi({ request: async () => body });

    const error = await api[method]({ from: FROM }).catch((caught) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe(ErrorKind.UNEXPECTED);
  });

  it('does not expose malformed response content or tokens in errors', async () => {
    const api = createAnalysisApi({
      request: async () => ({
        startedFrom: [],
        impacted: 'sensitive-response-body',
        paths: [],
      }),
    });

    const error = await api.getImpact({ from: FROM }).catch((caught) => caught);

    expect(error.message).not.toContain('sensitive-response-body');
    expect(error.message).not.toContain('test-token');
  });
});
