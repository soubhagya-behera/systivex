import { afterEach, describe, expect, it, vi } from 'vitest';
import { AUDIT_VERIFY, createAuditApi } from '../api/audit.js';
import { createApiClient } from '../api/client.js';
import { ApiError, ErrorKind, safeMessage } from '../api/errors.js';

const OK_RESULT = {
  status: 'OK',
  eventCount: 12,
  headSeq: 12,
  brokenAtSeq: null,
  detail: 'chain valid',
};

const BROKEN_RESULT = {
  status: 'BROKEN',
  eventCount: 12,
  headSeq: 12,
  brokenAtSeq: 4,
  detail: 'event_hash does not match stored fields',
};

function ok(body) {
  return { ok: true, status: 200, json: async () => body };
}

function setup(body, token = 'test-token') {
  const fetchImpl = vi.fn().mockResolvedValue(ok(body));
  const client = createApiClient({ getToken: () => token, fetchImpl });
  return { api: createAuditApi(client), fetchImpl };
}

async function captureError(promise) {
  try {
    await promise;
  } catch (error) {
    return error;
  }
  throw new Error('expected the audit verification request to reject');
}

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('audit API wrapper', () => {
  it('requests the exact audit verification path with GET and the bearer token', async () => {
    const { api, fetchImpl } = setup(OK_RESULT);

    await expect(api.verify()).resolves.toEqual(OK_RESULT);

    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const [path, options] = fetchImpl.mock.calls[0];
    expect(path).toBe(AUDIT_VERIFY);
    expect(path).toBe('/api/v1/twin/audit/verify');
    expect(options.method).toBe('GET');
    expect(options.headers.Accept).toBe('application/json');
    expect(options.headers.Authorization).toBe('Bearer test-token');
  });

  it('accepts an OK result with a null broken sequence', async () => {
    const { api } = setup(OK_RESULT);

    await expect(api.verify()).resolves.toEqual(OK_RESULT);
  });

  it('accepts a BROKEN result with a reported broken sequence', async () => {
    const { api } = setup(BROKEN_RESULT);

    await expect(api.verify()).resolves.toEqual(BROKEN_RESULT);
  });

  it('accepts BROKEN with a null broken sequence and a negative head sequence', async () => {
    const body = {
      status: 'BROKEN',
      eventCount: 0,
      headSeq: -1,
      brokenAtSeq: null,
      detail: 'chain head is missing',
    };
    const { api } = setup(body);

    await expect(api.verify()).resolves.toEqual(body);
  });

  it('sends no request when no token is available', async () => {
    const fetchImpl = vi.fn();
    const client = createApiClient({ getToken: () => null, fetchImpl });
    const api = createAuditApi(client);

    await expect(api.verify()).rejects.toMatchObject({
      kind: ErrorKind.NO_TOKEN,
      message: safeMessage(ErrorKind.NO_TOKEN),
    });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it.each([
    ['an array', []],
    ['null', null],
    ['a string', 'not an object'],
    ['an unsupported status', { ...OK_RESULT, status: 'UNKNOWN' }],
    ['a missing status', { ...OK_RESULT, status: undefined }],
    ['a fractional eventCount', { ...OK_RESULT, eventCount: 1.5 }],
    ['a string headSeq', { ...OK_RESULT, headSeq: '12' }],
    ['an infinite headSeq', { ...OK_RESULT, headSeq: Number.POSITIVE_INFINITY }],
    ['a fractional brokenAtSeq', { ...BROKEN_RESULT, brokenAtSeq: 1.5 }],
    ['a string brokenAtSeq', { ...BROKEN_RESULT, brokenAtSeq: '4' }],
    ['a non-string detail', { ...OK_RESULT, detail: 42 }],
    ['a missing detail', { ...OK_RESULT, detail: undefined }],
  ])('rejects %s with a safe unexpected error', async (_label, body) => {
    const { api } = setup(body);

    const error = await captureError(api.verify());

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      kind: ErrorKind.UNEXPECTED,
      message: safeMessage(ErrorKind.UNEXPECTED),
    });
  });

  it('does not leak response content or the token in validation errors', async () => {
    const { api } = setup({
      status: 'UNKNOWN',
      eventCount: 1,
      headSeq: 1,
      brokenAtSeq: null,
      detail: 'test-token sensitive-response-body',
    });

    const error = await captureError(api.verify());

    expect(error.message).toBe(safeMessage(ErrorKind.UNEXPECTED));
    expect(error.message).not.toContain('test-token');
    expect(error.message).not.toContain('sensitive-response-body');
    expect(String(error.stack ?? '')).not.toContain('sensitive-response-body');
  });

  it('does not persist the token in browser storage or cookies', async () => {
    const { api } = setup(OK_RESULT);

    await api.verify();

    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('test-token');
  });
});
