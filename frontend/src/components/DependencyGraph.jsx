import { useId, useMemo } from 'react';
import { buildGraph } from '../lib/graphLayout.js';
import { RELATIONSHIP_TYPES } from '../lib/constants.js';
import { pluralize, titleCaseType } from '../lib/format.js';
import { EmptyPanel } from './StatePanels.jsx';

function GraphNotice({ warnings }) {
  const notes = [];
  if (warnings.cycles) {
    notes.push('A dependency cycle is present; layers are relaxed deterministically.');
  }
  if (warnings.missingEndpoints > 0) {
    notes.push(
      `${warnings.missingEndpoints} relationship(s) reference entities outside the loaded set and were omitted.`,
    );
  }
  if (warnings.components > 1) {
    notes.push(`${warnings.components} disconnected components are shown side by side.`);
  }
  if (warnings.truncatedNodes) {
    notes.push(`Showing the first 300 of ${warnings.totalEntities} entities.`);
  }
  if (notes.length === 0) return null;
  return (
    <div className="notice" role="status">
      <span>{notes.join(' ')}</span>
      <span className="notice__meta">
        Derived in the browser from loaded twin records; not operational risk.
      </span>
    </div>
  );
}

function GraphAlternative({ graph }) {
  const names = new Map(
    graph.nodes.map((node) => [node.id, node.entity?.name ?? node.id]),
  );

  return (
    <details className="graph-alternative">
      <summary>Node and edge table (accessible alternative)</summary>
      <div className="graph-alternative__body">
        <h3 className="graph-alternative__title">Entities in this graph</h3>
        <div className="table-wrap">
          <table className="data">
            <caption className="visually-hidden">Entities in this graph</caption>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Type</th>
                <th scope="col">Environment</th>
                <th scope="col">ID</th>
              </tr>
            </thead>
            <tbody>
              {graph.nodes.map((node) => (
                <tr key={`alt-n-${node.id}`}>
                  <td>{node.entity?.name ?? node.id}</td>
                  <td>{titleCaseType(node.entity?.type)}</td>
                  <td>{node.entity?.environment ?? '—'}</td>
                  <td className="mono">{node.id}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <h3 className="graph-alternative__title">Relationships in this graph</h3>
        <div className="table-wrap">
          <table className="data">
            <caption className="visually-hidden">Relationships in this graph</caption>
            <thead>
              <tr>
                <th scope="col">Type</th>
                <th scope="col">From</th>
                <th scope="col">To</th>
              </tr>
            </thead>
            <tbody>
              {graph.edges.length === 0 ? (
                <tr>
                  <td colSpan="3">No modeled relationships are shown.</td>
                </tr>
              ) : (
                graph.edges.map((edge) => (
                  <tr key={`alt-e-${edge.id}`}>
                    <td>{edge.type}</td>
                    <td>{names.get(edge.sourceId) ?? edge.sourceId}</td>
                    <td>{names.get(edge.targetId) ?? edge.targetId}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </details>
  );
}

export function DependencyGraph({ entities, relationships, selectedId, onSelectEntity }) {
  const graphHelpId = useId();
  const graph = useMemo(
    () => buildGraph(entities, relationships),
    [entities, relationships],
  );

  if (graph.nodes.length === 0) {
    return (
      <EmptyPanel
        title="No entities to graph"
        body="The twin returned no entities, so there is nothing to plot."
      />
    );
  }

  const { nodeWidth, nodeHeight } = graph;
  const graphName = `Modeled relationship graph with ${pluralize(
    graph.nodes.length,
    'entity',
    'entities',
  )} and ${pluralize(graph.edges.length, 'relationship')}`;

  return (
    <div>
      <GraphNotice warnings={graph.warnings} />
      <div className="legend">
        <span className="legend__item">
          <span className="legend__swatch" style={{ background: '#141c2e' }} />
          entity
        </span>
        <span className="legend__item">
          <span className="legend__swatch" style={{ background: '#3a4767' }} />
          {graph.edges.length} modeled relationship(s)
        </span>
        <span className="legend__item">
          Arrow direction is stored relationship direction, not traffic. Column order is
          layout only.
        </span>
      </div>
      <ul
        className="legend legend--types"
        aria-label="Supported modeled relationship types"
      >
        {RELATIONSHIP_TYPES.map((type) => (
          <li className="legend__item" key={type}>
            <span className="legend__swatch" style={{ background: '#3a4767' }} />
            {type}
          </li>
        ))}
      </ul>
      <p id={graphHelpId} className="visually-hidden">
        Relationship lines and labels are visual only. Use the node and edge table
        for relationship details. Press Tab to move between entity controls, then
        Enter or Space to open an entity.
      </p>
      <div className="graph-shell">
        <svg
          className="graph-svg"
          role="group"
          aria-label={graphName}
          aria-describedby={graphHelpId}
          width={graph.width}
          height={graph.height}
          viewBox={`0 0 ${graph.width} ${graph.height}`}
        >
          <defs>
            <marker
              id="arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="7"
              markerHeight="7"
              orient="auto-start-reverse"
            >
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#3a4767" />
            </marker>
          </defs>

          {graph.edges.map((edge) => {
            const midX = (edge.x1 + edge.x2) / 2;
            const midY = (edge.y1 + edge.y2) / 2;
            return (
              <g key={`e-${edge.id}`} className="graph-edge" aria-hidden="true">
                {edge.selfLoop ? (
                  <path
                    className="graph-edge__line"
                    d={`M ${edge.x1} ${edge.y1} q 40 -46 0 -46 q -40 0 0 46`}
                    markerEnd="url(#arrow)"
                  />
                ) : (
                  <line
                    className="graph-edge__line"
                    x1={edge.x1}
                    y1={edge.y1}
                    x2={edge.x2}
                    y2={edge.y2}
                    markerEnd="url(#arrow)"
                  />
                )}
                <text className="graph-edge__label" x={midX} y={midY - 4} textAnchor="middle">
                  {edge.type}
                </text>
              </g>
            );
          })}

          {graph.nodes.map((node) => {
            const selected = node.id === selectedId;
            return (
              <g
                key={`n-${node.id}`}
                className={`graph-node${selected ? ' graph-node--selected' : ''}`}
                transform={`translate(${node.x}, ${node.y})`}
                role="button"
                tabIndex={0}
                aria-pressed={selected}
                aria-label={`${node.entity?.name ?? node.id}, ${titleCaseType(node.entity?.type)}`}
                onClick={() => onSelectEntity?.(node.id)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    onSelectEntity?.(node.id);
                  }
                }}
              >
                <rect className="graph-node__box" width={nodeWidth} height={nodeHeight} rx="9" />
                <text className="graph-node__label" x="12" y="22">
                  {(node.entity?.name ?? node.id).slice(0, 22)}
                </text>
                <text className="graph-node__sub" x="12" y="38">
                  {node.entity?.type ?? 'UNKNOWN'}
                </text>
              </g>
            );
          })}
        </svg>
      </div>
      <GraphAlternative graph={graph} />
    </div>
  );
}
