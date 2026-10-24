import { useMemo } from 'react';
import { StatCard, StatGrid } from '../components/StatCard.jsx';
import { countBy } from '../lib/filter.js';
import { titleCaseType } from '../lib/format.js';

function Distribution({ title, rows, emptyLabel }) {
  return (
    <section className="card">
      <h2 className="card__title">{title}</h2>
      {rows.length === 0 ? (
        <p className="hint">{emptyLabel}</p>
      ) : (
        <div className="stat-grid" style={{ marginBottom: 0 }}>
          {rows.map((row) => (
            <div key={row.label} className="stat">
              <div className="stat__value" style={{ fontSize: 22 }}>
                {row.count}
              </div>
              <div className="stat__label">{titleCaseType(row.label)}</div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

export function Overview({ entities, relationships, onSelectEntity }) {
  const entityTypes = useMemo(() => countBy(entities, 'type'), [entities]);
  const relTypes = useMemo(() => countBy(relationships, 'type'), [relationships]);
  const environments = useMemo(() => countBy(entities, 'environment'), [entities]);

  const recent = useMemo(() => {
    return [...entities]
      .sort((a, b) => String(b.updatedAt ?? '').localeCompare(String(a.updatedAt ?? '')))
      .slice(0, 6);
  }, [entities]);

  return (
    <div>
      <div className="view-header">
        <div>
          <h1 className="view-header__title">Overview</h1>
          <p className="view-header__hint">
            A snapshot of the System Twin as reported by the control plane. This
            console only reads; it never mutates twin state.
          </p>
        </div>
      </div>

      <StatGrid>
        <StatCard value={entities.length} label="Entities" />
        <StatCard value={relationships.length} label="Relationships" />
        <StatCard value={entityTypes.length} label="Entity types in use" />
        <StatCard value={environments.length} label="Environments" />
      </StatGrid>

      <div className="split">
        <Distribution
          title="Entities by type"
          rows={entityTypes}
          emptyLabel="No entities available."
        />
        <Distribution
          title="Relationships by type"
          rows={relTypes}
          emptyLabel="No relationships available."
        />
      </div>

      <section className="card" style={{ marginTop: 18 }}>
        <h2 className="card__title">Recently updated</h2>
        {recent.length === 0 ? (
          <p className="hint">No entities available.</p>
        ) : (
          <ul className="list" style={{ maxHeight: 'none' }}>
            {recent.map((entity) => (
              <li key={entity.id}>
                <button
                  type="button"
                  className="list__item"
                  onClick={() => onSelectEntity?.(entity.id)}
                >
                  <span className="list__name">{entity.name}</span>{' '}
                  <span className="badge badge--type">{titleCaseType(entity.type)}</span>
                  <div className="list__meta">{entity.environment ?? 'no environment'}</div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
