import {
  backendProxyTarget,
  DEFAULT_BACKEND_PORT,
  LOOPBACK_HOST,
  normalizeBackendPort,
} from '../../devProxy.js';

describe('Vite backend proxy port', () => {
  it('defaults to the documented port 8080', () => {
    expect(DEFAULT_BACKEND_PORT).toBe(8080);
    expect(normalizeBackendPort(undefined)).toBe(8080);
    expect(normalizeBackendPort('')).toBe(8080);
    expect(backendProxyTarget()).toBe('http://127.0.0.1:8080');
  });

  it('accepts numeric loopback ports, including the selected 8090', () => {
    expect(normalizeBackendPort('8090')).toBe(8090);
    expect(normalizeBackendPort(8090)).toBe(8090);
    expect(normalizeBackendPort(' 8090 ')).toBe(8090);
    expect(normalizeBackendPort('1')).toBe(1);
    expect(normalizeBackendPort('65535')).toBe(65535);
    expect(backendProxyTarget(8090)).toBe('http://127.0.0.1:8090');
  });

  it.each([
    '0',
    '65536',
    '8081.5',
    '-1',
    '+8081',
    'abc',
    'http://127.0.0.1:8081',
    'https://example.com:8081',
    'localhost:8081',
    '127.0.0.1:8081',
    '8081/path',
    '8081?x=1',
    '8081#fragment',
    'user:pass@127.0.0.1:8081',
  ])('rejects non-numeric or destination-like input: %s', (value) => {
    expect(() => normalizeBackendPort(value)).toThrow(
      /numeric TCP port between 1 and 65535/,
    );
    expect(() => backendProxyTarget(value)).toThrow(
      /numeric TCP port between 1 and 65535/,
    );
  });

  it('hard-codes the IPv4 loopback host', () => {
    expect(LOOPBACK_HOST).toBe('127.0.0.1');
    expect(backendProxyTarget('8090')).toMatch(/^http:\/\/127\.0\.0\.1:\d+$/);
  });
});
