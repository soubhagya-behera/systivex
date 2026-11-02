import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ProposalGovernance } from '../views/ProposalGovernance.jsx';
import { ApiError, ErrorKind, safeMessage } from '../api/errors.js';

const KEY = '11111111-1111-1111-1111-111111111111';
const KEY_TWO = '33333333-3333-3333-3333-333333333333';
const KEY_THREE = '44444444-4444-4444-4444-444444444444';
const EVENT_ID = '22222222-2222-2222-2222-222222222222';

const PROPOSAL = {
  proposalKey: KEY,
  revisionNumber: 2,
  status: 'AWAITING_APPROVAL',
  title: 'Change payment routing',
  description: 'Route checkout traffic through the new payment service.',
  operations: [{ action: 'UPDATE', target: 'payments-api', note: '<b>skip</b>' }],
  operationsHash: 'sha256:operations',
  assessment: { decision: 'ALLOW', rationale: 'No blocked dependency.' },
  assessmentHash: 'sha256:assessment',
  policyDecision: 'ALLOW',
  policyVersion: '2026-01',
  riskTaxonomy: '2026-01',
  twinFingerprint: 'sha256:twin',
  snapshotObservedAt: '2026-10-01T09:55:00Z',
  proposerIssuer: 'https://issuer.example',
  proposerSubject: 'user-1',
  approverIssuer: null,
  approverSubject: null,
  reason: null,
  expiresAt: null,
  createdAt: '2026-10-01T09:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
};

const PAGE = {
  items: [PROPOSAL],
  page: 0,
  size: 20,
  total: 1,
};

const REVISION_ONE = {
  ...PROPOSAL,
  revisionNumber: 1,
  status: 'DRAFT',
  title: 'Initial payment routing',
  description: 'Initial draft.',
  assessment: null,
  assessmentHash: null,
  policyDecision: null,
  policyVersion: null,
  riskTaxonomy: null,
  twinFingerprint: null,
  snapshotObservedAt: null,
  approverIssuer: null,
  approverSubject: null,
  reason: null,
  createdAt: '2026-10-01T08:00:00Z',
  updatedAt: '2026-10-01T08:30:00Z',
};

const AUDIT_ONE = {
  id: EVENT_ID,
  seq: 1,
  occurredAt: '2026-10-01T09:01:00Z',
  actorIssuer: 'https://issuer.example',
  actorSubject: 'user-1',
  action: 'CREATED',
  outcome: 'SUCCESS',
  proposalKey: KEY,
  revisionNumber: 1,
  operationsHash: 'sha256:operations',
  assessmentHash: null,
  reason: null,
  correlationId: 'correlation-1',
  prevHash: 'sha256:previous',
  eventHash: 'sha256:event',
};

const AUDIT_TWO = {
  ...AUDIT_ONE,
  id: '55555555-5555-5555-5555-555555555555',
  seq: 2,
  action: 'FUTURE_ACTION',
  outcome: 'FUTURE_OUTCOME',
  assessmentHash: 'sha256:assessment',
  reason: 'Forward-compatible audit reason.',
};

function createApi(overrides = {}) {
  return {
    listProposals: vi.fn().mockResolvedValue(PAGE),
    getLatestProposal: vi.fn().mockResolvedValue(PROPOSAL),
    listProposalRevisions: vi.fn().mockResolvedValue([REVISION_ONE, PROPOSAL]),
    getProposalRevision: vi.fn().mockResolvedValue(REVISION_ONE),
    listProposalAudit: vi.fn().mockResolvedValue([AUDIT_ONE, AUDIT_TWO]),
    ...overrides,
  };
}

function renderView(api = createApi()) {
  render(<ProposalGovernance proposalApi={api} />);
  return api;
}

async function selectProposal(user) {
  await user.click(
    await screen.findByRole('button', { name: /Change payment routing/i }),
  );
  return screen.findByRole('heading', { name: 'Latest revision' });
}

