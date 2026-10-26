import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App.jsx';
import { canUsePreview } from '../preview/localPreview.js';
import { PREVIEW_BANNER } from '../preview/syntheticTwin.js';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('local synthetic preview', () => {
  it('is available only when Vite reports development mode', () => {
    expect(canUsePreview({ DEV: true })).toBe(true);
    expect(canUsePreview({ DEV: false, PROD: true })).toBe(false);
    expect(canUsePreview({ DEV: false })).toBe(false);
    expect(canUsePreview({ DEV: true, PROD: true })).toBe(false);
    expect(canUsePreview(undefined)).toBe(false);
  });

  it('does not offer preview selection when development mode is off', () => {
    vi.stubEnv('DEV', false);
    render(<App />);

    expect(
      screen.queryByRole('button', { name: /open local preview/i }),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText(/bearer token/i)).toBeInTheDocument();
  });

  it('requires an explicit selection and then shows synthetic data without API calls', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    expect(screen.queryByText(PREVIEW_BANNER)).not.toBeInTheDocument();
    await user.click(
      screen.getByRole('button', { name: /open local preview/i }),
    );

    expect(await screen.findByText(PREVIEW_BANNER)).toBeInTheDocument();
    expect(await screen.findByText('sample-aurora-gateway')).toBeInTheDocument();
    expect(
      screen.getByRole('status', { name: /local preview/i }),
    ).toHaveTextContent('Local preview');
    expect(screen.queryByText('Access confirmed')).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/bearer token/i)).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('exposes synthetic runtime evidence and relationships', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    await user.click(
      screen.getByRole('button', { name: /open local preview/i }),
    );
    await user.click(screen.getByRole('button', { name: /relationships/i }));
    expect(
      (await screen.findAllByText('sample-aurora-orders-db')).length,
    ).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: /entities/i }));
    await user.click(
      await screen.findByRole('button', { name: /sample-aurora-gateway/i }),
    );

    expect(
      await screen.findByRole('heading', { name: 'sample-aurora-gateway' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Synthetic sample')).toBeInTheDocument();
    expect(
      screen.getAllByText(/fictional sample value for local ui inspection/i)
        .length,
    ).toBeGreaterThan(0);
    expect(screen.queryByText('Access confirmed')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('leaves the regular authenticated flow unchanged when preview is not selected', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => [],
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    await user.type(screen.getByLabelText(/bearer token/i), 'test-token');
    await user.click(screen.getByRole('button', { name: /^connect$/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(
      await screen.findByRole('status', { name: /access confirmed/i }),
    ).toHaveTextContent('Access confirmed');
    expect(screen.queryByText(PREVIEW_BANNER)).not.toBeInTheDocument();
    for (const [, options] of fetchMock.mock.calls) {
      expect(options.headers.Authorization).toBe('Bearer test-token');
    }
  });
});
