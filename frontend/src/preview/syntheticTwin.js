/**
 * Fictional sample records for the development-only local preview.
 *
 * Nothing here came from a backend, a real environment, or a credential store.
 * This module is loaded by a development-only dynamic import.
 */
export const PREVIEW_BANNER =
  'LOCAL PREVIEW — SYNTHETIC DATA — NOT CONNECTED TO BACKEND';

export const PREVIEW_SNAPSHOT_AT = '2026-10-24T09:00:00Z';

const SYNTHETIC_ENVIRONMENT = 'local-preview';

export const SYNTHETIC_ENTITIES = [
  {
    id: 'preview-service-gateway',
    type: 'SERVICE',
    name: 'sample-aurora-gateway',
    externalRef: 'sample://services/aurora-gateway',
    environment: SYNTHETIC_ENVIRONMENT,
    metadata: {
      owner: 'fictional-aurora-platform',
      runtime: {
        managedBy: 'synthetic-preview-connector',
        connectorVersion: 'preview-v1',
        observedAt: '2026-10-24T09:15:00Z',
        health: { available: true, status: 'UP' },
        counters: { 'sample.requests': 128, 'sample.errors': 2 },
        limitations: ['Fictional sample value for local UI inspection only.'],
      },
    },
    createdAt: '2026-10-01T08:00:00Z',
    updatedAt: '2026-10-24T09:15:00Z',
  },
  {
    id: 'preview-service-orders',
    type: 'SERVICE',
    name: 'sample-aurora-orders',
    externalRef: 'sample://services/aurora-orders',
    environment: SYNTHETIC_ENVIRONMENT,
    metadata: {
      owner: 'fictional-aurora-platform',
      runtime: {
        managedBy: 'synthetic-preview-connector',
        connectorVersion: 'preview-v1',
        observedAt: '2026-10-24T09:10:00Z',
        health: { available: true, status: 'DEGRADED' },
        counters: { 'sample.queue_depth': 42 },
        limitations: ['Synthetic sample; not observed from a real service.'],
      },
    },
    createdAt: '2026-10-01T08:05:00Z',
    updatedAt: '2026-10-24T09:10:00Z',
  },
  {
    id: 'preview-api-orders',
    type: 'API',
    name: 'sample-aurora-orders-api',
    externalRef: 'sample://apis/aurora-orders',
    environment: SYNTHETIC_ENVIRONMENT,
    metadata: { owner: 'fictional-aurora-platform', contract: 'sample-v1' },
    createdAt: '2026-10-02T10:00:00Z',
    updatedAt: '2026-10-20T11:00:00Z',
  },
  {
    id: 'preview-db-orders',
    type: 'DATABASE',
    name: 'sample-aurora-orders-db',
    externalRef: 'sample://databases/aurora-orders',
    environment: SYNTHETIC_ENVIRONMENT,
    metadata: {
      owner: 'fictional-aurora-data',
      runtime: {
        managedBy: 'synthetic-preview-connector',
        connectorVersion: 'preview-v1',
        observedAt: '2026-10-24T09:05:00Z',
        health: { available: false },
        counters: {},
        limitations: ['Synthetic sample: database health was not observed.'],
      },
    },
    createdAt: '2026-10-02T10:10:00Z',
    updatedAt: '2026-10-24T09:05:00Z',
  },
  {
    id: 'preview-table-orders',
    type: 'TABLE',
    name: 'sample-aurora-orders-table',
    externalRef: 'sample://tables/orders',
    environment: SYNTHETIC_ENVIRONMENT,
    metadata: { owner: 'fictional-aurora-data', retention: 'sample-only' },
    createdAt: '2026-10-02T10:20:00Z',
    updatedAt: '2026-10-18T14:00:00Z',
  },
  {
    id: 'preview-repo-orders',
    type: 'REPOSITORY',
    name: 'sample-aurora-orders-repo',
    externalRef: 'sample://repositories/aurora-orders',
    environment: SYNTHETIC_ENVIRONMENT,
    metadata: { owner: 'fictional-aurora-engineering', defaultBranch: 'sample-main' },
    createdAt: '2026-10-01T07:30:00Z',
    updatedAt: '2026-10-22T16:00:00Z',
  },
  {
    id: 'preview-deployment-orders',
    type: 'DEPLOYMENT',
    name: 'sample-aurora-orders-deployment',
    externalRef: 'sample://deployments/aurora-orders',
    environment: SYNTHETIC_ENVIRONMENT,
    metadata: { owner: 'fictional-aurora-platform', image: 'sample/orders:preview' },
    createdAt: '2026-10-03T12:00:00Z',
    updatedAt: '2026-10-23T08:00:00Z',
  },
  {
    id: 'preview-config-flags',
    type: 'CONFIGURATION',
    name: 'sample-aurora-feature-flags',
    externalRef: 'sample://configuration/aurora-flags',
    environment: SYNTHETIC_ENVIRONMENT,
    metadata: { owner: 'fictional-aurora-platform', values: { previewOnly: true } },
    createdAt: '2026-10-03T12:10:00Z',
    updatedAt: '2026-10-21T07:45:00Z',
  },
];

