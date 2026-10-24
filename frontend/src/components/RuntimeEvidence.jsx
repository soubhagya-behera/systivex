import { formatAge, formatInstant } from '../lib/format.js';
import { parseRuntimeEvidence } from '../lib/runtimeEvidence.js';
import { useNow } from '../hooks/useNow.js';

const HEALTH_STATE = {
  observed: 'Stored status',
  unavailable: 'Unavailable',
  unrecognized: 'Unrecognized',
};

function CounterValues({ evidence }) {
  if (evidence.countersState === 'present' || evidence.countersState === 'partial') {
    return (
      <dl className="evidence__counters">
        {evidence.counters.map((counter) => (
          <div className="evidence__counter" key={counter.name}>
            <dt className="mono">{counter.name}</dt>
            <dd className="mono">{counter.value}</dd>
          </div>
        ))}
      </dl>
    );
  }

  if (evidence.countersState === 'empty') {
    return <p className="hint">No counters were recorded in the last observation.</p>;
  }

  if (evidence.countersState === 'missing') {
    return <p className="hint">Counter data was not provided in the last observation.</p>;
  }

  return <p className="hint">Counter data could not be interpreted.</p>;
}

function SourceValue({ evidence }) {
  if (evidence.sourceState === 'malformed') {
    return (
      <span className="hint">Source unavailable (malformed metadata).</span>
    );
  }

  return (
    <>
      {evidence.source ?? 'Not provided'}
      {evidence.sourceState === 'present' && evidence.connectorVersion ? (
        <span className="evidence__version"> · {evidence.connectorVersion}</span>
      ) : null}
    </>
  );
}

function LimitationsList({ evidence }) {
  if (
    evidence.limitationsState === 'present' ||
    evidence.limitationsState === 'partial'
  ) {
    return (
      <ul className="evidence__list">
        {evidence.limitations.map((limitation, index) => (
          <li key={`${index}-${limitation}`}>{limitation}</li>
        ))}
      </ul>
    );
  }

  if (evidence.limitationsState === 'empty') {
    return (
      <p className="hint">No limitations were stored with this observation.</p>
    );
  }

  if (evidence.limitationsState === 'missing') {
    return (
      <p className="hint">Limitation data was not provided in the last observation.</p>
    );
  }

  return <p className="hint">Limitation metadata could not be interpreted.</p>;
}

function HealthValue({ health }) {
  if (health.kind === 'observed') {
    return (
      <span className="health-badge" data-health={health.status.toLowerCase()}>
        {health.status}
      </span>
    );
  }
  if (health.kind === 'unavailable') {
    return <span>Health unavailable in the last observation.</span>;
  }
  return <span>{HEALTH_STATE.unrecognized}; no health status is inferred.</span>;
}

export function RuntimeEvidence({ metadata }) {
  const now = useNow();
  const evidence = parseRuntimeEvidence(metadata);

  if (evidence.state === 'missing') {
    return (
      <p className="hint">
        No runtime evidence is stored for this entity. Missing evidence is not treated as
        healthy or unhealthy.
      </p>
    );
  }

  if (evidence.state === 'unrecognized') {
    return (
      <div className="notice notice--neutral" role="status">
        Runtime metadata is present but could not be interpreted. No health status is
        inferred. {evidence.reason}
      </div>
    );
  }

  const future = evidence.observedAtMs > now + 1000;

  return (
    <section className="evidence" aria-label="Observed runtime evidence">
      <div className="evidence__top">
        <span className="badge badge--type">Stored observation</span>
        <span className="hint">Latest value stored in the twin, not live telemetry.</span>
      </div>

      <dl className="evidence__grid">
        <div className="evidence__row">
          <dt>Source</dt>
          <dd>
            <SourceValue evidence={evidence} />
          </dd>
        </div>
        <div className="evidence__row">
          <dt>Observed at</dt>
          <dd>
            <time dateTime={evidence.observedAt}>{formatInstant(evidence.observedAt)}</time>
            <span className="evidence__age"> · {formatAge(evidence.observedAtMs, now)}</span>
          </dd>
        </div>
        <div className="evidence__row">
          <dt>Health</dt>
          <dd>
            <HealthValue health={evidence.health} />
          </dd>
        </div>
      </dl>

      {future ? (
        <p className="hint">
          The stored observation time is in the future; treat this evidence as untrusted.
        </p>
      ) : null}

      <h4 className="evidence__heading">Stored limitations</h4>
      <LimitationsList evidence={evidence} />

      <h4 className="evidence__heading">Stored counters</h4>
      <CounterValues evidence={evidence} />

      {evidence.notes.length > 0 ? (
        <ul className="evidence__notes" role="status">
          {evidence.notes.map((note, index) => (
            <li key={`${index}-${note}`}>{note}</li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
