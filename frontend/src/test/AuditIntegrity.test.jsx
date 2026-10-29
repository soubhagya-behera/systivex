import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AuditIntegrity } from '../views/AuditIntegrity.jsx';
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

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function renderView(verify = vi.fn()) {
  const auditApi = { verify };
  render(<AuditIntegrity auditApi={auditApi} />);
  return auditApi;
}

function verifyButton() {
  return screen.getByRole('button', { name: /verify audit chain/i });
}

async function verify(user) {
  await user.click(verifyButton());
}

describe('AuditIntegrity initial and loading states', () => {
  it('makes no request before the explicit verification action', () => {
    const auditApi = renderView();

    expect(screen.getByText('No verification requested')).toBeInTheDocument();
    expect(
      screen.getByText(/choose verify audit chain to run one read-only/i),
    ).toBeInTheDocument();
    expect(verifyButton()).toBeEnabled();
    expect(auditApi.verify).not.toHaveBeenCalled();
  });

  it('shows the loading panel and disables the button while verifying', async () => {
    const user = userEvent.setup();
    const pending = deferred();
    const auditApi = renderView(vi.fn(() => pending.promise));

    await verify(user);

    expect(
      await screen.findByRole('status', { name: /verifying audit chain/i }),
    ).toBeInTheDocument();
    expect(verifyButton()).toBeDisabled();
    expect(auditApi.verify).toHaveBeenCalledTimes(1);

    await act(async () => {
      pending.resolve(OK_RESULT);
    });

    expect(
      await screen.findByRole('status', { name: /verification result: OK/i }),
    ).toBeInTheDocument();
    expect(verifyButton()).toBeEnabled();
  });

  it('supports keyboard activation of the verification control', async () => {
    const user = userEvent.setup();
    const verifyMock = vi.fn().mockResolvedValue(OK_RESULT);
    renderView(verifyMock);

    verifyButton().focus();
    expect(verifyButton()).toHaveFocus();
    await user.keyboard('{Enter}');

    expect(
      await screen.findByRole('status', { name: /verification result: OK/i }),
    ).toBeInTheDocument();
    expect(verifyMock).toHaveBeenCalledTimes(1);
  });
});

describe('AuditIntegrity results', () => {
  it('renders an OK result with faithful counts and a null broken sequence', async () => {
    const user = userEvent.setup();
    renderView(vi.fn().mockResolvedValue(OK_RESULT));

    await verify(user);

    const status = await screen.findByRole('status', {
      name: /verification result: OK/i,
    });
    expect(status).toHaveTextContent('OK — chain verified');
    expect(screen.getByRole('heading', { name: 'Audit chain verification' })).toBeInTheDocument();
    expect(screen.getByText('Events verified').closest('.analysis-fact')).toHaveTextContent('12');
    expect(screen.getByText('Head sequence').closest('.analysis-fact')).toHaveTextContent('12');
    expect(screen.getByText('First broken sequence').closest('.analysis-fact')).toHaveTextContent(
      'Not reported',
    );
    expect(screen.getByText(/chain valid/)).toBeInTheDocument();
    expect(screen.queryByText('Request failed')).not.toBeInTheDocument();
  });

  it('renders BROKEN as a distinct finding for HTTP 200 and shows the broken sequence', async () => {
    const user = userEvent.setup();
    renderView(vi.fn().mockResolvedValue(BROKEN_RESULT));

    await verify(user);

    const status = await screen.findByRole('status', {
      name: /verification result: BROKEN/i,
    });
    expect(status).toHaveTextContent('BROKEN — chain integrity finding');
    expect(status).toHaveTextContent(/not an HTTP error/i);
    expect(screen.getByText('First broken sequence').closest('.analysis-fact')).toHaveTextContent(
      '4',
    );
    expect(
      screen.getByText(/event_hash does not match stored fields/),
    ).toBeInTheDocument();
    expect(screen.queryByText('Request failed')).not.toBeInTheDocument();
  });

  it('preserves a negative head sequence and never infers a break from a null sequence', async () => {
    const user = userEvent.setup();
    renderView(
      vi.fn().mockResolvedValue({
        status: 'BROKEN',
        eventCount: 0,
        headSeq: -1,
        brokenAtSeq: null,
        detail: 'chain head is missing',
      }),
    );

    await verify(user);

    const status = await screen.findByRole('status', {
      name: /verification result: BROKEN/i,
    });
    expect(status).toHaveTextContent('BROKEN — chain integrity finding');
    expect(screen.getByText('Head sequence').closest('.analysis-fact')).toHaveTextContent('-1');
    expect(screen.getByText('First broken sequence').closest('.analysis-fact')).toHaveTextContent(
      'Not reported',
    );
    expect(screen.getByText(/chain head is missing/)).toBeInTheDocument();
  });
});

describe('AuditIntegrity errors', () => {
  it.each([
    [ErrorKind.SERVER, /server error/i],
    [ErrorKind.NETWORK, /could not reach/i],
    [ErrorKind.UNAUTHORIZED, /authentication required/i],
    [ErrorKind.FORBIDDEN, /access denied/i],
    [ErrorKind.UNEXPECTED, /failed unexpectedly/i],
  ])('uses the fixed safe message for %s', async (kind, message) => {
    const user = userEvent.setup();
    renderView(vi.fn().mockRejectedValue(new ApiError(kind)));

    await verify(user);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(message);
    expect(alert).toHaveTextContent(safeMessage(kind));
    expect(screen.queryByRole('status', { name: /verification result/i })).not.toBeInTheDocument();
  });

  it('retries only the verification request and can then show the result', async () => {
    const user = userEvent.setup();
    const verifyMock = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(ErrorKind.SERVER))
      .mockResolvedValueOnce(OK_RESULT);
    renderView(verifyMock);

    await verify(user);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/server error/i);
    expect(verifyMock).toHaveBeenCalledTimes(1);

    await user.click(within(alert).getByRole('button', { name: /retry/i }));

    expect(
      await screen.findByRole('status', { name: /verification result: OK/i }),
    ).toBeInTheDocument();
    expect(verifyMock).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('clears a prior OK result when a later verification fails', async () => {
    const user = userEvent.setup();
    const first = deferred();
    const second = deferred();
    const verifyMock = vi
      .fn()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    renderView(verifyMock);

    await verify(user);
    await act(async () => {
      first.resolve(OK_RESULT);
    });

    const firstStatus = await screen.findByRole('status', {
      name: /verification result: OK/i,
    });
    expect(firstStatus).toHaveTextContent('OK — chain verified');
    expect(screen.getByText('Events verified').closest('.analysis-fact')).toHaveTextContent(
      '12',
    );
    expect(screen.getByText('Head sequence').closest('.analysis-fact')).toHaveTextContent(
      '12',
    );

    await verify(user);

    expect(
      await screen.findByRole('status', { name: /verifying audit chain/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('status', { name: /verification result: OK/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('OK — chain verified')).not.toBeInTheDocument();
    expect(screen.queryByText('Events verified')).not.toBeInTheDocument();
    expect(verifyMock).toHaveBeenCalledTimes(2);

    await act(async () => {
      second.reject(new ApiError(ErrorKind.SERVER));
    });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(safeMessage(ErrorKind.SERVER));
    expect(
      screen.queryByRole('status', { name: /verification result: OK/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('OK — chain verified')).not.toBeInTheDocument();
    expect(screen.queryByText('Events verified')).not.toBeInTheDocument();
  });
});
