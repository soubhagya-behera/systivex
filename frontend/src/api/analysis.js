import { ApiError, ErrorKind } from './errors.js';

const IMPACT = '/api/v1/twin/impact';
const RISK = '/api/v1/twin/risk';

const IMPACT_ARRAYS = ['startedFrom', 'impacted', 'paths'];
const RISK_ARRAYS = ['startedFrom', 'risks'];

function expectObject(value) {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new ApiError(ErrorKind.UNEXPECTED);
  }
  return value;
}

function expectArrays(value, fields) {
  const body = expectObject(value);
  for (const field of fields) {
    if (!Array.isArray(body[field])) {
      throw new ApiError(ErrorKind.UNEXPECTED);
    }
  }
  return body;
}

function pathFor(base, from) {
  const params = new URLSearchParams();
  params.set('from', from);
  return `${base}?${params.toString()}`;
}

export function createAnalysisApi(client) {
  return {
    getImpact: ({ from } = {}) =>
      client
        .request(pathFor(IMPACT, from))
        .then((body) => expectArrays(body, IMPACT_ARRAYS)),
    getRisk: ({ from } = {}) =>
      client
        .request(pathFor(RISK, from))
        .then((body) => expectArrays(body, RISK_ARRAYS)),
  };
}

export { IMPACT, RISK };
