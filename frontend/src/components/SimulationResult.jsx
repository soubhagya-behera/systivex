import { formatInstant } from '../lib/format.js';

export function text(value, fallback = 'Not reported') {
  if (value === null || value === undefined || value === '') return fallback;
  return String(value);
}

export function Fact({ label, children }) {
  return (
    <div className="analysis-fact">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function Instant({ label, value }) {
  return (
    <Fact label={label}>
      <time dateTime={value || undefined}>{formatInstant(value)}</time>
    </Fact>
  );
}

function ValueList({ values, empty = 'None reported.' }) {
  if (!Array.isArray(values) || values.length === 0) {
    return <p className="hint">{empty}</p>;
  }
  return (
    <ul className="simulation-value-list">
      {values.map((value, index) => (
        <li key={`${index}-${String(value)}`}>
          <span className="mono">{String(value)}</span>
        </li>
      ))}
    </ul>
  );
}

export function SettingsSummary({ settings }) {
  const includeTypes = Array.isArray(settings?.includeTypes)
    ? settings.includeTypes
    : [];
  return (
    <>
      <h3 className="analysis-subtitle">Settings</h3>
      <dl className="analysis-facts">
        <Fact label="Max depth">{text(settings?.maxDepth)}</Fact>
        <Fact label="Root count">{text(settings?.rootCount)}</Fact>
        <Fact label="Included types">
          {includeTypes.length === 0 ? 'None reported' : includeTypes.join(', ')}
        </Fact>
      </dl>
    </>
  );
}

export function GraphSummary({ graph }) {
  return (
    <>
      <h3 className="analysis-subtitle">Twin working-set summary</h3>
      <p className="hint">
        These are whole baseline/hypothetical twin working-set counts, not counts
        restricted to the impacted subgraph.
      </p>
      <dl className="analysis-facts">
        <Fact label="Baseline entity count">
          {text(graph?.baselineEntityCount)}
        </Fact>
        <Fact label="Hypothetical entity count">
          {text(graph?.hypotheticalEntityCount)}
        </Fact>
        <Fact label="Baseline relationship count">
          {text(graph?.baselineRelationshipCount)}
        </Fact>
        <Fact label="Hypothetical relationship count">
          {text(graph?.hypotheticalRelationshipCount)}
        </Fact>
        <Fact label="Removed entity count">{text(graph?.removedEntityCount)}</Fact>
        <Fact label="Added relationship count">
          {text(graph?.addedRelationshipCount)}
        </Fact>
        <Fact label="Removed relationship count">
          {text(graph?.removedRelationshipCount)}
        </Fact>
      </dl>
    </>
  );
}

function RiskLevelChanges({ changes }) {
  if (!Array.isArray(changes) || changes.length === 0) {
    return <p className="hint">No risk level changes were reported.</p>;
  }
  return (
    <ul className="simulation-change-list" aria-label="Risk level changes">
      {changes.map((change, index) => (
        <li key={`${index}-${change?.entityRef ?? 'change'}`}>
          <span className="mono">{text(change?.entityRef)}</span>
          <span>
            {text(change?.before)} {'->'} {text(change?.after)}
          </span>
        </li>
      ))}
    </ul>
  );
}

export function DeltaSummary({ delta }) {
  const impactedAdded = Array.isArray(delta?.impactedAdded)
    ? delta.impactedAdded
    : [];
  const impactedRemoved = Array.isArray(delta?.impactedRemoved)
    ? delta.impactedRemoved
    : [];
  const riskLevelChanges = Array.isArray(delta?.riskLevelChanges)
    ? delta.riskLevelChanges
    : [];

  return (
    <>
      <h3 className="analysis-subtitle">Before/after delta</h3>
      <dl className="analysis-facts">
        <Fact label="Before impacted count">
          {text(delta?.beforeImpactedCount)}
        </Fact>
        <Fact label="After impacted count">
          {text(delta?.afterImpactedCount)}
        </Fact>
        <Fact label="Impacted added count">
          {text(delta?.impactedAddedCount)}
        </Fact>
        <Fact label="Impacted removed count">
          {text(delta?.impactedRemovedCount)}
        </Fact>
        <Fact label="Before risk count">{text(delta?.beforeRiskCount)}</Fact>
        <Fact label="After risk count">{text(delta?.afterRiskCount)}</Fact>
        <Fact label="Risk added count">{text(delta?.riskAddedCount)}</Fact>
        <Fact label="Risk removed count">{text(delta?.riskRemovedCount)}</Fact>
        <Fact label="Uncertain before count">
          {text(delta?.uncertainBeforeCount)}
        </Fact>
        <Fact label="Uncertain after count">
          {text(delta?.uncertainAfterCount)}
        </Fact>
        <Fact label="Truncation changed">
          {delta?.truncationChanged === true ? 'Yes' : 'No'}
        </Fact>
      </dl>

      <h4 className="simulation-subtitle">Impacted references added</h4>
      <ValueList values={impactedAdded} />

      <h4 className="simulation-subtitle">Impacted references removed</h4>
      <ValueList values={impactedRemoved} />

      <h4 className="simulation-subtitle">
        Risk level changes (upward or downward)
      </h4>
      <RiskLevelChanges changes={riskLevelChanges} />
      <p className="hint">
        Risk added/removed counts are reported without reference lists in this
        response, so no risk-added or risk-removed references are shown.
      </p>
    </>
  );
}

export function Limitations({ limitations }) {
  return (
    <>
      <h3 className="analysis-subtitle">Limitations</h3>
      {Array.isArray(limitations) && limitations.length > 0 ? (
        <ul className="simulation-limitations">
          {limitations.map((limitation, index) => (
            <li key={`${index}-${String(limitation)}`}>{String(limitation)}</li>
          ))}
        </ul>
      ) : (
        <p className="hint">No limitations were reported.</p>
      )}
    </>
  );
}

function StartList({ starts }) {
  if (!Array.isArray(starts) || starts.length === 0) {
    return <p className="hint">No analysis starts were reported.</p>;
  }
  return (
    <ul className="analysis-starts">
      {starts.map((start, index) => (
        <li key={start?.id ?? index}>
          <span className="analysis-starts__name">{text(start?.name)}</span>
          <span className="badge">{text(start?.type)}</span>
          <span className="mono">{text(start?.externalRef ?? start?.id)}</span>
          <span className="hint">
            ID {text(start?.id)} · environment {text(start?.environment)}
          </span>
        </li>
      ))}
    </ul>
  );
}

function EvidencePaths({ paths }) {
  if (!Array.isArray(paths) || paths.length === 0) {
    return <p className="hint">No evidence paths were reported for this entity.</p>;
  }
  return (
    <div className="simulation-paths">
      {paths.map((path, pathIndex) => {
        const steps = Array.isArray(path?.steps) ? path.steps : [];
        return (
          <article
            className="analysis-path"
            key={`${pathIndex}-${path?.targetId ?? 'path'}`}
          >
            <h5 className="analysis-path__title">
              Evidence path {pathIndex + 1} · target{' '}
              <span className="mono">
                {text(path?.targetExternalRef ?? path?.targetId)}
              </span>{' '}
              · depth {text(path?.depth)}
            </h5>
            <dl className="analysis-facts analysis-facts--compact">
              <Fact label="Target ID">
                <span className="mono">{text(path?.targetId)}</span>
              </Fact>
              <Fact label="Target external reference">
                <span className="mono">{text(path?.targetExternalRef)}</span>
              </Fact>
            </dl>
            <p className="analysis-explanation">
              {text(path?.explanation, 'No explanation was reported.')}
            </p>
            {steps.length === 0 ? (
              <p className="hint">No ordered steps were reported for this path.</p>
            ) : (
              <ol className="analysis-steps">
                {steps.map((step, stepIndex) => (
                  <li
                    className="analysis-step"
                    key={`${stepIndex}-${step?.sourceId ?? ''}-${step?.targetId ?? ''}`}
                  >
                    <div>
                      <div className="analysis-step__route mono">
                        <span>{text(step?.sourceExternalRef ?? step?.sourceId)}</span>
                        <span aria-hidden="true"> {'->'} </span>
                        <span>{text(step?.targetExternalRef ?? step?.targetId)}</span>
                      </div>
                      <div className="hint">
                        Source {text(step?.sourceId)} · target {text(step?.targetId)}
                      </div>
                    </div>
                    <div className="analysis-step__meta">
                      <span className="badge">{text(step?.type)}</span>
                      <span className="badge">{text(step?.direction)}</span>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </article>
        );
      })}
    </div>
  );
}

function ImpactScope({ heading, impact }) {
  const impacted = Array.isArray(impact?.impacted) ? impact.impacted : [];
  const paths = Array.isArray(impact?.paths) ? impact.paths : [];
  const limits = impact?.limits ?? null;

  return (
    <div className="simulation-scope-block">
      <h4 className="simulation-subtitle">{heading} impact</h4>
      <dl className="analysis-facts">
        <Instant label={`${heading} impact snapshot observed at`} value={impact?.snapshotObservedAt} />
        <Fact label="Max depth requested">{text(impact?.maxDepth)}</Fact>
        <Fact label="Traversal nodes">{text(impact?.nodeCount)}</Fact>
        <Fact label="Evidence paths">{text(impact?.pathCount)}</Fact>
        <Fact label="Twin entities read">{text(impact?.entityCount)}</Fact>
        <Fact label="Twin relationships read">
          {text(impact?.relationshipCount)}
        </Fact>
        <Fact label="Truncated">
          {impact?.truncated === true ? 'Yes' : 'No'}
        </Fact>
        <Fact label="Truncation reason">{text(impact?.truncationReason)}</Fact>
        <Fact label="Cycle encountered">
          {impact?.cycleEncountered === true ? 'Yes' : 'No'}
        </Fact>
      </dl>
      <dl className="analysis-facts analysis-facts--compact">
        <Fact label="Max starts">{text(limits?.maxStarts)}</Fact>
        <Fact label="Allowed depth">{text(limits?.maxDepthAllowed)}</Fact>
        <Fact label="Max nodes">{text(limits?.maxNodes)}</Fact>
        <Fact label="Max paths">{text(limits?.maxPaths)}</Fact>
      </dl>
      <p className="analysis-note">{text(impact?.note)}</p>

      <h5 className="simulation-subtitle">Analysis starts</h5>
      <StartList starts={impact?.startedFrom} />

      <h5 className="simulation-subtitle">Impacted entities ({impacted.length})</h5>
      {impacted.length === 0 ? (
        <p className="hint">No impacted entities were reported.</p>
      ) : (
        <ul className="simulation-entity-list">
          {impacted.map((entity, index) => {
            const entityPaths = paths.filter(
              (path) => path?.targetId === entity?.id,
            );
            return (
              <li className="simulation-entity" key={entity?.id ?? index}>
                <div className="analysis-entity__head">
                  <h5 className="analysis-entity__name">{text(entity?.name)}</h5>
                  <span className="badge">{text(entity?.type)}</span>
                </div>
                <dl className="analysis-facts analysis-facts--compact">
                  <Fact label="ID">
                    <span className="mono">{text(entity?.id)}</span>
                  </Fact>
                  <Fact label="External reference">
                    <span className="mono">{text(entity?.externalRef)}</span>
                  </Fact>
                  <Fact label="Depth">{text(entity?.depth)}</Fact>
                  <Fact label="Via start">
                    <span className="mono">{text(entity?.viaStart)}</span>
                  </Fact>
                </dl>
                <EvidencePaths paths={entityPaths} />
              </li>
            );
          })}
        </ul>
      )}

      <h5 className="simulation-subtitle">All evidence paths ({paths.length})</h5>
      <EvidencePaths paths={paths} />
    </div>
  );
}

function RiskScope({ heading, risk }) {
  const risks = Array.isArray(risk?.risks) ? risk.risks : [];

  return (
    <div className="simulation-scope-block">
      <h4 className="simulation-subtitle">{heading} risk</h4>
      <dl className="analysis-facts">
        <Instant label={`${heading} risk snapshot observed at`} value={risk?.snapshotObservedAt} />
        <Fact label="Taxonomy version">{text(risk?.taxonomyVersion)}</Fact>
        <Fact label="Max depth requested">{text(risk?.maxDepth)}</Fact>
        <Fact label="Twin entities read">{text(risk?.entityCount)}</Fact>
        <Fact label="Twin relationships read">
          {text(risk?.relationshipCount)}
        </Fact>
      </dl>
      <p className="analysis-note">{text(risk?.note)}</p>

      <h5 className="simulation-subtitle">Analysis starts</h5>
      <StartList starts={risk?.startedFrom} />

      <h5 className="simulation-subtitle">Risk entries ({risks.length})</h5>
      {risks.length === 0 ? (
        <p className="hint">No risk entries were reported.</p>
      ) : (
        <ol className="risk-list">
          {risks.map((entry, index) => {
            const reasons = Array.isArray(entry?.reasons) ? entry.reasons : [];
            return (
              <li className="risk-entry" key={entry?.id ?? index}>
                <div className="risk-entry__head">
                  <div>
                    <h5 className="risk-entry__name">{text(entry?.name)}</h5>
                    <span className="mono">{text(entry?.externalRef)}</span>
                  </div>
                  <span
                    className="badge risk-level"
                    data-level={text(entry?.level, 'unknown').toLowerCase()}
                  >
                    {text(entry?.level)}
                  </span>
                </div>
                <dl className="analysis-facts analysis-facts--compact">
                  <Fact label="ID">
                    <span className="mono">{text(entry?.id)}</span>
                  </Fact>
                  <Fact label="Type">{text(entry?.type)}</Fact>
                  <Fact label="Depth">{text(entry?.depth)}</Fact>
                  <Fact label="Via start">
                    <span className="mono">{text(entry?.viaStart)}</span>
                  </Fact>
                  <Fact label="Uncertain">
                    {entry?.uncertain === true ? 'Yes' : 'No'}
                  </Fact>
                  <Fact label="Stale">{entry?.stale === true ? 'Yes' : 'No'}</Fact>
                </dl>
                <h6 className="analysis-reasons__title">Reasons</h6>
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
    </div>
  );
}

function Scope({ label, scope }) {
  return (
    <section className="simulation-scope" aria-label={`${label} scope`}>
      <h3 className="analysis-subtitle">{label} scope</h3>
      <ImpactScope heading={label} impact={scope?.impact} />
      <RiskScope heading={label} risk={scope?.risk} />
    </section>
  );
}

export function SimulationResult({ simulation }) {
  return (
    <section
      className="analysis-section simulation-result"
      role="region"
      aria-label="Simulation result"
    >
      <div className="analysis-section__head">
        <div>
          <h2 className="analysis-section__title">Simulation result</h2>
          <p className="hint">
            One read-only, server-computed hypothetical result. Nothing was
            executed or persisted.
          </p>
        </div>
        <span className="badge">POST /simulations</span>
      </div>

      <div className="simulation-advisory" role="note">
        <strong>{simulation?.advisory}</strong>
        <span>Simulated flag: {simulation?.simulated === true ? 'true' : 'false'}</span>
      </div>

      <SettingsSummary settings={simulation?.settings} />
      <GraphSummary graph={simulation?.graph} />

      <div className="simulation-scopes">
        <Scope label="Before" scope={simulation?.before} />
        <Scope label="After" scope={simulation?.after} />
      </div>

      <DeltaSummary delta={simulation?.delta} />
      <Limitations limitations={simulation?.limitations} />
    </section>
  );
}
