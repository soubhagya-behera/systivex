import { describe, it, expect } from 'vitest';
import { createTwinApi } from '../api/twin.js';

describe('createTwinApi', () => {
  it('only ever calls the four read-only twin endpoints', async () => {
    const calls = [];
    const twin = createTwinApi({
      request: (path) => {
        calls.push(path);
        return Promise.resolve([]);
      },
    });

    await twin.listEntities();
    await twin.listRelationships();
    await twin.getEntity('e-1');
    await twin.getRelationship('r-1');

    expect(calls).toEqual([
      '/api/v1/twin/entities',
      '/api/v1/twin/relationships',
      '/api/v1/twin/entities/e-1',
      '/api/v1/twin/relationships/r-1',
    ]);
  });

  it('rejects a payload that is not a list', async () => {
    const twin = createTwinApi({ request: () => Promise.resolve({ not: 'array' }) });
    await expect(twin.listEntities()).rejects.toMatchObject({ kind: 'unexpected' });
    await expect(twin.listRelationships()).rejects.toMatchObject({ kind: 'unexpected' });
  });
});
