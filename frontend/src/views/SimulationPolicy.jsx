import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  MAX_OPERATIONS,
  OPERATION_TYPES,
  ProposalBuilder,
} from '../components/ProposalBuilder.jsx';
import { PolicyResult } from '../components/PolicyResult.jsx';
import { SimulationResult } from '../components/SimulationResult.jsx';
import { EmptyPanel, ErrorPanel, LoadingPanel } from '../components/StatePanels.jsx';
import { ErrorKind } from '../api/errors.js';
import { RELATIONSHIP_TYPES } from '../lib/constants.js';

const IDLE_SIMULATION = {
  status: 'idle',
  data: null,
  error: null,
  request: null,
};
const IDLE_POLICY = {
  status: 'idle',
  data: null,
  error: null,
  request: null,
};

const ERROR_HINT = {
  [ErrorKind.NO_TOKEN]: 'No authenticated simulation or policy request was made.',
  [ErrorKind.BAD_REQUEST]:
    'The server rejected the request shape or value. Check the selected root and operation fields; the server remains authoritative.',
  [ErrorKind.UNAUTHORIZED]:
    'The request was not authenticated. Provide a fresh bearer token and retry.',
  [ErrorKind.FORBIDDEN]:
    'Simulation and policy require TWIN_ANALYZE. TWIN_READ alone is insufficient and returns 403; TWIN_ADMIN inherits TWIN_ANALYZE. No execution was attempted.',
  [ErrorKind.NOT_FOUND]:
    'An operation or root identifier no longer resolves in the twin. Refresh the snapshot or choose different identifiers.',
  [ErrorKind.CONFLICT]:
    'The proposal conflicts with the current twin state. Local hints are advisory; the server conflict result is authoritative.',
  [ErrorKind.SERVER]:
    'The control plane returned a server error. Only the failed action can be retried.',
  [ErrorKind.NETWORK]:
    'The control plane could not be reached. Only the failed action can be retried.',
  [ErrorKind.UNEXPECTED]:
    'The response was malformed, so no partial simulation or policy result is shown.',
};

function createOperation(type, id) {
  return {
    id,
    type,
    entity: '',
    source: '',
    target: '',
    relationshipType: 'CALLS',
  };
}

function validateDraft(root, operations) {
  const validation = { root: null, operations: {}, form: null };
  if (!root) return validation;

  if (operations.length > MAX_OPERATIONS) {
    validation.form = `At most ${MAX_OPERATIONS} operations can be added in this frontend.`;
  }

  const rootRefs = new Set(
    [root.id, root.externalRef].filter(
      (value) => typeof value === 'string' && value.length > 0,
    ),
  );

  operations.forEach((operation, index) => {
    const label = `Operation ${index + 1}`;
    if (!OPERATION_TYPES.includes(operation.type)) {
      validation.operations[operation.id] = `${label}: choose a supported operation type.`;
      return;
    }

    if (operation.type === 'REMOVE_ENTITY') {
      const entity = operation.entity.trim();
      if (!entity) {
        validation.operations[operation.id] =
          `${label}: enter the entity UUID or external reference to remove.`;
      } else if (rootRefs.has(entity)) {
        validation.operations[operation.id] =
          `${label} removes the selected analysis root. Choose a different entity or select another root.`;
      }
      return;
    }

    if (!operation.source.trim()) {
      validation.operations[operation.id] =
        `${label}: enter the source entity UUID or external reference.`;
      return;
    }
    if (!operation.target.trim()) {
      validation.operations[operation.id] =
        `${label}: enter the target entity UUID or external reference.`;
      return;
    }
    if (!RELATIONSHIP_TYPES.includes(operation.relationshipType)) {
      validation.operations[operation.id] =
        `${label}: choose a supported relationship type.`;
    }
  });

  return validation;
}

function wireOperation(operation) {
  if (operation.type === 'REMOVE_ENTITY') {
    return {
      type: operation.type,
      entity: operation.entity.trim(),
    };
  }
  return {
    type: operation.type,
    source: operation.source.trim(),
    target: operation.target.trim(),
    relationshipType: operation.relationshipType,
  };
}