describe('ProposalGovernance initial loading', () => {
  it('loads only the first proposal page on entry', async () => {
    const api = renderView();

    expect(api.listProposals).toHaveBeenCalledTimes(1);
    expect(api.listProposals).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(api.getLatestProposal).not.toHaveBeenCalled();
    expect(api.listProposalRevisions).not.toHaveBeenCalled();
    expect(api.getProposalRevision).not.toHaveBeenCalled();
    expect(api.listProposalAudit).not.toHaveBeenCalled();

    expect(await screen.findByText('Change payment routing')).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 1 · 1 proposal')).toBeInTheDocument();
    expect(screen.getByText('No proposal selected')).toBeInTheDocument();
    expect(api.listProposals).toHaveBeenCalledTimes(1);
  });

  it('shows a loading state for the first page', async () => {
    let resolvePage;
    const listProposals = vi.fn(
      () =>
        new Promise((resolve) => {
          resolvePage = resolve;
        }),
    );
    renderView(createApi({ listProposals }));

    expect(
      screen.getByRole('status', { name: 'Loading proposal page' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('No proposal selected')).not.toBeInTheDocument();

    resolvePage(PAGE);

    expect(await screen.findByText('Change payment routing')).toBeInTheDocument();
  });
});

describe('ProposalGovernance selection and latest revision', () => {
  it('loads only the selected proposal latest revision', async () => {
    const user = userEvent.setup();
    const api = renderView();

    const heading = await selectProposal(user);

    expect(api.getLatestProposal).toHaveBeenCalledTimes(1);
    expect(api.getLatestProposal).toHaveBeenCalledWith(KEY);
    expect(api.listProposalRevisions).not.toHaveBeenCalled();
    expect(api.getProposalRevision).not.toHaveBeenCalled();
    expect(api.listProposalAudit).not.toHaveBeenCalled();
    expect(api.listProposals).toHaveBeenCalledTimes(1);
    expect(heading).toBeInTheDocument();
  });

  it('renders every proposal field and explicit nullable values', async () => {
    const user = userEvent.setup();
    renderView();

    const heading = await selectProposal(user);
    const section = within(heading.closest('section'));

    expect(section.getByText(KEY)).toBeInTheDocument();
    expect(section.getByText('Revision number').closest('.analysis-fact')).toHaveTextContent(
      '2',
    );
    expect(section.getByText('Change payment routing')).toBeInTheDocument();
    expect(
      section.getByText('Route checkout traffic through the new payment service.'),
    ).toBeInTheDocument();
    expect(section.getByText('sha256:operations')).toBeInTheDocument();
    expect(section.getByText('sha256:assessment')).toBeInTheDocument();
    expect(section.getByText('Policy decision').closest('.analysis-fact')).toHaveTextContent(
      'ALLOW',
    );
    expect(section.getByText('Policy version').closest('.analysis-fact')).toHaveTextContent(
      '2026-01',
    );
    expect(section.getByText('Risk taxonomy').closest('.analysis-fact')).toHaveTextContent(
      '2026-01',
    );
    expect(section.getByText('sha256:twin')).toBeInTheDocument();
    expect(
      section.getByText('Snapshot observed at').closest('.analysis-fact'),
    ).toHaveTextContent('2026-10-01 09:55:00 UTC');
    expect(section.getByText('Proposer issuer').closest('.analysis-fact')).toHaveTextContent(
      'https://issuer.example',
    );
    expect(section.getByText('Proposer subject').closest('.analysis-fact')).toHaveTextContent(
      'user-1',
    );
    expect(section.getByText('Approver issuer').closest('.analysis-fact')).toHaveTextContent(
      'Not reported',
    );
    expect(section.getByText('Approver subject').closest('.analysis-fact')).toHaveTextContent(
      'Not reported',
    );
    expect(section.getByText('Reason').closest('.analysis-fact')).toHaveTextContent(
      'Not reported',
    );
    expect(section.getByText('Expires at').closest('.analysis-fact')).toHaveTextContent(
      'Not reported',
    );
    expect(section.getByText('Created at').closest('.analysis-fact')).toHaveTextContent(
      '2026-10-01 09:00:00 UTC',
    );
    expect(section.getByText('Updated at').closest('.analysis-fact')).toHaveTextContent(
      '2026-10-01 10:00:00 UTC',
    );
    expect(section.getAllByText('AWAITING_APPROVAL').length).toBeGreaterThan(0);
  });

  it('renders operations and assessment as bounded inert disclosures', async () => {
    const user = userEvent.setup();
    const api = createApi({
      getLatestProposal: vi.fn().mockResolvedValue({
        ...PROPOSAL,
        title: '<script>alert("title")</script>',
        description: '<img src=x onerror=alert(1)>',
      }),
    });
    const { container } = render(<ProposalGovernance proposalApi={api} />);

    const heading = await selectProposal(user);
    const section = within(heading.closest('section'));

    expect(section.getByText('<script>alert("title")</script>')).toBeInTheDocument();
    expect(section.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(container.querySelectorAll('script')).toHaveLength(0);
    expect(container.querySelectorAll('img')).toHaveLength(0);

    const operations = section.getByText('Proposal operations (JSON)');
    expect(operations.closest('details')).not.toHaveAttribute('open');
    await user.click(operations);
    expect(operations.closest('details')).toHaveAttribute('open');
    expect(section.getByLabelText('Proposal operations (JSON)')).toHaveTextContent(
      'payments-api',
    );
    expect(section.getByLabelText('Proposal operations (JSON)')).toHaveTextContent(
      '<b>skip</b>',
    );

    const assessment = section.getByText('Proposal assessment (JSON)');
    await user.click(assessment);
    expect(section.getByLabelText('Proposal assessment (JSON)')).toHaveTextContent(
      'No blocked dependency.',
    );
  });

  it('shows a latest revision error with a retry that loads only the detail', async () => {
    const user = userEvent.setup();
    const getLatestProposal = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(ErrorKind.NOT_FOUND))
      .mockResolvedValueOnce(PROPOSAL);
    renderView(createApi({ getLatestProposal }));

    await user.click(
      await screen.findByRole('button', { name: /Change payment routing/i }),
    );

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(safeMessage(ErrorKind.NOT_FOUND));
    expect(alert).toHaveTextContent(/may have been removed/i);
    expect(getLatestProposal).toHaveBeenCalledTimes(1);

    await user.click(within(alert).getByRole('button', { name: /retry/i }));

    expect(
      await screen.findByRole('heading', { name: 'Latest revision' }),
    ).toBeInTheDocument();
    expect(getLatestProposal).toHaveBeenCalledTimes(2);
  });
});

describe('ProposalGovernance explicit history and audit', () => {
  it('loads history only after the explicit action and a revision only after selection', async () => {
    const user = userEvent.setup();
    const api = renderView();

    await selectProposal(user);
    expect(api.listProposalRevisions).not.toHaveBeenCalled();
    expect(api.listProposalAudit).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /show revision history/i }));

    expect(
      await screen.findByRole('heading', { name: 'Revision history' }),
    ).toBeInTheDocument();
    expect(api.listProposalRevisions).toHaveBeenCalledTimes(1);
    expect(api.listProposalRevisions).toHaveBeenCalledWith(KEY);
    expect(api.getProposalRevision).not.toHaveBeenCalled();
    expect(api.listProposalAudit).not.toHaveBeenCalled();
    expect(api.getLatestProposal).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: /Revision 1/i }));

    expect(
      await screen.findByRole('heading', { name: 'Revision 1' }),
    ).toBeInTheDocument();
    expect(api.getProposalRevision).toHaveBeenCalledTimes(1);
    expect(api.getProposalRevision).toHaveBeenCalledWith(KEY, 1);
    expect(api.listProposalRevisions).toHaveBeenCalledTimes(1);
    expect(api.listProposalAudit).not.toHaveBeenCalled();
    expect(api.getLatestProposal).toHaveBeenCalledTimes(1);
  });

  it('loads audit evidence only after the explicit action, in server order', async () => {
    const user = userEvent.setup();
    const api = renderView(createApi({ listProposalAudit: vi.fn().mockResolvedValue([AUDIT_TWO, AUDIT_ONE]) }));

    await selectProposal(user);
    await user.click(screen.getByRole('button', { name: /show audit trail/i }));

    expect(
      await screen.findByRole('heading', { name: 'Audit trail' }),
    ).toBeInTheDocument();
    expect(api.listProposalAudit).toHaveBeenCalledTimes(1);
    expect(api.listProposalAudit).toHaveBeenCalledWith(KEY);
    expect(api.listProposalRevisions).not.toHaveBeenCalled();
    expect(api.getProposalRevision).not.toHaveBeenCalled();

    const list = screen.getByRole('list', { name: 'Proposal audit events' });
    const events = within(list).getAllByRole('listitem');
    expect(events[0]).toHaveTextContent('Sequence 2');
    expect(events[0]).toHaveTextContent('FUTURE_ACTION');
    expect(events[0]).toHaveTextContent('FUTURE_OUTCOME');
    expect(events[1]).toHaveTextContent('Sequence 1');
    expect(events[1]).toHaveTextContent('CREATED');
    expect(events[0]).toHaveTextContent('sha256:assessment');
    expect(events[1]).toHaveTextContent('Not reported');
    expect(events[0]).toHaveTextContent('correlation-1');
    expect(events[1]).toHaveTextContent('sha256:previous');
    expect(events[1]).toHaveTextContent('sha256:event');
    expect(screen.getByText(/not a chain verification result/i)).toBeInTheDocument();
  });

  it('shows empty history and audit responses explicitly', async () => {
    const user = userEvent.setup();
    renderView(
      createApi({
        listProposalRevisions: vi.fn().mockResolvedValue([]),
        listProposalAudit: vi.fn().mockResolvedValue([]),
      }),
    );

    await selectProposal(user);
    await user.click(screen.getByRole('button', { name: /show revision history/i }));
    expect(await screen.findByText('No revisions')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /show audit trail/i }));
    expect(await screen.findByText('No audit events')).toBeInTheDocument();
  });

  it('shows history, audit, and revision errors without replacing the latest revision', async () => {
    const user = userEvent.setup();
    const api = renderView(
      createApi({
        listProposalRevisions: vi
          .fn()
          .mockRejectedValueOnce(new ApiError(ErrorKind.SERVER))
          .mockResolvedValueOnce([REVISION_ONE]),
        getProposalRevision: vi.fn().mockRejectedValue(new ApiError(ErrorKind.BAD_REQUEST)),
        listProposalAudit: vi.fn().mockRejectedValue(new ApiError(ErrorKind.NETWORK)),
      }),
    );

    await selectProposal(user);

    await user.click(screen.getByRole('button', { name: /show revision history/i }));
    let alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(safeMessage(ErrorKind.SERVER));
    expect(screen.getByRole('heading', { name: 'Latest revision' })).toBeInTheDocument();

    await user.click(within(alert).getByRole('button', { name: /retry/i }));
    await user.click(await screen.findByRole('button', { name: /Revision 1/i }));
    alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(safeMessage(ErrorKind.BAD_REQUEST));

    await user.click(screen.getByRole('button', { name: /show audit trail/i }));
    alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(safeMessage(ErrorKind.NETWORK));
    expect(screen.getByRole('heading', { name: 'Latest revision' })).toBeInTheDocument();
  });
});

