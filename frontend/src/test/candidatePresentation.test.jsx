import { afterEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App.jsx';
import { TokenGate } from '../components/TokenGate.jsx';
import { CandidatePresentation } from '../views/CandidatePresentation.jsx';
import {
  FIXTURE_BANNER,
  loadSyntheticCandidateFixtures,
} from '../preview/syntheticCandidates.js';

const FIXTURE_CSS = resolve(process.cwd(), 'src/styles/fixture.css');

const NON_ENTAILMENT_NOTICE =
  'Candidates are model-generated and untrusted. Deterministic validation checks structure, provenance, category, qualification, limitation inheritance, and wording only. It does not establish that the cited evidence supports the statement. Nothing on this screen is approved, authorized, or executed, and no human review is recorded by this surface.';

function renderPresentation(onExit = vi.fn()) {
  const fixtureSets = loadSyntheticCandidateFixtures();
  render(<CandidatePresentation fixtureSets={fixtureSets} onExit={onExit} />);
  return { fixtureSets, onExit };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe('synthetic candidate presentation', () => {
  it('offers a DEV-only candidate preview entry in the token gate', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    await user.click(
      screen.getByRole('button', { name: /open candidate presentation/i }),
    );
    expect(await screen.findByText(FIXTURE_BANNER)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('offers no preview entry or fixture content when DEV is false and PROD is true', () => {
    vi.stubEnv('DEV', false);
    vi.stubEnv('PROD', true);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    expect(import.meta.env.DEV).toBeFalsy();
    expect(import.meta.env.PROD).toBeTruthy();
    expect(
      screen.queryByRole('button', { name: /open candidate presentation/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /open local preview/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(FIXTURE_BANNER)).not.toBeInTheDocument();
    expect(
      screen.queryByText(/synthetic candidate presentation/i),
    ).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain('fictional aurora gateway');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('keeps the synthetic-data banner visible across fixture sets', async () => {
    const user = userEvent.setup();
    renderPresentation();

    expect(screen.getByText(FIXTURE_BANNER)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /text rendering rehearsal/i }));
    expect(screen.getByText(FIXTURE_BANNER)).toBeInTheDocument();
  });

  it('shows the exact non-entailment notice', () => {
    renderPresentation();
    expect(screen.getByText(NON_ENTAILMENT_NOTICE)).toBeInTheDocument();
  });

  it('presents S-A as an illustrative candidate and S-B as a synthetic verdict', () => {
    renderPresentation();

    expect(screen.getByText(/illustrative synthetic candidate/i)).toBeInTheDocument();
    expect(
      screen.getByText(/synthetic deterministic validation verdict: PASSED/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/does not establish semantic support/i),
    ).toBeInTheDocument();
  });

  it('exposes no S-C or S-D state, control, or wording', () => {
    renderPresentation();
    const text = document.body.textContent.replace(NON_ENTAILMENT_NOTICE, '');
    expect(text).not.toMatch(
      /\b(S-C|S-D|review|accept|reject|approve|approved|decision|accepted|rejected)\b/i,
    );
  });

  it('labels every rendered candidate and evidence item as synthetic', () => {
    renderPresentation();

    expect(screen.getAllByText('Synthetic fixture candidate')).toHaveLength(1);
    expect(screen.getAllByText('Synthetic fixture evidence item')).toHaveLength(1);
    expect(
      screen.getByRole('button', { name: /synthetic presentation reference E1/i }),
    ).toBeInTheDocument();
  });

  it('shows only aggregate counts and fixed labels in the excluded summary', () => {
    renderPresentation();
    const region = screen.getByRole('region', {
      name: /excluded presentation summary/i,
    });

    expect(
      within(region).getByText(/Fabricated aggregate count/i),
    ).toBeInTheDocument();
    expect(within(region).getByText('REFERENCE_EXISTENCE')).toBeInTheDocument();
    expect(within(region).getByText('QUALIFICATION')).toBeInTheDocument();
    expect(
      within(region).getByText(
        /No candidate statement, evidence reference, or validator detail/i,
      ),
    ).toBeInTheDocument();
  });

  it('never renders injected excluded-statement or validator-detail fields', () => {
    const fixtureSets = loadSyntheticCandidateFixtures();
    Object.assign(fixtureSets[0].excludedSummary, {
      rejectedStatement: 'SENTINEL-STATEMENT',
      evidenceReference: 'SENTINEL-REFERENCE',
      validatorDetail: 'SENTINEL-DETAIL',
      rawRejectedCandidate: 'SENTINEL-RAW',
    });
    render(<CandidatePresentation fixtureSets={fixtureSets} onExit={vi.fn()} />);

    expect(screen.queryByText(/SENTINEL/)).not.toBeInTheDocument();
  });

  it('renders adversarial fixture text literally with no injected elements', async () => {
    const user = userEvent.setup();
    renderPresentation();

    await user.click(screen.getByRole('button', { name: /text rendering rehearsal/i }));
    expect(
      await screen.findByText(/<img src=x onerror="fixture-marker">/),
    ).toBeInTheDocument();
    expect(document.querySelector('img')).toBeNull();
    expect(document.querySelector('script')).toBeNull();
  });

  it('makes no fetch call on entry, set switching, or exit', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    await user.click(
      screen.getByRole('button', { name: /open candidate presentation/i }),
    );
    await screen.findByText(FIXTURE_BANNER);
    await user.click(screen.getByRole('button', { name: /text rendering rehearsal/i }));
    await user.click(screen.getByRole('button', { name: /exit candidate preview/i }));

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('uses no browser storage or URL mutation', async () => {
    const user = userEvent.setup();
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const pushState = vi.spyOn(window.history, 'pushState');
    const replaceState = vi.spyOn(window.history, 'replaceState');
    renderPresentation();

    await user.click(screen.getByRole('button', { name: /text rendering rehearsal/i }));

    expect(setItem).not.toHaveBeenCalled();
    expect(pushState).not.toHaveBeenCalled();
    expect(replaceState).not.toHaveBeenCalled();
  });

  it('moves keyboard focus to the referenced synthetic evidence item', async () => {
    const user = userEvent.setup();
    renderPresentation();

    await user.click(
      screen.getByRole('button', { name: /synthetic presentation reference E1/i }),
    );
    const heading = await screen.findByRole('heading', {
      name: /synthetic fixture evidence E1/i,
    });
    expect(heading).toHaveFocus();
  });

  it('removes stale fixture content when sets are switched', async () => {
    const user = userEvent.setup();
    renderPresentation();

    expect(
      screen.getByText(/fictional aurora gateway is recorded/i),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /text rendering rehearsal/i }));

    expect(
      screen.queryByText(/fictional aurora gateway is recorded/i),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/<img src=x/)).toBeInTheDocument();
  });

  it('clears the preview and returns to the token gate on exit', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(
      screen.getByRole('button', { name: /open candidate presentation/i }),
    );
    await screen.findByText(FIXTURE_BANNER);
    await user.click(screen.getByRole('button', { name: /exit candidate preview/i }));

    expect(screen.queryByText(FIXTURE_BANNER)).not.toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /connect to the control plane/i }),
    ).toBeInTheDocument();
  });

  it('supports keyboard selection with visible focus and polite announcements', async () => {
    const user = userEvent.setup();
    renderPresentation();
    const announcement = screen.getByRole('status');
    expect(announcement).toHaveAttribute('aria-live', 'polite');

    const selection = screen.getByRole('button', {
      name: /illustrative candidate and evidence presentation/i,
    });
    selection.focus();
    expect(selection).toHaveFocus();
    await user.keyboard('{Enter}');

    expect(announcement).toHaveTextContent(/illustrative/i);
  });

  it('gives an explicit neutral empty state without claiming no problems', async () => {
    const user = userEvent.setup();
    renderPresentation();

    await user.click(screen.getByRole('button', { name: /empty presentation/i }));
    expect(
      screen.getByText(/no synthetic candidates in this fixture set/i),
    ).toBeInTheDocument();
    expect(screen.queryByText(/no problems found/i)).not.toBeInTheDocument();
  });

  it('disables banner animation and transition under reduced motion', () => {
    const css = readFileSync(FIXTURE_CSS, 'utf8');
    const media = css.match(
      /@media\s*\(prefers-reduced-motion:\s*reduce\)\s*\{([\s\S]*?)\n\}/,
    );
    expect(media).not.toBeNull();
    const bannerRule = media[1].match(/\.fixture-banner[^{}]*\{([^}]*)\}/);
    expect(bannerRule).not.toBeNull();
    expect(bannerRule[1]).toMatch(/animation:\s*none\s*!important/);
    expect(bannerRule[1]).toMatch(/transition:\s*none\s*!important/);
  });

  it('exposes the candidate preview loading state as an accessible control', () => {
    render(
      <TokenGate
        onSubmit={vi.fn()}
        onCandidatePreview={vi.fn()}
        candidatePreviewLoading
      />,
    );
    const button = screen.getByRole('button', {
      name: /loading candidate presentation/i,
    });
    expect(button).toBeDisabled();
  });
});
