/**
 * Deterministic layered graph layout for the System Twin dependency view.
 *
 * Pure function, no dependencies. It intentionally handles the messy cases:
 *  - disconnected entities (multiple components are placed in separate columns
 *    by their computed layer);
 *  - relationships whose endpoints are not in the loaded entity set (counted
 *    and skipped rather than crashing or inventing nodes);
 *  - cycles (layering relaxes without hanging and reports `cycles: true`);
 *  - larger graphs (node count is capped, and the cap is reported).
 */
export const NODE_WIDTH = 172;
export const NODE_HEIGHT = 52;
const COLUMN_GAP = 96;
const ROW_GAP = 24;
const MARGIN = 40;

export function buildGraph(entities, relationships, { maxNodes = 300 } = {}) {
  const allEntities = Array.isArray(entities) ? entities : [];
  const truncatedNodes = allEntities.length > maxNodes;
  const nodesInput = truncatedNodes ? allEntities.slice(0, maxNodes) : allEntities;

  const byId = new Map();
  for (const entity of nodesInput) {
    byId.set(String(entity.id), entity);
  }

  const rels = Array.isArray(relationships) ? relationships : [];
  const edges = [];
  let missingEndpoints = 0;
  for (const rel of rels) {
    const sourceId = String(rel.sourceId);
    const targetId = String(rel.targetId);
    if (!byId.has(sourceId) || !byId.has(targetId)) {
      missingEndpoints += 1;
      continue;
    }
    edges.push({ id: String(rel.id), sourceId, targetId, type: rel.type });
  }

  const ids = [...byId.keys()].sort();
  const adjacency = new Map(ids.map((id) => [id, []]));
  const indegree = new Map(ids.map((id) => [id, 0]));
  for (const edge of edges) {
    adjacency.get(edge.sourceId).push(edge.targetId);
    indegree.set(edge.targetId, indegree.get(edge.targetId) + 1);
  }

  const layer = new Map();
  const remaining = new Map(indegree);
  const queue = ids.filter((id) => remaining.get(id) === 0);
  for (const id of queue) layer.set(id, 0);
  let cursor = 0;
  while (cursor < queue.length) {
    const id = queue[cursor++];
    const base = layer.get(id) ?? 0;
    for (const next of adjacency.get(id)) {
      if ((layer.get(next) ?? -1) < base + 1) {
        layer.set(next, base + 1);
      }
      remaining.set(next, remaining.get(next) - 1);
      if (remaining.get(next) === 0) {
        queue.push(next);
      }
    }
  }

  // Nodes still unassigned sit on a cycle; relax deterministically.
  let cycles = false;
  let changed = true;
  let passes = 0;
  while (changed && passes <= ids.length) {
    changed = false;
    for (const id of ids) {
      if (layer.has(id)) continue;
      cycles = true;
      let best = null;
      for (const edge of edges) {
        if (edge.targetId === id && layer.has(edge.sourceId)) {
          const candidate = layer.get(edge.sourceId) + 1;
          best = best === null ? candidate : Math.max(best, candidate);
        }
      }
      if (best !== null) {
        layer.set(id, best);
        changed = true;
      }
    }
    passes += 1;
  }
  for (const id of ids) {
    if (!layer.has(id)) layer.set(id, 0);
  }

  const columns = new Map();
  for (const id of ids) {
    const level = layer.get(id);
    if (!columns.has(level)) columns.set(level, []);
    columns.get(level).push(id);
  }
  const levels = [...columns.keys()].sort((a, b) => a - b);
  for (const level of levels) {
    columns.get(level).sort((a, b) => {
      const nameA = String(byId.get(a)?.name ?? a);
      const nameB = String(byId.get(b)?.name ?? b);
      return nameA.localeCompare(nameB) || a.localeCompare(b);
    });
  }

  const positions = new Map();
  let maxX = 0;
  let maxY = 0;
  levels.forEach((level, columnIndex) => {
    columns.get(level).forEach((id, rowIndex) => {
      const x = MARGIN + columnIndex * (NODE_WIDTH + COLUMN_GAP);
      const y = MARGIN + rowIndex * (NODE_HEIGHT + ROW_GAP);
      positions.set(id, { x, y });
      maxX = Math.max(maxX, x + NODE_WIDTH);
      maxY = Math.max(maxY, y + NODE_HEIGHT);
    });
  });

  const renderedEdges = edges.map((edge) => {
    const source = positions.get(edge.sourceId);
    const target = positions.get(edge.targetId);
    const selfLoop = edge.sourceId === edge.targetId;
    return {
      ...edge,
      selfLoop,
      x1: source.x + NODE_WIDTH,
      y1: source.y + NODE_HEIGHT / 2,
      x2: selfLoop ? target.x + NODE_WIDTH * 0.5 : target.x,
      y2: selfLoop ? target.y : target.y + NODE_HEIGHT / 2,
    };
  });

  const nodes = ids.map((id) => ({
    id,
    entity: byId.get(id),
    ...positions.get(id),
  }));

  return {
    nodes,
    edges: renderedEdges,
    width: Math.max(maxX + MARGIN, NODE_WIDTH + 2 * MARGIN),
    height: Math.max(maxY + MARGIN, NODE_HEIGHT + 2 * MARGIN),
    nodeWidth: NODE_WIDTH,
    nodeHeight: NODE_HEIGHT,
    warnings: {
      missingEndpoints,
      cycles,
      components: countComponents(ids, edges),
      truncatedNodes,
      totalEntities: allEntities.length,
      totalRelationships: rels.length,
    },
  };
}

function countComponents(ids, edges) {
  const parent = new Map(ids.map((id) => [id, id]));
  const find = (value) => {
    let current = value;
    while (parent.get(current) !== current) {
      parent.set(current, parent.get(parent.get(current)));
      current = parent.get(current);
    }
    return current;
  };
  for (const edge of edges) {
    const a = find(edge.sourceId);
    const b = find(edge.targetId);
    if (a !== b) parent.set(a, b);
  }
  return new Set(ids.map((id) => find(id))).size;
}
