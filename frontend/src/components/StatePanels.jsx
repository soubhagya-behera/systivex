import { ErrorKind } from '../api/errors.js';

export function LoadingPanel({ label = 'Loading' }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="visually-hidden">{label}</span>
      <div className="skeleton-row" />
      <div className="skeleton-row" />
      <div className="skeleton-row short" />
    </div>
  );
}

export function EmptyPanel({ title = 'Nothing to show', body = null }) {
  return (
    <div className="state" role="status">
      <span className="state__icon" aria-hidden="true">
        ◍
      </span>
      <h3 className="state__title">{title}</h3>
      {body ? <p className="state__body">{body}</p> : null}
    </div>
  );
}

const VARIANT = {
  [ErrorKind.FORBIDDEN]: 'state--forbidden',
  [ErrorKind.UNAUTHORIZED]: 'state--forbidden',
};

export function ErrorPanel({ error, onRetry }) {
  const kind = error?.kind ?? ErrorKind.UNEXPECTED;
  return (
    <div className={`state state--error ${VARIANT[kind] ?? ''}`} role="alert">
      <span className="state__icon" aria-hidden="true">
        ⚠
      </span>
      <h3 className="state__title">Request failed</h3>
      <p className="state__body">{error?.message ?? 'The request failed.'}</p>
      {kind === ErrorKind.UNAUTHORIZED ? (
        <p className="hint">
          The token may be missing, malformed, or expired. Provide a fresh one.
        </p>
      ) : null}
      {kind === ErrorKind.FORBIDDEN ? (
        <p className="hint">This view only reads the twin; no writes are performed.</p>
      ) : null}
      {onRetry ? (
        <button type="button" className="btn" onClick={onRetry}>
          Retry
        </button>
      ) : null}
    </div>
  );
}
