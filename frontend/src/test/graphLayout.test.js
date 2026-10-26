import { describe, it, expect } from 'vitest';
import { buildGraph } from '../lib/graphLayout.js';

const entity = (id, name, type = 'SERVICE') => ({ id, name, type });

describe('buildGraph', () => {
  it('places a dependency chain left to right, deterministically', () => {
    const entities = [entity('b', 'B'), entity('a', 'A')];
    const rels = [{ id: 'r1', sourceId: 'a', targetId: 'b', type: 'CALLS' }];

    const first = buildGraph(entities, rels);
    const second = buildGraph(entities, rels);
    expect(first).toEqual(second);

    const a = first.nodes.find((n) => n.id === 'a');
    const b = first.nodes.find((n) => n.id === 'b');
    expect(a.x).toBeLessThan(b.x);
  });

  it('counts disconnected components and skips relationships with missing endpoints', () => {
    const entities = [entity('a', 'A'), entity('b', 'B'), entity('c', 'C')];
    const rels = [
      { id: 'r1', sourceId: 'a', targetId: 'b', type: 'CALLS' },
      { id: 'r2', sourceId: 'a', targetId: 'ghost', type: 'CALLS' },
    ];

    const graph = buildGraph(entities, rels);
    expect(graph.edges).toHaveLength(1);
    expect(graph.warnings.missingEndpoints).toBe(1);
    expect(graph.warnings.components).toBe(2);
  });

  it('terminates on cycles and flags them', () => {
    const entities = [entity('a', 'A'), entity('b', 'B')];
    const rels = [
      { id: 'r1', sourceId: 'a', targetId: 'b', type: 'CALLS' },
      { id: 'r2', sourceId: 'b', targetId: 'a', type: 'CALLS' },
    ];

    const graph = buildGraph(entities, rels);
    expect(graph.warnings.cycles).toBe(true);
    expect(graph.nodes).toHaveLength(2);
  });

  it('flags self loops and reports node truncation', () => {
    const self = buildGraph(
      [entity('a', 'A')],
      [{ id: 'r1', sourceId: 'a', targetId: 'a', type: 'DEPENDS_ON' }],
    );
    expect(self.edges[0].selfLoop).toBe(true);

    const many = Array.from({ length: 5 }, (_, i) => entity(`n${i}`, `N${i}`));
    const capped = buildGraph(many, [], { maxNodes: 2 });
    expect(capped.warnings.truncatedNodes).toBe(true);
    expect(capped.nodes).toHaveLength(2);
  });

  it('handles empty input without a graph', () => {
    const graph = buildGraph([], []);
    expect(graph.nodes).toEqual([]);
    expect(graph.warnings.components).toBe(0);
  });
});
