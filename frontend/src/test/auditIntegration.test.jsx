import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TwinConsole } from '../components/TwinConsole.jsx';

const ENTITIES = [
  {
    id: 'e1',
    type: 'SERVICE',
    name: 'payments-api',
    externalRef: 'repo/apps/payments',
    environment: 'production',
    metadata: {},
  },
];

const RELATIONSHIPS = [];

const OK_RESULT = {
  status: 'OK',
  eventCount: 3,
  headSeq: 3,
  brokenAtSeq: null,
  detail: 'chain valid',
};

const ok = (body) => ({ ok: true, status: 200, json: async () => body });

function router(url) {
  if (url === '/api/v1/twin/audit/verify') return ok(OK_RESULT);
  if (url.includes('/relationships')) return ok(RELATIONSHIPS);
  return ok(ENTITIES);
}

function stubFetch(handler) {
  const mock = vi.fn(async (url, options) => handler(url, options));
  vi.stubGlobal('fetch', mock);
  return mock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('Audit Integrity navigation', () => {
  it('exposes the view live and sends exactly one approved GET only after Verify', async () => {
    const user = userEvent.setup();
    const fetchMock = stubFetch(router);
    render(<TwinConsole token="test-token" onDisconnect={() => {}} />);

    expect(await screen.findByText('payments-api')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);

    const navItem = screen.getByRole('button', { name: /audit integrity/i });
    await user.click(navItem);

    expect(screen.getByText('No verification requested')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);

    await user.click(screen.getByRole('button', { name: /verify audit chain/i }));

    expect(
      await screen.findByRole('status', { name: /verification result: OK/i }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(3);

    const auditCalls = fetchMock.mock.calls.filter(
      ([url]) => url === '/api/v1/twin/audit/verify',
    );
    expect(auditCalls).toHaveLength(1);
    const [, options] = auditCalls[0];
    expect(options.method).toBe('GET');
    expect(options.headers.Authorization).toBe('Bearer test-token');

    for (const [url, requestOptions] of fetchMock.mock.calls) {
      expect(url.startsWith('/api/')).toBe(true);
      expect(url).not.toContain('test-token');
      expect(requestOptions.headers.Authorization).toBe('Bearer test-token');
    }
    expect(document.body.textContent).not.toContain('test-token');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('test-token');
  });

  it('hides Audit Integrity in preview and makes no audit request', async () => {
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
      screen.queryByRole('button', { name: /audit integrity/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Audit Integrity')).not.toBeInTheDocument();
    expect(screen.queryByText('No verification requested')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
