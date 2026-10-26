import { describe, it, expect } from 'vitest';
import { countBy, filterEntities, filterRelationships } from '../lib/filter.js';

const ENTITIES = [
  { id: '1', name: 'Payments API', type: 'API', externalRef: 'svc/pay', environment: 'production' },
  { id: '2', name: 'Ledger DB', type: 'DATABASE', externalRef: 'db/ledger', environment: 'production' },
];

describe('filterEntities', () => {
  it('matches across name, ref, type and environment', () => {
    expect(filterEntities(ENTITIES, { query: 'payment' }).map((e) => e.id)).toEqual(['1']);
    expect(filterEntities(ENTITIES, { query: 'db/ledger' }).map((e) => e.id)).toEqual(['2']);
    expect(filterEntities(ENTITIES, { query: 'production' })).toHaveLength(2);
  });

  it('filters by type', () => {
    expect(filterEntities(ENTITIES, { types: ['DATABASE'] }).map((e) => e.id)).toEqual(['2']);
    expect(filterEntities(ENTITIES, { types: [] })).toHaveLength(2);
  });
});

describe('filterRelationships', () => {
  const byId = new Map([
    ['1', { id: '1', name: 'Payments API' }],
    ['2', { id: '2', name: 'Ledger DB' }],
  ]);
  const rels = [{ id: 'r1', sourceId: '1', targetId: '2', type: 'WRITES' }];

  it('resolves endpoint names when searching', () => {
    expect(filterRelationships(rels, byId, { query: 'ledger' })).toHaveLength(1);
    expect(filterRelationships(rels, byId, { query: 'nope' })).toHaveLength(0);
  });

  it('filters by relationship type', () => {
    expect(filterRelationships(rels, byId, { types: ['WRITES'] })).toHaveLength(1);
    expect(filterRelationships(rels, byId, { types: ['CALLS'] })).toHaveLength(0);
  });
});

describe('countBy', () => {
  it('counts and orders descending', () => {
    expect(countBy([{ type: 'A' }, { type: 'A' }, { type: 'B' }], 'type')).toEqual([
      { label: 'A', count: 2 },
      { label: 'B', count: 1 },
    ]);
  });
});
