import { useMemo, useState } from 'react';
import { SearchBar, TypeFilter } from '../components/SearchBar.jsx';
import { EntityDetail } from '../components/EntityDetail.jsx';
import { EmptyPanel } from '../components/StatePanels.jsx';
import { filterEntities } from '../lib/filter.js';
import { titleCaseType } from '../lib/format.js';

export function Entities({
  entities,
  relationships,
  selectedId,
  onSelectEntity,
  entityTypes,
}) {
  const [query, setQuery] = useState('');
  const [types, setTypes] = useState([]);

  const visible = useMemo(
    () => filterEntities(entities, { query, types }),
    [entities, query, types],
  );

  const toggleType = (type) =>
    setTypes((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type],
    );

  const selected = entities.find((entity) => entity.id === selectedId) ?? null;

  return (
    <div>
      <div className="view-header">
        <div>
          <h1 className="view-header__title">Entities</h1>
          <p className="view-header__hint">
            Source, deploy, data, and configuration entities in the twin. Filtering
            happens entirely in the browser over the fetched set.
          </p>
        </div>
      </div>

      <div className="controls">
        <SearchBar
          value={query}
          onChange={setQuery}
          label="Search entities"
          placeholder="Search name, external ref, environment…"
        />
        <span className="hint">
          {visible.length} of {entities.length}
        </span>
      </div>
      <TypeFilter
        options={entityTypes}
        selected={types}
        onToggle={toggleType}
        label="Filter entities by type"
      />

      {entities.length === 0 ? (
        <EmptyPanel title="No entities" body="The twin reported no entities." />
      ) : visible.length === 0 ? (
        <EmptyPanel
          title="No matches"
          body="No entity matches the current search and type filters."
        />
      ) : (
        <div className="explorer">
          <ul className="list" aria-label="Entity list">
            {visible.map((entity) => (
              <li key={entity.id}>
                <button
                  type="button"
                  className="list__item"
                  aria-selected={entity.id === selectedId}
                  onClick={() => onSelectEntity(entity.id)}
                >
                  <span className="list__name">{entity.name}</span>
                  <div className="list__meta">
                    <span className="badge badge--type">{titleCaseType(entity.type)}</span>{' '}
                    {entity.externalRef ?? 'no external ref'}
                  </div>
                </button>
              </li>
            ))}
          </ul>
          <EntityDetail
            entity={selected}
            relationships={relationships}
            onSelectEntity={onSelectEntity}
          />
        </div>
      )}
    </div>
  );
}
