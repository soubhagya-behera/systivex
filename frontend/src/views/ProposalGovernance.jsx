import { useCallback, useId, useState } from 'react';
import { EmptyPanel, ErrorPanel, LoadingPanel } from '../components/StatePanels.jsx';
import { useAsyncResource } from '../hooks/useAsyncResource.js';
import { ErrorKind } from '../api/errors.js';
import { formatInstant, pluralize, prettyJson } from '../lib/format.js';

const PAGE_SIZE = 20;

const ERROR_HINT = {
  [ErrorKind.BAD_REQUEST]:
    'The proposal request was invalid. This view sends only the approved read paths and list page parameters.',
  [ErrorKind.UNAUTHORIZED]:
    'The proposal read was rejected. Provide a fresh bearer token and retry.',
  [ErrorKind.FORBIDDEN]:
    'Proposal reads require the TWIN_READ role. No write or approval request was attempted.',
  [ErrorKind.NOT_FOUND]:
    'The selected proposal or revision was not found. It may have been removed from the control plane.',
  [ErrorKind.UNEXPECTED]:
    'The proposal response was malformed, so no partial record is shown.',
};

function text(value, fallback = 'Not reported') {
  if (value === null || value === undefined || value === '') return fallback;
  return String(value);
}

function Fact({ label, children }) {
  return (
    <div className="analysis-fact">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function Instant({ value }) {
  if (value === null || value === undefined) {
    return <span className="hint">Not reported</span>;
  }
  return <time dateTime={value}>{formatInstant(value)}</time>;
}

function Hash({ value }) {
  if (value === null || value === undefined) {
    return <span className="hint">Not reported</span>;
  }
  return <span className="mono proposal-hash">{value}</span>;
}

function JsonBlock({ label, value, description }) {
  if (value === null || value === undefined) {
    return <p className="hint">{description} not reported.</p>;
  }
  return (
    <details className="proposal-json">
      <summary>{label}</summary>
      <pre className="proposal-json__body" aria-label={label}>
        {prettyJson(value)}
      </pre>
    </details>
  );
}

function StatusBadge({ status }) {
  return (
    <span className="badge proposal-status" data-status={String(status).toLowerCase()}>
      {status}
    </span>
  );
}

function ProposalDetail({ heading, proposal }) {
  const headingId = useId();

  return (
    <section className="analysis-section proposal-detail" aria-labelledby={headingId}>
      <div className="analysis-section__head">
        <div>
          <h2 className="analysis-section__title" id={headingId}>
            {heading}
          </h2>
          <p className="hint">
            One stored proposal revision, displayed without change. This view does not
            advance, approve, reject, or execute a proposal.
          </p>
        </div>
        <StatusBadge status={proposal.status} />
      </div>

      <dl className="analysis-facts">
        <Fact label="Proposal key">
          <span className="mono proposal-hash">{proposal.proposalKey}</span>
        </Fact>
        <Fact label="Revision number">{proposal.revisionNumber}</Fact>
        <Fact label="Status">{proposal.status}</Fact>
        <Fact label="Title">
          <span className="proposal-text">{proposal.title}</span>
        </Fact>
        <Fact label="Description">
          <span className="proposal-text">{text(proposal.description)}</span>
        </Fact>
        <Fact label="Operations hash">
          <Hash value={proposal.operationsHash} />
        </Fact>
        <Fact label="Assessment hash">
          <Hash value={proposal.assessmentHash} />
        </Fact>
        <Fact label="Policy decision">{text(proposal.policyDecision)}</Fact>
        <Fact label="Policy version">{text(proposal.policyVersion)}</Fact>
        <Fact label="Risk taxonomy">{text(proposal.riskTaxonomy)}</Fact>
        <Fact label="Twin fingerprint">
          <Hash value={proposal.twinFingerprint} />
        </Fact>
        <Fact label="Snapshot observed at">
          <Instant value={proposal.snapshotObservedAt} />
        </Fact>
        <Fact label="Proposer issuer">{text(proposal.proposerIssuer)}</Fact>
        <Fact label="Proposer subject">
          <span className="mono">{text(proposal.proposerSubject)}</span>
        </Fact>
        <Fact label="Approver issuer">{text(proposal.approverIssuer)}</Fact>
        <Fact label="Approver subject">
          <span className="mono">{text(proposal.approverSubject)}</span>
        </Fact>
        <Fact label="Reason">
          <span className="proposal-text">{text(proposal.reason)}</span>
        </Fact>
        <Fact label="Expires at">
          <Instant value={proposal.expiresAt} />
        </Fact>
        <Fact label="Created at">
          <Instant value={proposal.createdAt} />
        </Fact>
        <Fact label="Updated at">
          <Instant value={proposal.updatedAt} />
        </Fact>
      </dl>

      <h3 className="analysis-subtitle">Operations document</h3>
      <JsonBlock
        label="Proposal operations (JSON)"
        value={proposal.operations}
        description="Operations document"
      />

      <h3 className="analysis-subtitle">Assessment document</h3>
      <JsonBlock
        label="Proposal assessment (JSON)"
        value={proposal.assessment}
        description="Assessment document"
      />

      <p className="hint">
        Json documents and all text are rendered inertly as plain text. Hashes are
        shown as stored and are not recomputed here.
      </p>
    </section>
  );
}

function ProposalList({ page, selectedKey, onSelect }) {
  return (
    <section className="proposal-list-section" aria-label="Proposal list">
      <ul className="list proposal-list" aria-label="Proposals">
        {page.items.map((proposal) => (
          <li key={proposal.proposalKey}>
            <button
              type="button"
              className="list__item proposal-list__item"
              aria-pressed={proposal.proposalKey === selectedKey}
              onClick={() => onSelect(proposal.proposalKey)}
            >
              <span className="proposal-list__head">
                <span className="list__name proposal-list__title">
                  {text(proposal.title, 'Untitled proposal')}
                </span>
                <StatusBadge status={proposal.status} />
              </span>
              <span className="list__meta proposal-list__meta">
                <span className="mono">{proposal.proposalKey}</span>
                <span>Revision {proposal.revisionNumber}</span>
                <span>Updated {formatInstant(proposal.updatedAt)}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function Pagination({ page, onPrevious, onNext, busy }) {
  const totalPages = Math.max(1, Math.ceil(page.total / page.size));
  const beyond = page.page >= totalPages && page.total > 0;
  const nextAvailable = (page.page + 1) * page.size < page.total;

  return (
    <div className="proposal-pagination" role="group" aria-label="Proposal pages">
      <button
        type="button"
        className="btn"
        onClick={onPrevious}
        disabled={busy || page.page <= 0}
      >
        Previous page
      </button>
      <span className={beyond ? 'proposal-pagination__beyond' : 'hint'}>
        {beyond
          ? `Page ${page.page + 1} is beyond the available range · ${pluralize(
              page.total,
              'proposal',
            )}`
          : `Page ${page.page + 1} of ${totalPages} · ${pluralize(
              page.total,
              'proposal',
            )}`}
      </span>
      <button
        type="button"
        className="btn"
        onClick={onNext}
        disabled={busy || !nextAvailable}
      >
        Next page
      </button>
    </div>
  );
}

export function ProposalGovernance({ proposalApi }) {
  const [pageNumber, setPageNumber] = useState(0);
  const [selectedKey, setSelectedKey] = useState(null);
  const [panel, setPanel] = useState('none');
  const [revisionNumber, setRevisionNumber] = useState(null);

  const list = useAsyncResource(
    () => proposalApi.listProposals({ page: pageNumber, size: PAGE_SIZE }),
    [proposalApi, pageNumber],
  );

  const latest = useAsyncResource(
    () => (selectedKey ? proposalApi.getLatestProposal(selectedKey) : null),
    [proposalApi, selectedKey],
  );

  const history = useAsyncResource(
    () =>
      selectedKey && panel === 'history'
        ? proposalApi.listProposalRevisions(selectedKey)
        : null,
    [proposalApi, selectedKey, panel],
  );

  const audit = useAsyncResource(
    () =>
      selectedKey && panel === 'audit'
        ? proposalApi.listProposalAudit(selectedKey)
        : null,
    [proposalApi, selectedKey, panel],
  );

  const revision = useAsyncResource(
    () =>
      selectedKey && revisionNumber !== null
        ? proposalApi.getProposalRevision(selectedKey, revisionNumber)
        : null,
    [proposalApi, selectedKey, revisionNumber],
  );

  const latestData =
    latest.data && latest.data.proposalKey === selectedKey ? latest.data : null;
  const latestLoading = Boolean(selectedKey) && !latest.error && !latestData;

  const historyData =
    panel === 'history' && Array.isArray(history.data) ? history.data : null;
  const auditData = panel === 'audit' && Array.isArray(audit.data) ? audit.data : null;

  const revisionData =
    revision.data &&
    revision.data.proposalKey === selectedKey &&
    revision.data.revisionNumber === revisionNumber
      ? revision.data
      : null;

  const page = list.status === 'success' ? list.data : null;
  const listLoading = !list.error && !page;

  const selectProposal = useCallback((key) => {
    setSelectedKey(key);
    setPanel('none');
    setRevisionNumber(null);
  }, []);

  const clearSelection = useCallback(() => {
    setSelectedKey(null);
    setPanel('none');
    setRevisionNumber(null);
  }, []);

  const changePage = useCallback((next) => {
    setSelectedKey(null);
    setPanel('none');
    setRevisionNumber(null);
    setPageNumber(next);
  }, []);

  const togglePanel = useCallback((next) => {
    setPanel((current) => (current === next ? 'none' : next));
  }, []);

  const latestHint = ERROR_HINT[latest.error?.kind] ?? null;
  const listHint = ERROR_HINT[list.error?.kind] ?? null;
  const historyHint = ERROR_HINT[history.error?.kind] ?? null;
  const auditHint = ERROR_HINT[audit.error?.kind] ?? null;
  const revisionHint = ERROR_HINT[revision.error?.kind] ?? null;

  return (
    <div>
      <div className="view-header">
        <div>
          <h1 className="view-header__title">Proposal Governance</h1>
          <p className="view-header__hint">
            Read-only governed proposal records. Entering this view loads only the
            first proposal page; proposal details, revisions, and audit evidence load
            only after you select them.
          </p>
        </div>
      </div>

      <div className="notice" role="note">
        <strong>Display only.</strong> This view cannot create, withdraw, assess,
        submit, amend, approve, reject, or execute a proposal. An APPROVED record is
        terminal evidence and authorizes no execution.
      </div>

      {list.error ? (
        <ErrorPanel error={list.error} onRetry={list.reload} hint={listHint} />
      ) : listLoading || !page ? (
        <LoadingPanel label="Loading proposal page" />
      ) : (
        <>
          {page.items.length === 0 ? (
            <EmptyPanel
              title={page.total === 0 ? 'No proposals' : 'No proposals on this page'}
              body={
                page.total === 0
                  ? 'The control plane returned no governed proposals.'
                  : 'The server returned an empty page. Use Previous page to return to an available page.'
              }
            />
          ) : (
            <ProposalList
              page={page}
              selectedKey={selectedKey}
              onSelect={selectProposal}
            />
          )}
          <Pagination
            page={page}
            onPrevious={() => changePage(page.page - 1)}
            onNext={() => changePage(page.page + 1)}
            busy={listLoading}
          />
        </>
      )}

      {selectedKey ? (
        <section className="proposal-selection" aria-label="Selected proposal">
          <div className="analysis-action proposal-selection__head">
            <div className="analysis-selection">
              <span className="analysis-selection__label">Selected proposal</span>
              <span className="mono proposal-hash">{selectedKey}</span>
              <button type="button" className="btn" onClick={clearSelection}>
                Clear selection
              </button>
            </div>
            <div className="proposal-actions">
              <button
                type="button"
                className="btn"
                aria-expanded={panel === 'history'}
                onClick={() => togglePanel('history')}
              >
                {panel === 'history' ? 'Hide revision history' : 'Show revision history'}
              </button>
              <button
                type="button"
                className="btn"
                aria-expanded={panel === 'audit'}
                onClick={() => togglePanel('audit')}
              >
                {panel === 'audit' ? 'Hide audit trail' : 'Show audit trail'}
              </button>
            </div>
          </div>

          {latest.error ? (
            <ErrorPanel error={latest.error} onRetry={latest.reload} hint={latestHint} />
          ) : latestLoading ? (
            <LoadingPanel label="Loading latest proposal revision" />
          ) : latestData ? (
            <ProposalDetail heading="Latest revision" proposal={latestData} />
          ) : null}

          {panel === 'history' ? (
            <section
              className="analysis-section proposal-panel"
              aria-label="Revision history"
            >
              <div className="analysis-section__head">
                <div>
                  <h2 className="analysis-section__title">Revision history</h2>
                  <p className="hint">
                    The server&apos;s unpaginated revision list, displayed in returned
                    order. Choose a revision to load its stored detail.
                  </p>
                </div>
                <span className="badge">GET /revisions</span>
              </div>

              {history.error ? (
                <ErrorPanel
                  error={history.error}
                  onRetry={history.reload}
                  hint={historyHint}
                />
              ) : historyData === null ? (
                <LoadingPanel label="Loading proposal revision history" />
              ) : historyData.length === 0 ? (
                <EmptyPanel
                  title="No revisions"
                  body="The server returned an empty revision history for this proposal."
                />
              ) : (
                <ul className="list proposal-history" aria-label="Proposal revisions">
                  {historyData.map((item) => (
                    <li key={`${item.proposalKey}:${item.revisionNumber}`}>
                      <button
                        type="button"
                        className="list__item proposal-history__item"
                        aria-pressed={item.revisionNumber === revisionNumber}
                        onClick={() => setRevisionNumber(item.revisionNumber)}
                      >
                        <span className="proposal-list__head">
                          <span className="list__name">
                            Revision {item.revisionNumber}
                          </span>
                          <StatusBadge status={item.status} />
                        </span>
                        <span className="list__meta proposal-list__meta">
                          <span>Created {formatInstant(item.createdAt)}</span>
                          <span>Updated {formatInstant(item.updatedAt)}</span>
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          ) : null}

          {panel === 'history' && revisionNumber !== null ? (
            revision.error ? (
              <ErrorPanel
                error={revision.error}
                onRetry={revision.reload}
                hint={revisionHint}
              />
            ) : revisionData ? (
              <ProposalDetail
                heading={`Revision ${revisionData.revisionNumber}`}
                proposal={revisionData}
              />
            ) : (
              <LoadingPanel label={`Loading proposal revision ${revisionNumber}`} />
            )
          ) : null}

          {panel === 'audit' ? (
            <section className="analysis-section proposal-panel" aria-label="Audit trail">
              <div className="analysis-section__head">
                <div>
                  <h2 className="analysis-section__title">Audit trail</h2>
                  <p className="hint">
                    Server-ordered audit evidence for this proposal. This is not a
                    chain verification result; use Audit Integrity for that check.
                  </p>
                </div>
                <span className="badge">GET /audit</span>
              </div>

              {audit.error ? (
                <ErrorPanel error={audit.error} onRetry={audit.reload} hint={auditHint} />
              ) : auditData === null ? (
                <LoadingPanel label="Loading proposal audit trail" />
              ) : auditData.length === 0 ? (
                <EmptyPanel
                  title="No audit events"
                  body="The server returned an empty audit trail for this proposal."
                />
              ) : (
                <ol className="proposal-audit" aria-label="Proposal audit events">
                  {auditData.map((event) => (
                    <li className="proposal-audit__item" key={event.id}>
                      <div className="proposal-audit__head">
                        <span className="mono">Sequence {event.seq}</span>
                        <span className="badge">{event.action}</span>
                        <span
                          className="badge"
                          data-outcome={String(event.outcome).toLowerCase()}
                        >
                          {event.outcome}
                        </span>
                        <Instant value={event.occurredAt} />
                      </div>
                      <dl className="analysis-facts analysis-facts--compact">
                        <Fact label="Event ID">
                          <span className="mono proposal-hash">{event.id}</span>
                        </Fact>
                        <Fact label="Sequence">{event.seq}</Fact>
                        <Fact label="Occurred at">
                          <Instant value={event.occurredAt} />
                        </Fact>
                        <Fact label="Actor issuer">{text(event.actorIssuer)}</Fact>
                        <Fact label="Actor subject">
                          <span className="mono">{event.actorSubject}</span>
                        </Fact>
                        <Fact label="Action">{event.action}</Fact>
                        <Fact label="Outcome">{event.outcome}</Fact>
                        <Fact label="Proposal key">
                          <span className="mono proposal-hash">{event.proposalKey}</span>
                        </Fact>
                        <Fact label="Revision number">{event.revisionNumber}</Fact>
                        <Fact label="Operations hash">
                          <Hash value={event.operationsHash} />
                        </Fact>
                        <Fact label="Assessment hash">
                          <Hash value={event.assessmentHash} />
                        </Fact>
                        <Fact label="Reason">
                          <span className="proposal-text">{text(event.reason)}</span>
                        </Fact>
                        <Fact label="Correlation ID">
                          <span className="mono">{text(event.correlationId)}</span>
                        </Fact>
                        <Fact label="Previous hash">
                          <Hash value={event.prevHash} />
                        </Fact>
                        <Fact label="Event hash">
                          <Hash value={event.eventHash} />
                        </Fact>
                      </dl>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          ) : null}
        </section>
      ) : page ? (
        <EmptyPanel
          title="No proposal selected"
          body="Select a proposal from the list to load its latest revision. Revision history and audit data remain unrequested until you choose them."
        />
      ) : null}
    </div>
  );
}
