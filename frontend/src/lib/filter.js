/** Client-side filtering over already-fetched data (no server filtering exists). */

export function textMatches(query, ...values) {
  const q = String(query ?? '').trim().toLowerCase();
  if (!q) return true;
  return values.some((v) => String(v ?? '').toLowerCase().includes(q));
}

export function filterEntities(entities, { query = '', types = [] } = {}) {
  const list = Array.isArray(entities) ? entities : [];
  return list.filter((e) => {
    if (types.length > 0 && !types.includes(e.type)) return false;
    return textMatches(query, e.name, e.externalRef, e.type, e.environment, e.id);
  });
}

export function filterRelationships(
  relationships,
  entitiesById,
  { query = '', types = [] } = {},
) {
  const list = Array.isArray(relationships) ? relationships : [];
  return list.filter((r) => {
    if (types.length > 0 && !types.includes(r.type)) return false;
    const source = entitiesById?.get(r.sourceId)?.name ?? r.sourceId;
    const target = entitiesById?.get(r.targetId)?.name ?? r.targetId;
    return textMatches(query, source, target, r.type, r.id);
  });
}

export function countBy(list, key) {
  const counts = new Map();
  for (const item of Array.isArray(list) ? list : []) {
    const value = typeof key === 'function' ? key(item) : item?.[key];
    const label = value ?? 'UNKNOWN';
    counts.set(label, (counts.get(label) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label));
}
