import { ApiError, ErrorKind } from './errors.js';

// Verified Phase 13 S1 read-only endpoints (SecurityConfig.java, D28):
//   GET /api/v1/twin/entities            -> TWIN_READ
//   GET /api/v1/twin/entities/{id}       -> TWIN_READ
//   GET /api/v1/twin/relationships       -> TWIN_READ
//   GET /api/v1/twin/relationships/{id}  -> TWIN_READ
// No simulations, policy evaluations, sync, observations, writes, or proposal
// mutations are reachable from this slice.
const ENTITIES = '/api/v1/twin/entities';
const RELATIONSHIPS = '/api/v1/twin/relationships';

function expectArray(value) {
  if (!Array.isArray(value)) {
    throw new ApiError(ErrorKind.UNEXPECTED);
  }
  return value;
}

export function createTwinApi(client) {
  return {
    listEntities: () => client.request(ENTITIES).then(expectArray),
    getEntity: (id) => client.request(`${ENTITIES}/${encodeURIComponent(id)}`),
    listRelationships: () => client.request(RELATIONSHIPS).then(expectArray),
    getRelationship: (id) =>
      client.request(`${RELATIONSHIPS}/${encodeURIComponent(id)}`),
  };
}

export { ENTITIES, RELATIONSHIPS };
