import { useCallback, useId, useMemo, useState } from 'react';
import { SearchBar, TypeFilter } from '../components/SearchBar.jsx';
import { EmptyPanel, ErrorPanel, LoadingPanel } from '../components/StatePanels.jsx';
import { useAsyncResource } from '../hooks/useAsyncResource.js';
import { ErrorKind } from '../api/errors.js';
import { filterEntities } from '../lib/filter.js';
import { formatInstant, titleCaseType } from '../lib/format.js';

const TRUNCATION_REASON = {
  'node-cap': 'node cap reached',
  'path-cap': 'evidence path cap reached',
};

const ERROR_HINT = {
  [ErrorKind.BAD_REQUEST]:
    'The analysis request was invalid. This view sends only the selected root UUID and no optional parameters.',
  [ErrorKind.NOT_FOUND]:
    'The selected root may no longer exist in the twin. Refresh the snapshot or choose another entity.',
  [ErrorKind.NO_TOKEN]: 'No authenticated analysis request was made.',
  [ErrorKind.UNEXPECTED]:
    'One or both analysis responses were malformed, so no partial result is shown.',
};

function text(value, fallback = 'Not reported') {
  if (value === null || value === undefined || value === '') return fallback;
  return String(value);
}

function truncationReason(value) {
  if (!value) return 'Reason not reported';
  return TRUNCATION_REASON[value] ?? String(value);
}

