import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TwinConsole } from '../components/TwinConsole.jsx';

const ENTITIES = [
  {
    id: 'e1',
    type: 'SERVICE',
    name: 'payments-api',
    externalRef: 'repo/apps/payments',
    environment: 'production',
    metadata: {},
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-02-01T00:00:00Z',
  },
];

const RELATIONSHIPS = [
  {
    id: 'r1',
    sourceId: 'e1',
    targetId: 'e2',
    type: 'CALLS',
    createdAt: '2026-01-04T00:00:00Z',
  },
];

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function stubDeferredFetch() {
  const requests = [];
  const mock = vi.fn((url) => {
    const request = deferred();
    requests.push({ url, ...request });
    return request.promise;
  });
  vi.stubGlobal('fetch', mock);
  return requests;
}

function response(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}

async function settle(request, body, status = 200) {
  await act(async () => {
    request.resolve(response(body, status));
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Twin snapshot transitions', () => {
  it('clears confirmed state on a failed refresh and restores it only after both reads succeed', async () => {
    const user = userEvent.setup();
    const requests = stubDeferredFetch();
    const { rerender } = render(
      <TwinConsole token="initial-token" onDisconnect={() => {}} />,
    );

    await waitFor(() => expect(requests).toHaveLength(2));
    await settle(requests[0], ENTITIES);
    await settle(requests[1], RELATIONSHIPS);

    expect(
      await screen.findByRole('status', { name: /access confirmed/i }),
    ).toBeInTheDocument();
    expect(await screen.findByText('Snapshot loaded')).toBeInTheDocument();

    rerender(<TwinConsole token="next-token" onDisconnect={() => {}} />);

    await waitFor(() => expect(requests).toHaveLength(4));
    expect(await screen.findByText('Loading snapshot…')).toBeInTheDocument();
    expect(screen.queryByText('Access confirmed')).not.toBeInTheDocument();
    expect(screen.queryByText('Snapshot loaded')).not.toBeInTheDocument();

    await settle(requests[2], null, 500);
    await settle(requests[3], null, 500);

    expect(
      await screen.findByRole('status', { name: /connection unavailable/i }),
    ).toBeInTheDocument();
    expect(await screen.findByText('No snapshot loaded')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(requests).toHaveLength(6));
    expect(screen.queryByText('Access confirmed')).not.toBeInTheDocument();
    expect(await screen.findByText('Loading snapshot…')).toBeInTheDocument();

    await settle(requests[4], ENTITIES);
    await waitFor(() =>
      expect(screen.getByText(/loading system twin/i)).toBeInTheDocument(),
    );
    expect(screen.queryByText('Access confirmed')).not.toBeInTheDocument();
    expect(screen.queryByText('Snapshot loaded')).not.toBeInTheDocument();

    await settle(requests[5], RELATIONSHIPS);
    expect(
      await screen.findByRole('status', { name: /access confirmed/i }),
    ).toBeInTheDocument();
    expect(await screen.findByText('Snapshot loaded')).toBeInTheDocument();
  });

  it('does not confirm a partial endpoint failure or attach a snapshot', async () => {
    const requests = stubDeferredFetch();
    render(<TwinConsole token="test-token" onDisconnect={() => {}} />);

    await waitFor(() => expect(requests).toHaveLength(2));
    await settle(requests[0], ENTITIES);
    await settle(requests[1], null, 403);

    expect(
      await screen.findByRole('status', { name: /access rejected/i }),
    ).toBeInTheDocument();
    expect(screen.queryByText('Access confirmed')).not.toBeInTheDocument();
    expect(await screen.findByText('No snapshot loaded')).toBeInTheDocument();
    expect(screen.queryByText('Snapshot loaded')).not.toBeInTheDocument();
  });
});