function buildRequest(root, operations) {
  return {
    from: [root.id],
    operations: operations.map(wireOperation),
  };
}

function hintFor(error) {
  if (!error) return null;
  return (
    ERROR_HINT[error.kind] ??
    'Only the selected action is retried; no execution occurs.'
  );
}

export function SimulationPolicy({ entities = [], simulationApi }) {
  const [rootId, setRootId] = useState(null);
  const [operations, setOperations] = useState([]);
  const [simulation, setSimulation] = useState(IDLE_SIMULATION);
  const [policy, setPolicy] = useState(IDLE_POLICY);
  const simulationGeneration = useRef(0);
  const policyGeneration = useRef(0);
  const nextOperationId = useRef(1);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      simulationGeneration.current += 1;
      policyGeneration.current += 1;
    };
  }, []);

  const root = useMemo(
    () => entities.find((entity) => entity.id === rootId) ?? null,
    [entities, rootId],
  );

  const validation = useMemo(
    () => validateDraft(root, operations),
    [root, operations],
  );

  const canRun =
    Boolean(root) &&
    !validation.form &&
    Object.keys(validation.operations).length === 0;

  const invalidateResults = useCallback(() => {
    simulationGeneration.current += 1;
    policyGeneration.current += 1;
    setSimulation(IDLE_SIMULATION);
    setPolicy(IDLE_POLICY);
  }, []);

  const changeRoot = useCallback(
    (id) => {
      if (id === rootId) return;
      invalidateResults();
      setRootId(id);
    },
    [invalidateResults, rootId],
  );

  const addOperation = useCallback(
    (type) => {
      if (!root || operations.length >= MAX_OPERATIONS) return;
      if (!OPERATION_TYPES.includes(type)) return;
      invalidateResults();
      const id = `operation-${nextOperationId.current}`;
      nextOperationId.current += 1;
      setOperations((previous) => [...previous, createOperation(type, id)]);
    },
    [invalidateResults, operations.length, root],
  );

  const updateOperation = useCallback(
    (id, patch) => {
      invalidateResults();
      setOperations((previous) =>
        previous.map((operation) =>
          operation.id === id ? { ...operation, ...patch } : operation,
        ),
      );
    },
    [invalidateResults],
  );

  const removeOperation = useCallback(
    (id) => {
      invalidateResults();
      setOperations((previous) =>
        previous.filter((operation) => operation.id !== id),
      );
    },
    [invalidateResults],
  );

  const executeSimulation = useCallback(
    (request) => {
      const generation = simulationGeneration.current + 1;
      simulationGeneration.current = generation;
      setSimulation({
        status: 'loading',
        data: null,
        error: null,
        request,
      });

      simulationApi
        .runSimulation(request)
        .then((data) => {
          if (alive.current && generation === simulationGeneration.current) {
            setSimulation({
              status: 'success',
              data,
              error: null,
              request,
            });
          }
        })
        .catch((error) => {
          if (alive.current && generation === simulationGeneration.current) {
            setSimulation({
              status: 'error',
              data: null,
              error,
              request,
            });
          }
        });
    },
    [simulationApi],
  );

  const executePolicy = useCallback(
    (request) => {
      const generation = policyGeneration.current + 1;
      policyGeneration.current = generation;
      setPolicy({
        status: 'loading',
        data: null,
        error: null,
        request,
      });

      simulationApi
        .evaluatePolicy(request)
        .then((data) => {
          if (alive.current && generation === policyGeneration.current) {
            setPolicy({
              status: 'success',
              data,
              error: null,
              request,
            });
          }
        })
        .catch((error) => {
          if (alive.current && generation === policyGeneration.current) {
            setPolicy({
              status: 'error',
              data: null,
              error,
              request,
            });
          }
        });
    },
    [simulationApi],
  );

  const runSimulation = useCallback(() => {
    if (!canRun || simulation.status === 'loading') return;
    executeSimulation(buildRequest(root, operations));
  }, [canRun, executeSimulation, operations, root, simulation.status]);

  const runPolicy = useCallback(() => {
    if (!canRun || policy.status === 'loading') return;
    executePolicy(buildRequest(root, operations));
  }, [canRun, executePolicy, operations, policy.status, root]);

  const retrySimulation = useCallback(() => {
    if (simulation.request) executeSimulation(simulation.request);
  }, [executeSimulation, simulation.request]);

  const retryPolicy = useCallback(() => {
    if (policy.request) executePolicy(policy.request);
  }, [executePolicy, policy.request]);

  return (
    <div>
      <div className="view-header">
        <div>
          <h1 className="view-header__title">Simulation &amp; Policy</h1>
          <p className="view-header__hint">
            Build one hypothetical proposal from a loaded twin root, then run a
            simulated impact/risk result or an independent advisory policy
            evaluation. Entering this view sends no request.
          </p>
        </div>
      </div>

      <div className="notice" role="note">
        <strong>TWIN_ANALYZE required.</strong> TWIN_READ alone is insufficient and
        returns 403; TWIN_ADMIN inherits TWIN_ANALYZE. This view never executes,
        approves, persists, or modifies the twin or any target system.
      </div>

      <ProposalBuilder
        entities={entities}
        root={root}
        rootId={rootId}
        onRootChange={changeRoot}
        operations={operations}
        onAddOperation={addOperation}
        onUpdateOperation={updateOperation}
        onRemoveOperation={removeOperation}
        validation={validation}
      />

      <div className="analysis-action simulation-actions">
        <div className="analysis-selection" aria-live="polite">
          {root ? (
            <>
              <span className="analysis-selection__label">Request root</span>
              <span className="analysis-selection__name">{root.name}</span>
              <span className="mono">{root.externalRef ?? root.id}</span>
              <span className="hint">
                {operations.length === 0
                  ? 'Baseline-only: operations []'
                  : `${operations.length} operation${operations.length === 1 ? '' : 's'}`}
              </span>
            </>
          ) : (
            <span className="hint">
              Select one root entity. No request is sent automatically.
            </span>
          )}
          {!canRun && root ? (
            <span className="field-error">
              Resolve the operation validation messages before running.
            </span>
          ) : null}
        </div>
        <div className="simulation-actions__buttons">
          <button
            type="button"
            className="btn btn--primary"
            disabled={!canRun || simulation.status === 'loading'}
            onClick={runSimulation}
          >
            Run simulation
          </button>
          <button
            type="button"
            className="btn"
            disabled={!canRun || policy.status === 'loading'}
            onClick={runPolicy}
          >
            Evaluate policy
          </button>
        </div>
      </div>

      <p className="hint">
        Backend defaults apply when optional fields are omitted: max depth 6 and
        reported types SERVICE, API, DATABASE, and TABLE. The policy endpoint
        re-runs simulation server-side; it does not reuse the simulation result.
      </p>

      <div className="simulation-results">
        <div className="simulation-result-panel">
          {simulation.status === 'idle' ? (
            <EmptyPanel
              title="No simulation yet"
              body="Choose Run simulation to request a hypothetical simulation. Selecting a root or editing operations does not send a request."
            />
          ) : simulation.status === 'error' ? (
            <ErrorPanel
              error={simulation.error}
              onRetry={retrySimulation}
              hint={hintFor(simulation.error)}
            />
          ) : simulation.status === 'loading' ? (
            <LoadingPanel label="Running hypothetical simulation" />
          ) : (
            <SimulationResult simulation={simulation.data} />
          )}
        </div>

        <div className="simulation-result-panel">
          {policy.status === 'idle' ? (
            <EmptyPanel
              title="No policy evaluation yet"
              body="Choose Evaluate policy to request an independent advisory evaluation. It does not reuse a previous simulation result."
            />
          ) : policy.status === 'error' ? (
            <ErrorPanel
              error={policy.error}
              onRetry={retryPolicy}
              hint={hintFor(policy.error)}
            />
          ) : policy.status === 'loading' ? (
            <LoadingPanel label="Evaluating advisory policy" />
          ) : (
            <PolicyResult policy={policy.data} />
          )}
        </div>
      </div>
    </div>
  );
}
