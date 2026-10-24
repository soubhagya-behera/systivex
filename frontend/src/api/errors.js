/**
 * Safe, centralized error taxonomy for the API client.
 *
 * Security: error messages are fixed, safe strings. Raw response bodies,
 * headers, tokens, JWT claims, and stack traces are never surfaced to the UI
 * (see THREAT_MODEL.md and DECISIONS.md D33).
 */
export const ErrorKind = {
  NO_TOKEN: 'no-token',
  UNAUTHORIZED: 'unauthorized',
  FORBIDDEN: 'forbidden',
  NOT_FOUND: 'not-found',
  SERVER: 'server',
  NETWORK: 'network',
  UNEXPECTED: 'unexpected',
};

const SAFE_MESSAGES = {
  [ErrorKind.NO_TOKEN]: 'Enter a bearer token before requesting System Twin data.',
  [ErrorKind.UNAUTHORIZED]:
    'Authentication required (401). The control plane did not accept the request.',
  [ErrorKind.FORBIDDEN]:
    'Access denied (403). The authenticated identity lacks the required TWIN_READ role.',
  [ErrorKind.NOT_FOUND]: 'The requested record was not found (404).',
  [ErrorKind.SERVER]:
    'The control plane returned a server error. Please retry shortly.',
  [ErrorKind.NETWORK]:
    'Could not reach the control plane. Confirm it is running locally on 127.0.0.1:8080.',
  [ErrorKind.UNEXPECTED]: 'The request failed unexpectedly.',
};

export function safeMessage(kind) {
  return SAFE_MESSAGES[kind] ?? SAFE_MESSAGES[ErrorKind.UNEXPECTED];
}

export class ApiError extends Error {
  constructor(kind, { status = null } = {}) {
    super(safeMessage(kind));
    this.name = 'ApiError';
    this.kind = kind;
    this.status = status;
  }
}

export function isAuthBlock(error) {
  return (
    error?.kind === ErrorKind.UNAUTHORIZED || error?.kind === ErrorKind.FORBIDDEN
  );
}
