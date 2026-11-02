import { ApiError, ErrorKind } from './errors.js';

const PROPOSALS = '/api/v1/twin/proposals';
const PROPOSAL_PAGE_SIZE = 20;
const PROPOSAL_PAGE_SIZE_MAX = 100;

function malformed() {
  throw new ApiError(ErrorKind.UNEXPECTED);
}

function expectObject(value) {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    malformed();
  }
  return value;
}

function expectArray(value) {
  if (!Array.isArray(value)) malformed();
  return value;
}

function expectInteger(value) {
  if (!Number.isFinite(value) || !Number.isInteger(value)) malformed();
  return value;
}

function expectNonNegativeInteger(value) {
  const integer = expectInteger(value);
  if (integer < 0) malformed();
  return integer;
}

function expectString(value) {
  if (typeof value !== 'string') malformed();
  return value;
}

function expectNonEmptyString(value) {
  const text = expectString(value);
  if (text.length === 0) malformed();
  return text;
}

function expectNullableString(value) {
  if (value === null) return value;
  return expectString(value);
}

function expectTimestamp(value) {
  return expectNonEmptyString(value);
}

function expectNullableTimestamp(value) {
  if (value === null) return value;
  return expectTimestamp(value);
}

function expectJson(value) {
  if (value === null || value === undefined) malformed();
  return value;
}

function expectNullableJson(value) {
  if (value === null) return value;
  if (value === undefined) malformed();
  return value;
}

function expectProposal(value) {
  const body = expectObject(value);
  return {
    proposalKey: expectNonEmptyString(body.proposalKey),
    revisionNumber: expectInteger(body.revisionNumber),
    status: expectNonEmptyString(body.status),
    title: expectString(body.title),
    description: expectNullableString(body.description),
    operations: expectJson(body.operations),
    operationsHash: expectNonEmptyString(body.operationsHash),
    assessment: expectNullableJson(body.assessment),
    assessmentHash: expectNullableString(body.assessmentHash),
    policyDecision: expectNullableString(body.policyDecision),
    policyVersion: expectNullableString(body.policyVersion),
    riskTaxonomy: expectNullableString(body.riskTaxonomy),
    twinFingerprint: expectNullableString(body.twinFingerprint),
    snapshotObservedAt: expectNullableTimestamp(body.snapshotObservedAt),
    proposerIssuer: expectNonEmptyString(body.proposerIssuer),
    proposerSubject: expectNonEmptyString(body.proposerSubject),
    approverIssuer: expectNullableString(body.approverIssuer),
    approverSubject: expectNullableString(body.approverSubject),
    reason: expectNullableString(body.reason),
    expiresAt: expectNullableTimestamp(body.expiresAt),
    createdAt: expectTimestamp(body.createdAt),
    updatedAt: expectTimestamp(body.updatedAt),
  };
}

function expectProposalPage(value) {
  const body = expectObject(value);
  const items = expectArray(body.items).map(expectProposal);
  const page = expectNonNegativeInteger(body.page);
  const size = expectInteger(body.size);
  if (size < 1 || size > PROPOSAL_PAGE_SIZE_MAX) malformed();
  const total = expectNonNegativeInteger(body.total);
  return { items, page, size, total };
}

function expectProposalArray(value) {
  return expectArray(value).map(expectProposal);
}

function expectAuditEvent(value) {
  const body = expectObject(value);
  return {
    id: expectNonEmptyString(body.id),
    seq: expectNonNegativeInteger(body.seq),
    occurredAt: expectTimestamp(body.occurredAt),
    actorIssuer: expectNullableString(body.actorIssuer),
    actorSubject: expectNonEmptyString(body.actorSubject),
    action: expectNonEmptyString(body.action),
    outcome: expectNonEmptyString(body.outcome),
    proposalKey: expectNonEmptyString(body.proposalKey),
    revisionNumber: expectInteger(body.revisionNumber),
    operationsHash: expectNullableString(body.operationsHash),
    assessmentHash: expectNullableString(body.assessmentHash),
    reason: expectNullableString(body.reason),
    correlationId: expectNullableString(body.correlationId),
    prevHash: expectNonEmptyString(body.prevHash),
    eventHash: expectNonEmptyString(body.eventHash),
  };
}

function expectAuditArray(value) {
  return expectArray(value).map(expectAuditEvent);
}

function encodeKey(key) {
  if (typeof key !== 'string' || key.length === 0) malformed();
  return encodeURIComponent(key);
}

function proposalPath(key) {
  return `${PROPOSALS}/${encodeKey(key)}`;
}

function pageQuery({ page = 0, size = PROPOSAL_PAGE_SIZE } = {}) {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(size));
  return params.toString();
}

export function createProposalsApi(client) {
  return {
    listProposals: (options = {}) =>
      client
        .request(`${PROPOSALS}?${pageQuery(options)}`, { method: 'GET' })
        .then((body) => expectProposalPage(body)),
    getLatestProposal: (key) =>
      client
        .request(proposalPath(key), { method: 'GET' })
        .then((body) => expectProposal(body)),
    listProposalRevisions: (key) =>
      client
        .request(`${proposalPath(key)}/revisions`, { method: 'GET' })
        .then((body) => expectProposalArray(body)),
    getProposalRevision: (key, revisionNumber) =>
      client
        .request(
          `${proposalPath(key)}/revisions/${expectInteger(revisionNumber)}`,
          { method: 'GET' },
        )
        .then((body) => expectProposal(body)),
    listProposalAudit: (key) =>
      client
        .request(`${proposalPath(key)}/audit`, { method: 'GET' })
        .then((body) => expectAuditArray(body)),
  };
}

export { PROPOSALS, PROPOSAL_PAGE_SIZE, PROPOSAL_PAGE_SIZE_MAX };
