import { describe, it, expect, vi } from 'vitest';
import { createApiClient } from '../api/client.js';
import { ApiError, ErrorKind, safeMessage } from '../api/errors.js';

function jsonResponse(body, { status = 200 } = {}) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

describe('createApiClient', () => {
  it('sends no request before a token is supplied', async () => {
    const fetchImpl = vi.fn();
    const client = createApiClient({ getToken: () => null, fetchImpl });
    await expect(client.request('/api/v1/twin/entities')).rejects.toMatchObject({
      kind: ErrorKind.NO_TOKEN,
    });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('attaches a bearer header only to local /api requests', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse([]));
    const client = createApiClient({ getToken: () => 'test-token', fetchImpl });

    await client.request('/api/v1/twin/entities');
    const [url, options] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v1/twin/entities');
    expect(options.headers.Authorization).toBe('Bearer test-token');

    await expect(client.request('https://evil.example/steal')).rejects.toMatchObject({
      kind: ErrorKind.UNEXPECTED,
    });
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it('maps 401 and 403 to safe errors without leaking the response body or token', async () => {
    for (const status of [401, 403]) {
      const fetchImpl = vi.fn().mockResolvedValue({
        ok: false,
        status,
        json: async () => ({ detail: 'test-token should never surface' }),
      });
      const client = createApiClient({ getToken: () => 'test-token', fetchImpl });
      const error = await client.request('/api/v1/twin/entities').catch((e) => e);
      expect(error).toBeInstanceOf(ApiError);
      expect(error.kind).toBe(status === 401 ? ErrorKind.UNAUTHORIZED : ErrorKind.FORBIDDEN);
      expect(error.message).not.toContain('test-token');
      expect(error.message).not.toContain('detail');
    }
  });

  it('maps HTTP 400 to a fixed safe validation error', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ detail: 'test-token sensitive-response-body' }),
    });
    const client = createApiClient({ getToken: () => 'test-token', fetchImpl });

    const error = await client
      .request('/api/v1/twin/impact?from=x')
      .catch((caught) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe(ErrorKind.BAD_REQUEST);
    expect(error.status).toBe(400);
    expect(error.message).toBe(safeMessage(ErrorKind.BAD_REQUEST));
    expect(error.message).not.toContain('test-token');
    expect(error.message).not.toContain('sensitive-response-body');
  });

  it('maps network failures and server errors', async () => {
    const offline = createApiClient({
      getToken: () => 't',
      fetchImpl: vi.fn().mockRejectedValue(new Error('boom')),
    });
    await expect(offline.request('/api/v1/twin/entities')).rejects.toMatchObject({
      kind: ErrorKind.NETWORK,
    });

    const server = createApiClient({
      getToken: () => 't',
      fetchImpl: vi.fn().mockResolvedValue(jsonResponse({}, { status: 503 })),
    });
    await expect(server.request('/api/v1/twin/entities')).rejects.toMatchObject({
      kind: ErrorKind.SERVER,
      status: 503,
    });
  });
});