export const SYNTHETIC_RELATIONSHIPS = [
  {
    id: 'preview-rel-gateway-calls-orders',
    sourceId: 'preview-service-gateway',
    targetId: 'preview-service-orders',
    type: 'CALLS',
    createdAt: '2026-10-05T09:00:00Z',
  },
  {
    id: 'preview-rel-orders-exposes-api',
    sourceId: 'preview-service-orders',
    targetId: 'preview-api-orders',
    type: 'EXPOSES',
    createdAt: '2026-10-05T09:01:00Z',
  },
  {
    id: 'preview-rel-orders-depends-db',
    sourceId: 'preview-service-orders',
    targetId: 'preview-db-orders',
    type: 'DEPENDS_ON',
    createdAt: '2026-10-05T09:02:00Z',
  },
  {
    id: 'preview-rel-orders-writes-table',
    sourceId: 'preview-service-orders',
    targetId: 'preview-table-orders',
    type: 'WRITES',
    createdAt: '2026-10-05T09:03:00Z',
  },
  {
    id: 'preview-rel-db-contains-table',
    sourceId: 'preview-db-orders',
    targetId: 'preview-table-orders',
    type: 'CONTAINS',
    createdAt: '2026-10-05T09:04:00Z',
  },
  {
    id: 'preview-rel-repo-deployed-as',
    sourceId: 'preview-repo-orders',
    targetId: 'preview-deployment-orders',
    type: 'DEPLOYED_AS',
    createdAt: '2026-10-05T09:05:00Z',
  },
  {
    id: 'preview-rel-deployment-registers-orders',
    sourceId: 'preview-deployment-orders',
    targetId: 'preview-service-orders',
    type: 'DEPLOYED_AS',
    createdAt: '2026-10-05T09:06:00Z',
  },
  {
    id: 'preview-rel-deployment-configures-flags',
    sourceId: 'preview-deployment-orders',
    targetId: 'preview-config-flags',
    type: 'CONFIGURES',
    createdAt: '2026-10-05T09:07:00Z',
  },
  {
    id: 'preview-rel-api-reads-db',
    sourceId: 'preview-api-orders',
    targetId: 'preview-db-orders',
    type: 'READS',
    createdAt: '2026-10-05T09:08:00Z',
  },
];

export function loadSyntheticPreview() {
  return {
    banner: PREVIEW_BANNER,
    entities: SYNTHETIC_ENTITIES,
    relationships: SYNTHETIC_RELATIONSHIPS,
    snapshotLoadedAt: new Date(PREVIEW_SNAPSHOT_AT).getTime(),
  };
}
