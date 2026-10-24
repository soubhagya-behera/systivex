import { DependencyGraph } from '../components/DependencyGraph.jsx';
import { EmptyPanel } from '../components/StatePanels.jsx';

export function GraphView({ entities, relationships, selectedId, onSelectEntity }) {
  return (
    <div>
      <div className="view-header">
        <div>
          <h1 className="view-header__title">Dependency Graph</h1>
          <p className="view-header__hint">
            A layered, deterministic view of directed relationships. This is a
            static layout of observed data — never a simulation.
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
          onSelectEntity={onSelectEntity}
        />
      )}
    </div>
  );
}
