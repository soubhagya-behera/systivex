import { useMemo, useState } from 'react';
import { SearchBar, TypeFilter } from '../components/SearchBar.jsx';
import { EmptyPanel } from '../components/StatePanels.jsx';
import { filterRelationships } from '../lib/filter.js';
import { titleCaseType } from '../lib/format.js';

export function Relationships({
  entities,
  relationships,
  entitiesById,
  relTypes,
  onSelectEntity,
  preview = false,
}) {
  const [query, setQuery] = useState('');
  const [types, setTypes] = useState([]);

  const visible = useMemo(
    () => filterRelationships(relationships, entitiesById, { query, types }),
    [relationships, entitiesById, query, types],
  );

  const toggleType = (type) =>
    setTypes((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type],
    );

  const nameOf = (id) => entitiesById.get(id)?.name ?? id;

  return (
    <div>
      <div className="view-header">
        <div>
          <h1 className="view-header__title">Relationships</h1>
          <p className="view-header__hint">
            {preview
              ? 'Synthetic sample relationships for local UI inspection. Endpoints are resolved against the sample entity set.'
              : 'Directed edges between entities. Endpoints are resolved against the loaded entity set.'}
          </p>
        </div>
      </div>

      <div className="controls">
        <SearchBar
          value={query}
          onChange={setQuery}
          label="Search relationships"
          placeholder="Search endpoint names or type…"
        />
        <span className="hint">
          {visible.length} of {relationships.length}
        </span>
      </div>
      <TypeFilter
        options={relTypes}
        selected={types}
        onToggle={toggleType}
        label="Filter relationships by type"
      />

      {relationships.length === 0 ? (
        <EmptyPanel
          title="No relationships"
          body="The twin reported no relationships between entities."
        />
      ) : visible.length === 0 ? (
        <EmptyPanel
          title="No matches"
          body="No relationship matches the current search and type filters."
        />
      ) : (
        <div className="table-wrap">
          <table className="data">
            <caption className="visually-hidden">System Twin relationships</caption>
            <thead>
              <tr>
                <th scope="col">Type</th>
                <th scope="col">Source</th>
                <th scope="col">Target</th>
                <th scope="col">Id</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((rel) => (
                <tr key={rel.id}>
                  <td>
                    <span className="badge">{titleCaseType(rel.type)}</span>
                  </td>
                  <td>
                    <button
                      type="button"
                      className="nav-item"
                      style={{ padding: '2px 6px' }}
                      onClick={() => onSelectEntity?.(rel.sourceId)}
                    >
                      {nameOf(rel.sourceId)}
                    </button>
                  </td>
                  <td>
                    <button
                      type="button"
                      className="nav-item"
                      style={{ padding: '2px 6px' }}
                      onClick={() => onSelectEntity?.(rel.targetId)}
                    >
                      {nameOf(rel.targetId)}
                    </button>
                  </td>
                  <td className="mono">{rel.id}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="hint" style={{ marginTop: 10 }}>
        {entities.length} entities loaded as endpoint context.
      </p>
    </div>
  );
}
