import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  PROPOSALS,
  createProposalsApi,
} from '../api/proposals.js';
import { createApiClient } from '../api/client.js';
import { ApiError, ErrorKind, safeMessage } from '../api/errors.js';

const KEY = '11111111-1111-1111-1111-111111111111';
const EVENT_ID = '22222222-2222-2222-2222-222222222222';

const PROPOSAL = {
  proposalKey: KEY,
  revisionNumber: 2,
  status: 'AWAITING_APPROVAL',
  title: 'Change payment routing',
  description: 'Route checkout traffic through the new payment service.',
  operations: [{ action: 'UPDATE', target: 'payments-api' }],
  operationsHash: 'sha256:operations',
  assessment: { decision: 'ALLOW', findings: [] },
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

const AUDIT = {
  id: EVENT_ID,
  seq: 7,
  occurredAt: '2026-10-01T10:00:00Z',
  actorIssuer: 'https://issuer.example',
  actorSubject: 'user-1',
  action: 'SUBMITTED',
  outcome: 'SUCCESS',
  proposalKey: KEY,
  revisionNumber: 2,
  operationsHash: 'sha256:operations',
  assessmentHash: null,
  reason: null,
  correlationId: 'correlation-1',
  prevHash: 'sha256:previous',
  eventHash: 'sha256:event',
};

function ok(body) {
  return { ok: true, status: 200, json: async () => body };
}

function setup(body, token = 'test-token') {
  const fetchImpl = vi.fn().mockResolvedValue(ok(body));
  const client = createApiClient({ getToken: () => token, fetchImpl });
  return { api: createProposalsApi(client), fetchImpl };
}

function responseFor(path) {
  if (path.endsWith('/audit')) return [AUDIT];
  if (path.endsWith('/revisions')) return [PROPOSAL];
  if (/\/revisions\/\d+$/.test(path)) return { ...PROPOSAL, revisionNumber: 1 };
  if (path.includes('?')) return PAGE;
  return PROPOSAL;
}

async function captureError(promise) {
  try {
    await promise;
  } catch (error) {
    return error;
  }
  throw new Error('expected the proposal request to reject');
}

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('proposals API paths', () => {
  it('calls exactly the five approved GET paths with no mutation verbs', async () => {
    const fetchImpl = vi.fn(async (url) => ok(responseFor(url)));
    const api = createProposalsApi(
      createApiClient({ getToken: () => 'test-token', fetchImpl }),
    );

    await api.listProposals();
    await api.getLatestProposal(KEY);
    await api.listProposalRevisions(KEY);
    await api.getProposalRevision(KEY, 1);
    await api.listProposalAudit(KEY);

    expect(fetchImpl.mock.calls.map(([path]) => path)).toEqual([
      `${PROPOSALS}?page=0&size=20`,
      `${PROPOSALS}/${KEY}`,
      `${PROPOSALS}/${KEY}/revisions`,
      `${PROPOSALS}/${KEY}/revisions/1`,
      `${PROPOSALS}/${KEY}/audit`,
    ]);
    for (const [path, options] of fetchImpl.mock.calls) {
      expect(path.startsWith('/api/v1/twin/proposals')).toBe(true);
      expect(options.method).toBe('GET');
      expect(options.headers.Accept).toBe('application/json');
      expect(options.headers.Authorization).toBe('Bearer test-token');
      expect(path).not.toContain('test-token');
    }
  });

  it('sends only page and size, with no filters or sorting parameters', async () => {
    const { api, fetchImpl } = setup(PAGE);

    await api.listProposals({ page: 2, size: 50 });

    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const [path, options] = fetchImpl.mock.calls[0];
    expect(path).toBe(`${PROPOSALS}?page=2&size=50`);
    expect(options.method).toBe('GET');
    const url = new URL(path, 'http://localhost');
    expect([...url.searchParams.keys()]).toEqual(['page', 'size']);
    expect(url.searchParams.getAll('page')).toEqual(['2']);
    expect(url.searchParams.getAll('size')).toEqual(['50']);
  });

  it('encodes proposal keys in the path', async () => {
    const { api, fetchImpl } = setup(PROPOSAL);

    await api.getLatestProposal('proposal key/with slash');

    expect(fetchImpl.mock.calls[0][0]).toBe(
      `${PROPOSALS}/proposal%20key%2Fwith%20slash`,
    );
  });

  it('keeps the bearer token in memory and out of browser storage and URLs', async () => {
    const { api, fetchImpl } = setup(PAGE);

    await api.listProposals();

    expect(fetchImpl.mock.calls[0][0]).not.toContain('test-token');
    expect(document.body.textContent).not.toContain('test-token');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('test-token');
  });

  it('sends no request when no token is available', async () => {
    const fetchImpl = vi.fn();
    const client = createApiClient({ getToken: () => null, fetchImpl });
    const api = createProposalsApi(client);

    await expect(api.listProposals()).rejects.toMatchObject({
      kind: ErrorKind.NO_TOKEN,
      message: safeMessage(ErrorKind.NO_TOKEN),
    });
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});

describe('proposals API validation', () => {
  it('accepts the full proposal list, revision, and audit shapes', async () => {
    const fetchImpl = vi.fn(async (path) => ok(responseFor(path)));
    const api = createProposalsApi(
      createApiClient({ getToken: () => 'test-token', fetchImpl }),
    );

    await expect(api.listProposals()).resolves.toEqual(PAGE);
    await expect(api.getLatestProposal(KEY)).resolves.toEqual(PROPOSAL);
    await expect(api.listProposalRevisions(KEY)).resolves.toEqual([PROPOSAL]);
    await expect(api.getProposalRevision(KEY, 1)).resolves.toEqual({
      ...PROPOSAL,
      revisionNumber: 1,
    });
    await expect(api.listProposalAudit(KEY)).resolves.toEqual([AUDIT]);
  });

  it('accepts unknown forward-compatible status, action, and outcome strings', async () => {
    const futureProposal = { ...PROPOSAL, status: 'FUTURE_STATUS' };
    const futureAudit = {
      ...AUDIT,
      action: 'FUTURE_ACTION',
      outcome: 'FUTURE_OUTCOME',
    };
    const fetchImpl = vi.fn(async (path) => {
      if (path.endsWith('/audit')) return ok([futureAudit]);
      if (path.endsWith('/revisions')) return ok([futureProposal]);
      if (path.includes('?')) return ok({ ...PAGE, items: [futureProposal] });
      return ok(futureProposal);
    });
    const api = createProposalsApi(
      createApiClient({ getToken: () => 'test-token', fetchImpl }),
    );

    await expect(api.getLatestProposal(KEY)).resolves.toEqual(futureProposal);
    await expect(api.listProposalRevisions(KEY)).resolves.toEqual([futureProposal]);
    await expect(api.listProposalAudit(KEY)).resolves.toEqual([futureAudit]);
  });

  it.each([
    ['a non-object list', []],
    ['a null list', null],
    ['a string list', 'not a page'],
    ['non-array items', { ...PAGE, items: null }],
    ['a negative page', { ...PAGE, page: -1 }],
    ['a fractional page', { ...PAGE, page: 1.5 }],
    ['a zero size', { ...PAGE, size: 0 }],
    ['a size above the maximum', { ...PAGE, size: 101 }],
    ['a fractional total', { ...PAGE, total: 1.5 }],
    ['a negative total', { ...PAGE, total: -1 }],
    ['a missing item status', { ...PAGE, items: [{ ...PROPOSAL, status: undefined }] }],
    ['a numeric item status', { ...PAGE, items: [{ ...PROPOSAL, status: 7 }] }],
    ['a null operations document', { ...PAGE, items: [{ ...PROPOSAL, operations: null }] }],
    ['a numeric description', { ...PAGE, items: [{ ...PROPOSAL, description: 4 }] }],
    ['an undefined assessment', { ...PAGE, items: [{ ...PROPOSAL, assessment: undefined }] }],
    [
      'a numeric snapshot timestamp',
      { ...PAGE, items: [{ ...PROPOSAL, snapshotObservedAt: 4 }] },
    ],
  ])('rejects %s on the list with a safe unexpected error', async (_label, body) => {
    const { api } = setup(body);

    const error = await captureError(api.listProposals());

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      kind: ErrorKind.UNEXPECTED,
      message: safeMessage(ErrorKind.UNEXPECTED),
    });
  });

  it.each([
    ['a missing operations hash', { ...PROPOSAL, operationsHash: undefined }],
    ['a null operations hash', { ...PROPOSAL, operationsHash: null }],
    ['a numeric assessment hash', { ...PROPOSAL, assessmentHash: 7 }],
    ['a numeric policy decision', { ...PROPOSAL, policyDecision: 7 }],
    ['a numeric created timestamp', { ...PROPOSAL, createdAt: 7 }],
    ['a missing updated timestamp', { ...PROPOSAL, updatedAt: undefined }],
  ])('rejects %s on a proposal detail', async (_label, body) => {
    const { api } = setup(body);

    const error = await captureError(api.getLatestProposal(KEY));

    expect(error).toMatchObject({ kind: ErrorKind.UNEXPECTED });
  });

  it.each([
    ['a non-array audit response', AUDIT],
    ['a missing event hash', [{ ...AUDIT, eventHash: undefined }]],
    ['a null previous hash', [{ ...AUDIT, prevHash: null }]],
    ['a fractional sequence', [{ ...AUDIT, seq: 1.5 }]],
    ['a negative sequence', [{ ...AUDIT, seq: -1 }]],
    ['a null occurred timestamp', [{ ...AUDIT, occurredAt: null }]],
    ['an object actor issuer', [{ ...AUDIT, actorIssuer: {} }]],
    ['a numeric reason', [{ ...AUDIT, reason: 4 }]],
    ['a numeric correlation id', [{ ...AUDIT, correlationId: 4 }]],
  ])('rejects %s on an audit trail', async (_label, body) => {
    const { api } = setup(body);

    const error = await captureError(api.listProposalAudit(KEY));

    expect(error).toMatchObject({ kind: ErrorKind.UNEXPECTED });
  });

  it('does not leak response content or the token in validation errors', async () => {
    const { api } = setup({
      ...PAGE,
      items: [{ ...PROPOSAL, status: 42, title: 'sensitive-response-body' }],
    });

    const error = await captureError(api.listProposals());

    expect(error.message).toBe(safeMessage(ErrorKind.UNEXPECTED));
    expect(error.message).not.toContain('test-token');
    expect(error.message).not.toContain('sensitive-response-body');
    expect(String(error.stack ?? '')).not.toContain('sensitive-response-body');
  });
});