function Fact({ label, children }) {
  return (
    <div className="analysis-fact">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function DirectionLegend() {
  return (
    <ul className="analysis-legend" aria-label="Traversal direction meanings">
      <li>
        <span className="badge">FORWARD</span> follows the stored source to target
        direction.
      </li>
      <li>
        <span className="badge">REVERSE</span> walks against the stored direction,
        from the stored target back to its stored source.
      </li>
    </ul>
  );
}

function EvidencePaths({ entityName, paths }) {
  const [open, setOpen] = useState(false);
  const panelId = useId();

  return (
    <div className="analysis-disclosure">
      <button
        type="button"
        className="analysis-disclosure__toggle"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((value) => !value)}
      >
        {open ? 'Hide' : 'Show'} evidence paths for {entityName} ({paths.length})
      </button>
      {open ? (
        <div className="analysis-disclosure__body" id={panelId}>
          <p className="hint">
            Each path is a complete ordered evidence chain. Direction describes how
            the stored relationship was traversed, not confirmed breakage.
          </p>
          <DirectionLegend />
          {paths.length === 0 ? (
            <p className="hint">No evidence path was included for this entity.</p>
          ) : (
            paths.map((path, pathIndex) => {
              const steps = Array.isArray(path?.steps) ? path.steps : [];
              return (
                <article
                  className="analysis-path"
                  key={`${pathIndex}-${path?.targetId ?? 'path'}`}
                >
                  <h4 className="analysis-path__title">
                    Path {pathIndex + 1} · target{' '}
                    <span className="mono">
                      {text(path?.targetExternalRef ?? path?.targetId)}
                    </span>{' '}
                    · depth {text(path?.depth)}
                  </h4>
                  <p className="analysis-explanation">
                    {text(
                      path?.explanation,
                      'No explanation was provided for this evidence path.',
                    )}
                  </p>
                  {steps.length === 0 ? (
                    <p className="hint">No ordered steps were included for this path.</p>
                  ) : (
                    <ol
                      className="analysis-steps"
                      aria-label={`Ordered steps for evidence path ${pathIndex + 1}`}
                    >
                      {steps.map((step, stepIndex) => (
                        <li
                          className="analysis-step"
                          key={`${stepIndex}-${step?.sourceId ?? ''}-${step?.targetId ?? ''}`}
                        >
                          <div className="analysis-step__route mono">
                            <span>{text(step?.sourceExternalRef ?? step?.sourceId)}</span>
                            <span aria-hidden="true"> → </span>
                            <span>{text(step?.targetExternalRef ?? step?.targetId)}</span>
                          </div>
                          <div className="analysis-step__meta">
                            <span className="badge">
                              {step?.type ? titleCaseType(step.type) : 'Type not reported'}
                            </span>
                            <span className="badge">
                              {step?.direction ? step.direction : 'Direction not reported'}
                            </span>
                          </div>
                        </li>
                      ))}
                    </ol>
                  )}
                </article>
              );
            })
          )}
        </div>
      ) : null}
    </div>
  );
}

function ImpactSection({ impact }) {
  const headingId = useId();
  const impacted = Array.isArray(impact?.impacted) ? impact.impacted : [];
  const startedFrom = Array.isArray(impact?.startedFrom) ? impact.startedFrom : [];
  const limits =
    impact?.limits && typeof impact.limits === 'object' ? impact.limits : null;

  const pathsByTarget = useMemo(() => {
    const grouped = new Map();
    const paths = Array.isArray(impact?.paths) ? impact.paths : [];
    paths.forEach((path, index) => {
      const key = path?.targetId ?? `unknown-${index}`;
      if (!grouped.has(key)) grouped.set(key, []);
      grouped.get(key).push(path);
    });
    return grouped;
  }, [impact]);

  const truncated = impact?.truncated === true;
  const cycle = impact?.cycleEncountered === true;

  return (
    <section className="analysis-section" aria-labelledby={headingId}>
      <div className="analysis-section__head">
        <div>
          <h2 className="analysis-section__title" id={headingId}>
            Impact findings
          </h2>
          <p className="hint">
            Potentially affected components from one read-only traversal. Starts are
            listed separately and are not counted as impacted.
          </p>
        </div>
        <span className="badge">GET /api/v1/twin/impact</span>
      </div>

      <dl className="analysis-facts">
        <Fact label="Impact computed at">
          <time dateTime={impact?.snapshotObservedAt || undefined}>
            {formatInstant(impact?.snapshotObservedAt)}
          </time>
        </Fact>
        <Fact label="Twin entities read">{text(impact?.entityCount)}</Fact>
        <Fact label="Twin relationships read">{text(impact?.relationshipCount)}</Fact>
        <Fact label="Traversal nodes">{text(impact?.nodeCount)}</Fact>
        <Fact label="Evidence paths">{text(impact?.pathCount)}</Fact>
        <Fact label="Max depth requested">{text(impact?.maxDepth)}</Fact>
      </dl>

      <div className="analysis-flags">
        <div className="analysis-flag" data-tone={truncated ? 'warn' : 'ok'}>
          <strong>{truncated ? 'Truncated' : 'No truncation reported'}</strong>
          <span>
            {truncated
              ? `Reason: ${truncationReason(impact?.truncationReason)}.`
              : 'No completeness is implied when truncation is not reported.'}
          </span>
        </div>
        <div className="analysis-flag" data-tone={cycle ? 'warn' : 'neutral'}>
          <strong>{cycle ? 'Cycle encountered' : 'No cycle reported'}</strong>
          <span>
            {cycle
              ? 'A directed cycle was detected and that path expansion was stopped.'
              : 'No directed cycle was reported for this traversal.'}
          </span>
        </div>
      </div>

      <div className="analysis-limits">
        <span className="analysis-limits__label">Traversal limits</span>
        {limits ? (
          <span>
            Starts {text(limits.maxStarts)} · allowed depth{' '}
            {text(limits.maxDepthAllowed)} · nodes {text(limits.maxNodes)} · paths{' '}
            {text(limits.maxPaths)}
          </span>
        ) : (
          <span>Limits not reported.</span>
        )}
      </div>

      <p className="analysis-note">
        {text(impact?.note, 'No impact advisory note was provided.')}
      </p>

      <h3 className="analysis-subtitle">Analysis starts ({startedFrom.length})</h3>
      {startedFrom.length === 0 ? (
        <p className="hint">No analysis starts were reported.</p>
      ) : (
        <ul className="analysis-starts">
          {startedFrom.map((start, index) => (
            <li key={start?.id ?? index}>
              <span className="analysis-starts__name">{text(start?.name)}</span>{' '}
              <span className="badge">{titleCaseType(start?.type)}</span>{' '}
              <span className="mono">{text(start?.externalRef ?? start?.id)}</span>
            </li>
          ))}
        </ul>
      )}

      <h3 className="analysis-subtitle">Impacted entities ({impacted.length})</h3>
      {impacted.length === 0 ? (
        <EmptyPanel
          title="No impacted entities"
          body="The impact analysis reported no affected entities outside the analysis starts."
        />
      ) : (
        <ul className="analysis-entity-list" aria-label="Impacted entities">
          {impacted.map((entity, index) => {
            const name = text(entity?.name, 'Unnamed entity');
            return (
              <li className="analysis-entity" key={entity?.id ?? index}>
                <div className="analysis-entity__head">
                  <h4 className="analysis-entity__name">{name}</h4>
                  <span className="badge badge--type">{titleCaseType(entity?.type)}</span>
                </div>
                <dl className="analysis-facts analysis-facts--compact">
                  <Fact label="Type">{titleCaseType(entity?.type)}</Fact>
                  <Fact label="External ref">
                    <span className="mono">
                      {text(entity?.externalRef, 'No external reference')}
                    </span>
                  </Fact>
                  <Fact label="Depth">{text(entity?.depth)}</Fact>
                  <Fact label="Via start">
                    <span className="mono">{text(entity?.viaStart)}</span>
                  </Fact>
                </dl>
                <EvidencePaths
                  entityName={name}
                  paths={pathsByTarget.get(entity?.id) ?? []}
                />
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

function RiskSection({ risk }) {
  const headingId = useId();
  const risks = Array.isArray(risk?.risks) ? risk.risks : [];

  return (
    <section className="analysis-section" aria-labelledby={headingId}>
      <div className="analysis-section__head">
        <div>
          <h2 className="analysis-section__title" id={headingId}>
            Risk findings
          </h2>
          <p className="hint">
            Advisory classification in the backend&apos;s severity order. This view
            does not re-sort risk entries.
          </p>
        </div>
        <span className="badge">GET /api/v1/twin/risk</span>
      </div>

      <dl className="analysis-facts">
        <Fact label="Risk computed at">
          <time dateTime={risk?.snapshotObservedAt || undefined}>
            {formatInstant(risk?.snapshotObservedAt)}
          </time>
        </Fact>
        <Fact label="Taxonomy">{text(risk?.taxonomyVersion)}</Fact>
        <Fact label="Twin entities read">{text(risk?.entityCount)}</Fact>
        <Fact label="Twin relationships read">{text(risk?.relationshipCount)}</Fact>
        <Fact label="Max depth requested">{text(risk?.maxDepth)}</Fact>
      </dl>

      <div className="notice" role="note">
        <strong>Risk completeness caveat.</strong> The risk response has no truncation
        or cycle fields. This view cannot verify risk completeness or whether risk
        traversal was truncated.
      </div>

      <p className="hint">
        <strong>Uncertain</strong> means the evidence needed to judge is missing or
        stale. <strong>Stale</strong> means runtime evidence is older than 30 minutes,
        or absent for a service. They are separate flags; stale evidence is also
        treated as uncertain.
      </p>

      <p className="analysis-note">
        {text(risk?.note, 'No risk advisory note was provided.')}
      </p>

      <h3 className="analysis-subtitle">Risk entries ({risks.length})</h3>
      {risks.length === 0 ? (
        <EmptyPanel
          title="No risk entries"
          body="The risk assessment reported no entries outside the analysis starts."
        />
      ) : (
        <ol
          className="risk-list"
          aria-label="Risk entries in backend severity order"
        >
          {risks.map((entry, index) => {
            const level = text(entry?.level, 'NOT REPORTED');
            const reasons = Array.isArray(entry?.reasons) ? entry.reasons : [];
            return (
              <li className="risk-entry" key={entry?.id ?? index}>
                <div className="risk-entry__head">
                  <div>
                    <h4 className="risk-entry__name">
                      {text(entry?.name, 'Unnamed entity')}
                    </h4>
                    <span className="mono">
                      {text(entry?.externalRef, 'No external reference')}
                    </span>
                  </div>
                  <span
                    className="badge risk-level"
                    data-level={text(entry?.level, 'unknown').toLowerCase()}
                  >
                    {level}
                  </span>
                </div>

                <dl className="analysis-facts analysis-facts--compact">
                  <Fact label="Type">{titleCaseType(entry?.type)}</Fact>
                  <Fact label="Depth">{text(entry?.depth)}</Fact>
                  <Fact label="Via start">
                    <span className="mono">{text(entry?.viaStart)}</span>
                  </Fact>
                  <Fact label="Uncertain">{entry?.uncertain === true ? 'Yes' : 'No'}</Fact>
                  <Fact label="Stale">{entry?.stale === true ? 'Yes' : 'No'}</Fact>
                </dl>

                <h5 className="analysis-reasons__title">Reasons</h5>
                {reasons.length === 0 ? (
                  <p className="hint">No reasons were reported.</p>
                ) : (
                  <ul className="analysis-reasons">
                    {reasons.map((reason, reasonIndex) => (
                      <li key={`${reasonIndex}-${String(reason)}`}>{String(reason)}</li>
                    ))}
                  </ul>
                )}
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}

function RootPicker({
  entities,
  entityTypes,
  rootId,
  onSelect,
  query,
  onQuery,
  types,
  onToggleType,
}) {
  const visible = useMemo(
    () => filterEntities(entities, { query, types }),
    [entities, query, types],
  );

  return (
    <section className="analysis-picker" aria-label="Root entity selection">
      <div className="controls">
        <SearchBar
          value={query}
          onChange={onQuery}
          label="Search root entities"
          placeholder="Search name, external ref, environment…"
        />
        <span className="hint">
          {visible.length} of {entities.length}
        </span>
      </div>
      <TypeFilter
        options={entityTypes}
        selected={types}
        onToggle={onToggleType}
        label="Filter root entities by type"
      />
      {entities.length === 0 ? (
        <EmptyPanel
          title="No entities available"
          body="Load a twin snapshot before selecting an analysis root."
        />
      ) : visible.length === 0 ? (
        <EmptyPanel
          title="No root matches"
          body="No entity matches the current search and type filters."
        />
      ) : (
        <ul className="list analysis-root-list" aria-label="Root entity candidates">
          {visible.map((entity) => (
            <li key={entity.id}>
              <button
                type="button"
                className="list__item"
                aria-pressed={entity.id === rootId}
                onClick={() => onSelect(entity.id)}
              >
                <span className="list__name">{entity.name}</span>{' '}
                <span className="badge badge--type">{titleCaseType(entity.type)}</span>
                <div className="list__meta">
                  {entity.externalRef ?? 'no external ref'} ·{' '}
                  {entity.environment ?? 'no environment'}
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export function ImpactRisk({ entities = [], entityTypes = [], analysisApi }) {
  const [rootId, setRootId] = useState(null);
  const [query, setQuery] = useState('');
  const [types, setTypes] = useState([]);
  const [request, setRequest] = useState(null);

  const root = entities.find((entity) => entity.id === rootId) ?? null;

  const { data, error, reload, reset } = useAsyncResource(async () => {
    if (!request) return null;
    const [impact, risk] = await Promise.all([
      analysisApi.getImpact({ from: request.from }),
      analysisApi.getRisk({ from: request.from }),
    ]);
    return { impact, risk, from: request.from, nonce: request.nonce };
  }, [analysisApi, request]);

  const current = request && data?.nonce === request.nonce ? data : null;
  const loading = Boolean(request) && !error && !current;

  const toggleType = useCallback((type) => {
    setTypes((previous) =>
      previous.includes(type)
        ? previous.filter((value) => value !== type)
        : [...previous, type],
    );
  }, []);

  const selectRoot = useCallback(
    (id) => {
      if (id === rootId) return;
      reset();
      setRootId(id);
      setRequest(null);
    },
    [reset, rootId],
  );

  const clearSelection = useCallback(() => {
    reset();
    setRootId(null);
    setRequest(null);
  }, [reset]);

  const analyze = useCallback(() => {
    if (!root) return;
    reset();
    setRequest((previous) => ({
      from: root.id,
      nonce: (previous?.nonce ?? 0) + 1,
    }));
  }, [reset, root]);

  const retry = useCallback(() => {
    reload();
  }, [reload]);

  const hint =
    ERROR_HINT[error?.kind] ??
    (error ? 'Both analyses must succeed before results are shown.' : null);

  return (
    <div>
      <div className="view-header">
        <div>
          <h1 className="view-header__title">Impact &amp; Risk</h1>
          <p className="view-header__hint">
            Run one read-only impact traversal and risk assessment from a single
            loaded entity. Nothing is requested until you choose Analyze.
          </p>
        </div>
      </div>

      <RootPicker
        entities={entities}
        entityTypes={entityTypes}
        rootId={rootId}
        onSelect={selectRoot}
        query={query}
        onQuery={setQuery}
        types={types}
        onToggleType={toggleType}
      />

      <div className="analysis-action">
        <div className="analysis-selection" aria-live="polite">
          {root ? (
            <>
              <span className="analysis-selection__label">Selected root</span>
              <span className="analysis-selection__name">{root.name}</span>
              <span className="badge badge--type">{titleCaseType(root.type)}</span>
              <span className="mono">{root.externalRef ?? root.id}</span>
              <button type="button" className="btn" onClick={clearSelection}>
                Clear selection
              </button>
            </>
          ) : (
            <span className="hint">Select one root entity to enable analysis.</span>
          )}
        </div>
        <button
          type="button"
          className="btn btn--primary"
          disabled={!root || loading}
          onClick={analyze}
        >
          Analyze impact and risk
        </button>
      </div>

      <p className="hint">
        One root only. Backend defaults apply: max depth 6 and reported types
        SERVICE, API, DATABASE, and TABLE. Type filtering affects reported impacts,
        not traversal through other entity types.
      </p>

      {!request ? (
        <EmptyPanel
          title="No analysis yet"
          body="Select one root entity, then choose Analyze to request both analyses together."
        />
      ) : error ? (
        <ErrorPanel error={error} onRetry={retry} hint={hint} />
      ) : loading ? (
        <LoadingPanel label="Running impact and risk analysis" />
      ) : current ? (
        <div className="analysis-results" aria-busy={loading}>
          <ImpactSection impact={current.impact} />
          <RiskSection risk={current.risk} />
        </div>
      ) : null}
    </div>
  );
}
