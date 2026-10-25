/**
 * Vite development proxy helpers.
 *
 * The proxy destination is always the loopback host. Only the numeric port is
 * configurable, and callers cannot supply a scheme, host, path, or credentials.
 */
export const DEFAULT_BACKEND_PORT = 8080;
export const LOOPBACK_HOST = '127.0.0.1';

export function normalizeBackendPort(raw) {
  if (raw === null || raw === undefined || String(raw).trim() === '') {
    return DEFAULT_BACKEND_PORT;
  }

  const text = String(raw).trim();
  if (!/^\d{1,5}$/.test(text)) {
    throw new Error(
      'SYSTIVEX_BACKEND_PORT must be a numeric TCP port between 1 and 65535',
    );
  }

  const port = Number(text);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(
      'SYSTIVEX_BACKEND_PORT must be a numeric TCP port between 1 and 65535',
    );
  }
  return port;
}

export function backendProxyTarget(raw) {
  return `http://${LOOPBACK_HOST}:${normalizeBackendPort(raw)}`;
}
