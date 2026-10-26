import { ApiError, ErrorKind } from './errors.js';

// Only local, read-only control-plane API paths are allowed through this client.
// The Vite dev proxy forwards /api to http://127.0.0.1:8080 (vite.config.js).
const API_PREFIX = '/api/';

function kindForStatus(status) {
  if (status === 401) return ErrorKind.UNAUTHORIZED;
  if (status === 403) return ErrorKind.FORBIDDEN;
  if (status === 404) return ErrorKind.NOT_FOUND;
  if (status >= 500) return ErrorKind.SERVER;
  return ErrorKind.UNEXPECTED;
}

/**
 * Create a small, centralized API client.
 *
 * - The bearer token is read from `getToken()` on each call and kept in memory
 *   by the caller. It is never persisted, logged, or placed in a URL.
 * - No request is sent before a token is supplied.
 * - The Authorization header is attached only to the local `/api` surface.
 */
export function createApiClient({ getToken, fetchImpl = globalThis.fetch } = {}) {
  async function request(path, { headers = {}, ...rest } = {}) {
    const token = typeof getToken === 'function' ? getToken() : null;
    if (!token) {
      throw new ApiError(ErrorKind.NO_TOKEN);
    }
    if (typeof path !== 'string' || !path.startsWith(API_PREFIX)) {
      throw new ApiError(ErrorKind.UNEXPECTED);
    }

    let response;
    try {
      response = await fetchImpl(path, {
        ...rest,
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
          ...headers,
        },
      });
    } catch {
      throw new ApiError(ErrorKind.NETWORK);
    }

    if (!response || typeof response.ok !== 'boolean') {
      throw new ApiError(ErrorKind.UNEXPECTED);
    }
    if (!response.ok) {
      throw new ApiError(kindForStatus(response.status), { status: response.status });
    }
    if (response.status === 204) {
      return null;
    }
    try {
      return await response.json();
    } catch {
      throw new ApiError(ErrorKind.UNEXPECTED);
    }
  }

  return { request };
}

export { API_PREFIX };
