import { useEffect, useRef } from 'react';
import { formatInstant, titleCaseType } from '../lib/format.js';
import { MetadataView } from './MetadataView.jsx';
import { RuntimeEvidence } from './RuntimeEvidence.jsx';

function Row({ label, children }) {
  return (
    <div className="detail__row">
      <span className="detail__key">{label}</span>
      <span className="detail__val">{children}</span>
    </div>
  );
}

export function EntityDetail({
  entity,
  relationships = [],
  onSelectEntity,
  focusOnRender = false,
  onFocused,
}) {
  const headingRef = useRef(null);

  useEffect(() => {
    if (!focusOnRender) return;
    headingRef.current?.focus();
    onFocused?.();
  }, [focusOnRender, entity, onFocused]);

  if (!entity) {
    return (
      <div className="detail" aria-label="Entity detail">
        <p className="hint">Select an entity to inspect its attributes.</p>
      </div>
    );
  }

  const outgoing = relationships.filter((rel) => rel.sourceId === entity.id);
  const incoming = relationships.filter((rel) => rel.targetId === entity.id);

  return (
    <div className="detail" aria-label="Entity detail">
      <h2
        ref={headingRef}
        tabIndex={-1}
        className="view-header__title"
        style={{ fontSize: 18 }}
      >
        {entity.name}
      </h2>
      <Row label="id">
        <span className="mono">{entity.id}</span>
      </Row>
      <Row label="type">
        <span className="badge badge--type">{titleCaseType(entity.type)}</span>
      </Row>
      <Row label="environment">{entity.environment ?? '—'}</Row>
      <Row label="externalRef">
        <span className="mono">{entity.externalRef ?? '—'}</span>
      </Row>
      <Row label="Twin record created">{formatInstant(entity.createdAt)}</Row>
      <Row label="Twin record updated">{formatInstant(entity.updatedAt)}</Row>

      <h3 className="card__title" style={{ marginTop: 18 }}>
        Runtime evidence
      </h3>
      <RuntimeEvidence metadata={entity.metadata} />

      <h3 className="card__title" style={{ marginTop: 18 }}>
        Metadata
      </h3>
      <MetadataView metadata={entity.metadata} />

      <h3 className="card__title" style={{ marginTop: 18 }}>
        Relationships ({outgoing.length + incoming.length})
      </h3>
      {outgoing.length + incoming.length === 0 ? (
        <p className="hint">No relationships reference this entity.</p>
      ) : (
        <ul className="list">
          {[...outgoing.map((rel) => ({ rel, dir: 'out' })), ...incoming.map((rel) => ({ rel, dir: 'in' }))].map(
            ({ rel, dir }) => (
              <li key={`${dir}-${rel.id}`}>
                <button
                  type="button"
                  className="list__item"
                  onClick={() => onSelectEntity?.(rel.sourceId === entity.id ? rel.targetId : rel.sourceId)}
                >
                  <span className="badge">{dir === 'out' ? '→' : '←'}</span>{' '}
                  <span className="list__name">{titleCaseType(rel.type)}</span>
                  <div className="list__meta">
                    {dir === 'out' ? rel.targetId : rel.sourceId}
                  </div>
                </button>
              </li>
            ),
          )}
        </ul>
      )}
    </div>
  );
}
