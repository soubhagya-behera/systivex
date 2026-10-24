import { useMemo } from 'react';
import { buildGraph } from '../lib/graphLayout.js';
import { titleCaseType } from '../lib/format.js';
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
      {notes.join(' ')}
    </div>
  );
}

export function DependencyGraph({ entities, relationships, selectedId, onSelectEntity }) {
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
          {graph.edges.length} relationship(s)
        </span>
        <span className="legend__item">left → right follows CALLS/DEPENDS_ON direction</span>
      </div>
      <div className="graph-shell">
        <svg
          className="graph-svg"
          role="img"
          aria-label={`Dependency graph with ${graph.nodes.length} entities and ${graph.edges.length} relationships`}
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
              <g key={`e-${edge.id}`} className="graph-edge">
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
    </div>
  );
}
