import { ApiError, ErrorKind } from './errors.js';

const SIMULATIONS = '/api/v1/twin/simulations';
const POLICY_EVALUATIONS = '/api/v1/twin/policy-evaluations';

function malformed() {
  throw new ApiError(ErrorKind.UNEXPECTED);
}

function expectObject(value) {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    malformed();
  }
  return value;
}

function expectArray(value) {
  if (!Array.isArray(value)) malformed();
  return value;
}

function expectString(value) {
  if (typeof value !== 'string') malformed();
  return value;
}

function expectNullableString(value) {
  if (value !== null && typeof value !== 'string') malformed();
  return value;
}

function expectNumber(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) malformed();
  return value;
}

function expectBoolean(value) {
  if (typeof value !== 'boolean') malformed();
  return value;
}

function expectArrayOf(value, validateItem) {
  expectArray(value).forEach(validateItem);
  return value;
}

function validateStart(value) {
  const start = expectObject(value);
  expectString(start.id);
  expectString(start.type);
  expectString(start.name);
  expectNullableString(start.externalRef);
  expectNullableString(start.environment);
  return start;
}

function validateImpacted(value) {
  const entity = expectObject(value);
  expectString(entity.id);
  expectString(entity.type);
  expectString(entity.name);
  expectNullableString(entity.externalRef);
  expectNumber(entity.depth);
  expectNullableString(entity.viaStart);
  return entity;
}

function validateStep(value) {
  const step = expectObject(value);
  expectString(step.sourceId);
  expectNullableString(step.sourceExternalRef);
  expectString(step.targetId);
  expectNullableString(step.targetExternalRef);
  expectString(step.type);
  expectString(step.direction);
  return step;
}

function validatePath(value) {
  const path = expectObject(value);
  expectString(path.targetId);
  expectNullableString(path.targetExternalRef);
  expectNumber(path.depth);
  expectArrayOf(path.steps, validateStep);
  expectNullableString(path.explanation);
  return path;
}

function validateLimits(value) {
  const limits = expectObject(value);
  expectNumber(limits.maxStarts);
  expectNumber(limits.maxDepthAllowed);
  expectNumber(limits.maxNodes);
  expectNumber(limits.maxPaths);
  return limits;
}

function validateImpact(value) {
  const impact = expectObject(value);
  expectArrayOf(impact.startedFrom, validateStart);
  expectArrayOf(impact.impacted, validateImpacted);
  expectArrayOf(impact.paths, validatePath);
  expectNumber(impact.maxDepth);
  expectNumber(impact.nodeCount);
  expectNumber(impact.pathCount);
  expectBoolean(impact.truncated);
  expectNullableString(impact.truncationReason);
  expectBoolean(impact.cycleEncountered);
  expectNullableString(impact.snapshotObservedAt);
  expectNumber(impact.entityCount);
  expectNumber(impact.relationshipCount);
  validateLimits(impact.limits);
  expectString(impact.note);
  return impact;
}

function validateRiskEntry(value) {
  const entry = expectObject(value);
  expectString(entry.id);
  expectString(entry.type);
  expectString(entry.name);
  expectNullableString(entry.externalRef);
  expectNumber(entry.depth);
  expectNullableString(entry.viaStart);
  expectString(entry.level);
  expectArrayOf(entry.reasons, expectString);
  expectBoolean(entry.uncertain);
  expectBoolean(entry.stale);
  return entry;
}

function validateRisk(value) {
  const risk = expectObject(value);
  expectArrayOf(risk.startedFrom, validateStart);
  expectArrayOf(risk.risks, validateRiskEntry);
  expectNumber(risk.maxDepth);
  expectString(risk.taxonomyVersion);
  expectNullableString(risk.snapshotObservedAt);
  expectNumber(risk.entityCount);
  expectNumber(risk.relationshipCount);
  expectString(risk.note);
  return risk;
}

function validateScope(value) {
  const scope = expectObject(value);
  validateImpact(scope.impact);
  validateRisk(scope.risk);
  return scope;
}

function validateSettings(value) {
  const settings = expectObject(value);
  expectNumber(settings.maxDepth);
  expectArrayOf(settings.includeTypes, expectString);
  expectNumber(settings.rootCount);
  return settings;
}

function validateGraph(value) {
  const graph = expectObject(value);
  expectNumber(graph.baselineEntityCount);
  expectNumber(graph.hypotheticalEntityCount);
  expectNumber(graph.baselineRelationshipCount);
  expectNumber(graph.hypotheticalRelationshipCount);
  expectNumber(graph.removedEntityCount);
  expectNumber(graph.addedRelationshipCount);
  expectNumber(graph.removedRelationshipCount);
  return graph;
}

function validateRiskLevelChange(value) {
  const change = expectObject(value);
  expectString(change.entityRef);
  expectString(change.before);
  expectString(change.after);
  return change;
}

function validateDelta(value) {
  const delta = expectObject(value);
  expectNumber(delta.beforeImpactedCount);
  expectNumber(delta.afterImpactedCount);
  expectNumber(delta.impactedAddedCount);
  expectNumber(delta.impactedRemovedCount);
  expectArrayOf(delta.impactedAdded, expectString);
  expectArrayOf(delta.impactedRemoved, expectString);
  expectNumber(delta.beforeRiskCount);
  expectNumber(delta.afterRiskCount);
  expectNumber(delta.riskAddedCount);
  expectNumber(delta.riskRemovedCount);
  expectArrayOf(delta.riskLevelChanges, validateRiskLevelChange);
  expectNumber(delta.uncertainBeforeCount);
  expectNumber(delta.uncertainAfterCount);
  expectBoolean(delta.truncationChanged);
  return delta;
}

function validateLimitations(value) {
  return expectArrayOf(value, expectString);
}

export function validateSimulationResponse(value) {
  const response = expectObject(value);
  expectBoolean(response.simulated);
  expectString(response.advisory);
  validateSettings(response.settings);
  validateScope(response.before);
  validateScope(response.after);
  validateGraph(response.graph);
  validateDelta(response.delta);
  validateLimitations(response.limitations);
  return response;
}

export function validatePolicyEvaluationResponse(value) {
  const response = expectObject(value);
  expectString(response.decision);
  expectString(response.policyVersion);
  expectString(response.advisory);
  validateSettings(response.settings);
  expectArrayOf(response.rulesFired, (rule) => {
    const fired = expectObject(rule);
    expectString(fired.code);
    expectString(fired.outcome);
    expectString(fired.detail);
  });
  const simulation = expectObject(response.simulation);
  validateGraph(simulation.graph);
  validateDelta(simulation.delta);
  validateLimitations(simulation.limitations);
  expectString(response.note);
  return response;
}

function post(client, path, request, validate) {
  return client
    .request(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })
    .then(validate);
}

export function createSimulationApi(client) {
  return {
    runSimulation: (request) =>
      post(client, SIMULATIONS, request, validateSimulationResponse),
    evaluatePolicy: (request) =>
      post(client, POLICY_EVALUATIONS, request, validatePolicyEvaluationResponse),
  };
}

export { POLICY_EVALUATIONS, SIMULATIONS };
