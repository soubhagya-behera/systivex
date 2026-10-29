import { ApiError, ErrorKind } from './errors.js';

const AUDIT_VERIFY = '/api/v1/twin/audit/verify';

const STATUSES = ['OK', 'BROKEN'];

function malformed() {
  throw new ApiError(ErrorKind.UNEXPECTED);
}

function expectObject(value) {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    malformed();
  }
  return value;
}

function expectInteger(value) {
  if (!Number.isFinite(value) || !Number.isInteger(value)) {
    malformed();
  }
  return value;
}

function expectNullableInteger(value) {
  if (value === null) return value;
  return expectInteger(value);
}

function expectVerification(value) {
  const body = expectObject(value);
  if (!STATUSES.includes(body.status)) malformed();
  const eventCount = expectInteger(body.eventCount);
  const headSeq = expectInteger(body.headSeq);
  const brokenAtSeq = expectNullableInteger(body.brokenAtSeq);
  if (typeof body.detail !== 'string') malformed();
  return {
    status: body.status,
    eventCount,
    headSeq,
    brokenAtSeq,
    detail: body.detail,
  };
}

export function createAuditApi(client) {
  return {
    verify: () =>
      client
        .request(AUDIT_VERIFY, { method: 'GET' })
        .then((body) => expectVerification(body)),
  };
}

export { AUDIT_VERIFY };
