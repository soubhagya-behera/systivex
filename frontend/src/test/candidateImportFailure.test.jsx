import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App.jsx';

const importControl = vi.hoisted(() => ({ fail: true }));

vi.mock('../preview/syntheticCandidates.js', async () => {
  if (importControl.fail) {
    throw new Error('simulated dynamic import failure');
  }
  return vi.importActual('../preview/syntheticCandidates.js');
});

afterEach(() => {
  importControl.fail = true;
  vi.unstubAllEnvs();
});

describe('candidate preview import failure', () => {
  it('announces the failure, re-enables retry, and recovers on a successful retry', async () => {
    const user = userEvent.setup();
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(<App />);
    const button = screen.getByRole('button', { name: /open candidate presentation/i });

    await user.click(button);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /could not be loaded/i,
    );
    expect(button).toBeEnabled();
    expect(
      screen.queryByRole('heading', { name: /synthetic candidate presentation/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/SYNTHETIC FIXTURE DATA/i)).not.toBeInTheDocument();

    importControl.fail = false;
    await user.click(button);

    expect(await screen.findByText(/SYNTHETIC FIXTURE DATA/i)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    consoleError.mockRestore();
  });
});
