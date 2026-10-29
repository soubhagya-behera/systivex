import { useCallback, useId, useState } from 'react';
import { EmptyPanel, ErrorPanel, LoadingPanel } from '../components/StatePanels.jsx';
import { useAsyncResource } from '../hooks/useAsyncResource.js';
import { ErrorKind } from '../api/errors.js';

const ERROR_HINT = {
  [ErrorKind.BAD_REQUEST]:
    'The verification request was invalid. This view sends only the approved audit verification GET.',
  [ErrorKind.NOT_FOUND]:
    'The audit verification endpoint was not found on this control plane.',
  [ErrorKind.NO_TOKEN]: 'No authenticated verification request was made.',
  [ErrorKind.UNEXPECTED]:
    'The verification response was malformed, so no result is shown.',
};

function Fact({ label, children }) {
  return (
    <div className="analysis-fact">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function VerificationResult({ result }) {
  const headingId = useId();
  const ok = result.status === 'OK';
  const brokenAt =
    result.brokenAtSeq === null ? 'Not reported' : String(result.brokenAtSeq);

  return (
    <section className="analysis-section" aria-labelledby={headingId}>
      <div className="analysis-section__head">
        <div>
          <h2 className="analysis-section__title" id={headingId}>
            Audit chain verification
          </h2>
          <p className="hint">
            A bounded, read-only check of the stored hash chain. Verification never
            repairs or changes the chain.
          </p>
        </div>
        <span className="badge">GET /api/v1/twin/audit/verify</span>
      </div>

      <div className="analysis-flags">
        <div
          className="analysis-flag"
          data-tone={ok ? 'ok' : 'warn'}
          role="status"
          aria-live="polite"
          aria-label={
            ok ? 'Audit chain verification result: OK' : 'Audit chain verification result: BROKEN'
          }
        >
          <strong>{ok ? 'OK — chain verified' : 'BROKEN — chain integrity finding'}</strong>
          <span>
            {ok
              ? 'The backend reported a valid chain for the stored events.'
              : 'The backend reported a broken chain. This is a verification finding, not an HTTP error.'}
          </span>
        </div>
      </div>

      <dl className="analysis-facts">
        <Fact label="Status">
          <span className="mono">{result.status}</span>
        </Fact>
        <Fact label="Events verified">{String(result.eventCount)}</Fact>
        <Fact label="Head sequence">{String(result.headSeq)}</Fact>
        <Fact label="First broken sequence">{brokenAt}</Fact>
      </dl>

      <p className="analysis-note">
        <strong>Diagnostic detail.</strong> {result.detail}
      </p>
    </section>
  );
}

export function AuditIntegrity({ auditApi }) {
  const [request, setRequest] = useState(null);

  const { data, error, reload, reset } = useAsyncResource(async () => {
    if (!request) return null;
    const result = await auditApi.verify();
    return { result, nonce: request.nonce };
  }, [auditApi, request]);

  const current = request && data?.nonce === request.nonce ? data.result : null;
  const loading = Boolean(request) && !error && !current;

  const verify = useCallback(() => {
    reset();
    setRequest((previous) => ({ nonce: (previous?.nonce ?? 0) + 1 }));
  }, [reset]);

  const retry = useCallback(() => {
    reload();
  }, [reload]);

  const hint =
    ERROR_HINT[error?.kind] ??
    (error ? 'The audit chain could not be verified.' : null);

  return (
    <div>
      <div className="view-header">
        <div>
          <h1 className="view-header__title">Audit Integrity</h1>
          <p className="view-header__hint">
            Verify the governance audit chain with one explicit read-only request.
            Nothing is requested until you choose Verify audit chain.
          </p>
        </div>
      </div>

      <div className="analysis-action">
        <div className="analysis-selection">
          <span className="analysis-selection__label">Verification</span>
          <span className="hint">
            The result reflects the backend&apos;s stored chain at request time.
          </span>
        </div>
        <button
          type="button"
          className="btn btn--primary"
          disabled={loading}
          onClick={verify}
        >
          Verify audit chain
        </button>
      </div>

      {!request ? (
        <EmptyPanel
          title="No verification requested"
          body="Choose Verify audit chain to run one read-only integrity check."
        />
      ) : error ? (
        <ErrorPanel error={error} onRetry={retry} hint={hint} />
      ) : loading ? (
        <LoadingPanel label="Verifying audit chain" />
      ) : current ? (
        <div className="analysis-results" aria-busy={loading}>
          <VerificationResult result={current} />
        </div>
      ) : null}
    </div>
  );
}
