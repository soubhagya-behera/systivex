import { useState } from 'react';

/**
 * Reads the connection token and stores it in memory only.
 *
 * A live OIDC redirect is intentionally not implemented: this deployment has no
 * configured issuer/client, so the operator pastes a bearer token they already
 * hold. Nothing is written to persistent storage and no fake IdP is used.
 */
export function TokenGate({ onSubmit, onPreview, previewLoading = false }) {
  const [value, setValue] = useState('');

  function handleSubmit(event) {
    event.preventDefault();
    if (value.trim()) {
      onSubmit(value);
      setValue('');
    }
  }

  return (
    <section className="state state--center" aria-labelledby="token-gate-title">
      <span className="state__icon" aria-hidden="true">
        ⚿
      </span>
      <h1 className="state__title" id="token-gate-title">
        Connect to the control plane
      </h1>
      <p className="state__body">
        Paste a bearer token to open the read-only System Twin. The token is held
        in memory for this tab only and is never stored, logged, or placed in the
        URL.
      </p>
      <form className="token-form" onSubmit={handleSubmit} aria-label="Connection token">
        <label htmlFor="token-input">Bearer token</label>
        <div className="row">
          <input
            id="token-input"
            className="input"
            type="password"
            autoComplete="off"
            spellCheck={false}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            placeholder="Paste token"
          />
          <button type="submit" className="btn btn--primary" disabled={!value.trim()}>
            Connect
          </button>
        </div>
        <p className="hint">
          Live OIDC sign-in is unavailable in this environment, so no redirect is
          attempted.
        </p>
      </form>
      {onPreview ? (
        <div className="preview-entry">
          <button
            type="button"
            className="btn"
            onClick={onPreview}
            disabled={previewLoading}
          >
            {previewLoading ? 'Loading preview…' : 'Open local preview (synthetic data)'}
          </button>
          <p className="hint">
            Development-only UI preview. Uses fictional sample records, makes no
            backend requests, and needs no bearer token.
          </p>
        </div>
      ) : null}
    </section>
  );
}
