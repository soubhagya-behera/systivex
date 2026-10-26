import { DependencyGraph } from '../components/DependencyGraph.jsx';
import { EmptyPanel } from '../components/StatePanels.jsx';

export function GraphView({ entities, relationships, selectedId, onSelectEntity, preview = false }) {
  return (
    <div>
      <div className="view-header">
        <div>
          <h1 className="view-header__title">Dependency Graph</h1>
          <p className="view-header__hint">
            {preview
              ? 'A layered, deterministic layout of synthetic sample relationships for local UI inspection — never observed traffic or a simulation.'
              : 'A layered, deterministic view of directed relationships. This is a static layout of loaded twin records — never observed traffic or a simulation.'}
          </p>
        </div>
      </div>

      {entities.length === 0 ? (
        <EmptyPanel
          title="Nothing to plot"
          body="The twin reported no entities, so the graph is empty."
        />
      ) : (
        <DependencyGraph
          entities={entities}
          relationships={relationships}
          selectedId={selectedId}
          onSelectEntity={(id) => onSelectEntity?.(id, { focusDetail: true })}
        />
      )}
    </div>
  );
}