describe('ProposalGovernance pagination and list states', () => {
  it('pages with the returned page, size, and total only', async () => {
    const user = userEvent.setup();
    const pageZero = {
      items: [PROPOSAL, { ...PROPOSAL, proposalKey: KEY_TWO, title: 'Second proposal' }],
      page: 0,
      size: 20,
      total: 45,
    };
    const pageOne = {
      items: [{ ...PROPOSAL, proposalKey: KEY_THREE, title: 'Third proposal' }],
      page: 1,
      size: 20,
      total: 45,
    };
    const listProposals = vi
      .fn()
      .mockResolvedValueOnce(pageZero)
      .mockResolvedValueOnce(pageOne)
      .mockResolvedValueOnce(pageZero);
    const api = renderView(createApi({ listProposals }));

    expect(await screen.findByText('Page 1 of 3 · 45 proposals')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled();

    await user.click(screen.getByRole('button', { name: 'Next page' }));

    expect(await screen.findByText('Page 2 of 3 · 45 proposals')).toBeInTheDocument();
    expect(screen.getByText('Third proposal')).toBeInTheDocument();
    expect(listProposals).toHaveBeenLastCalledWith({ page: 1, size: 20 });
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeEnabled();

    await user.click(screen.getByRole('button', { name: 'Previous page' }));

    expect(await screen.findByText('Page 1 of 3 · 45 proposals')).toBeInTheDocument();
    expect(listProposals).toHaveBeenLastCalledWith({ page: 0, size: 20 });
    expect(api.getLatestProposal).not.toHaveBeenCalled();
  });

  it('clears the selected detail when changing pages', async () => {
    const user = userEvent.setup();
    const pageZero = { ...PAGE, items: [PROPOSAL], total: 40 };
    const pageOne = { ...PAGE, items: [], page: 1, total: 40 };
    const listProposals = vi
      .fn()
      .mockResolvedValueOnce(pageZero)
      .mockResolvedValueOnce(pageOne);
    renderView(createApi({ listProposals }));

    await selectProposal(user);
    expect(screen.getByRole('heading', { name: 'Latest revision' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Next page' }));

    expect(await screen.findByText('No proposals on this page')).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Latest revision' }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('No proposal selected')).toBeInTheDocument();
  });

  it('shows an out-of-range empty page with previous-page navigation', async () => {
    const user = userEvent.setup();
    const listProposals = vi
      .fn()
      .mockResolvedValueOnce({ ...PAGE, total: 40 })
      .mockResolvedValueOnce({ items: [], page: 1, size: 20, total: 5 });
    renderView(createApi({ listProposals }));

    await screen.findByText('Page 1 of 2 · 40 proposals');
    await user.click(screen.getByRole('button', { name: 'Next page' }));

    expect(
      await screen.findByText('Page 2 is beyond the available range · 5 proposals'),
    ).toBeInTheDocument();
    expect(screen.getByText('No proposals on this page')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
  });

  it('shows an explicit empty state when no proposals exist', async () => {
    renderView(
      createApi({
        listProposals: vi
          .fn()
          .mockResolvedValue({ items: [], page: 0, size: 20, total: 0 }),
      }),
    );

    expect(await screen.findByText('No proposals')).toBeInTheDocument();
    expect(
      screen.getByText('The control plane returned no governed proposals.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 1 · 0 proposals')).toBeInTheDocument();
  });

  it('shows a list error with safe text and retries only the list', async () => {
    const user = userEvent.setup();
    const listProposals = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(ErrorKind.FORBIDDEN))
      .mockResolvedValueOnce(PAGE);
    const api = renderView(createApi({ listProposals }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(safeMessage(ErrorKind.FORBIDDEN));
    expect(alert).toHaveTextContent(/TWIN_READ/i);
    expect(api.getLatestProposal).not.toHaveBeenCalled();

    await user.click(within(alert).getByRole('button', { name: /retry/i }));

    expect(await screen.findByText('Change payment routing')).toBeInTheDocument();
    expect(listProposals).toHaveBeenCalledTimes(2);
    expect(api.getLatestProposal).not.toHaveBeenCalled();
  });
});
