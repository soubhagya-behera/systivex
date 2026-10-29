/** Canonical enums mirrored from the backend model (read-only reference). */
export const ENTITY_TYPES = [
  'SERVICE',
  'API',
  'DATABASE',
  'TABLE',
  'REPOSITORY',
  'DEPLOYMENT',
  'CONFIGURATION',
];

export const RELATIONSHIP_TYPES = [
  'CALLS',
  'DEPENDS_ON',
  'READS',
  'WRITES',
  'DEPLOYED_AS',
  'EXPOSES',
  'CONTAINS',
  'CONFIGURES',
];

export const VIEWS = [
  'overview',
  'entities',
  'relationships',
  'graph',
  'impact-risk',
  'audit-integrity',
];
