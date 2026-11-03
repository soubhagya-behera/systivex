import { useId, useState } from 'react';
import { EmptyPanel } from './StatePanels.jsx';
import { RELATIONSHIP_TYPES } from '../lib/constants.js';
import { titleCaseType } from '../lib/format.js';

export const MAX_OPERATIONS = 10;

export const OPERATION_TYPES = [
  'REMOVE_ENTITY',
  'ADD_RELATIONSHIP',
  'REMOVE_RELATIONSHIP',
];

function emptyOperation(type) {
  return {
    type,
    entity: '',
    source: '',
    target: '',
    relationshipType: 'CALLS',
  };
}

function ErrorText({ id, children }) {
  if (!children) return null;
  return (
    <p className="field-error" id={id} role="alert">
      {children}
    </p>
  );
}

function OperationFields({
  operation,
  index,
  entities,
  root,
  error,
  disabled,
  onChange,
}) {
  const prefix = useId();
  const number = index + 1;
  const errorId = `${prefix}-error`;
  const errorProps = error
    ? { 'aria-invalid': 'true', 'aria-describedby': errorId }
    : {};

  const update = (patch) => onChange(operation.id, patch);

  return (
    <fieldset className="operation-card" disabled={disabled}>
      <legend className="operation-card__legend">
        Operation {number} · {operation.type}
      </legend>

      <div className="form-row">
        <label className="field-label" htmlFor={`${prefix}-type`}>
          Operation type for operation {number}
        </label>
        <select
          id={`${prefix}-type`}
          className="input"
          value={operation.type}
          onChange={(event) => {
            const type = event.target.value;
            update({ ...emptyOperation(type), id: operation.id });
          }}
        >
          {OPERATION_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </div>

      {operation.type === 'REMOVE_ENTITY' ? (
        <>
          <div className="form-row">
            <label className="field-label" htmlFor={`${prefix}-entity`}>
              Entity to remove for operation {number} (UUID or external reference)
            </label>
            <input
              id={`${prefix}-entity`}
              className="input"
              value={operation.entity}
              onChange={(event) => update({ entity: event.target.value })}
              placeholder={root ? root.id : 'entity UUID or external reference'}
              {...errorProps}
            />
          </div>
          <p className="hint">
            The selected analysis root cannot be removed. Loaded entities:{' '}
            {entities.length}.
          </p>
        </>
      ) : (
        <>
          <div className="operation-grid">
            <div className="form-row">
              <label className="field-label" htmlFor={`${prefix}-source`}>
                Source entity for operation {number}
              </label>
              <input
                id={`${prefix}-source`}
                className="input"
                value={operation.source}
                onChange={(event) => update({ source: event.target.value })}
                placeholder="entity UUID or external reference"
                {...errorProps}
              />
            </div>
            <div className="form-row">
              <label className="field-label" htmlFor={`${prefix}-target`}>
                Target entity for operation {number}
              </label>
              <input
                id={`${prefix}-target`}
                className="input"
                value={operation.target}
                onChange={(event) => update({ target: event.target.value })}
                placeholder="entity UUID or external reference"
                {...errorProps}
              />
            </div>
            <div className="form-row">
              <label className="field-label" htmlFor={`${prefix}-relationship`}>
                Relationship type for operation {number}
              </label>
              <select
                id={`${prefix}-relationship`}
                className="input"
                value={operation.relationshipType}
                onChange={(event) =>
                  update({ relationshipType: event.target.value })
                }
              >
                {RELATIONSHIP_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </>
      )}

      <ErrorText id={errorId}>{error}</ErrorText>
    </fieldset>
  );
}

export function ProposalBuilder({
  entities = [],
  root = null,
  rootId = null,
  onRootChange,
  operations = [],
  onAddOperation,
  onUpdateOperation,
  onRemoveOperation,
  validation = { root: null, operations: {}, form: null },
}) {
  const prefix = useId();
  const [newType, setNewType] = useState(OPERATION_TYPES[0]);
  const atLimit = operations.length >= MAX_OPERATIONS;

  return (
    <section className="analysis-section proposal-builder" aria-label="Proposal builder">
      <div className="analysis-section__head">
        <div>
          <h2 className="analysis-section__title">Hypothetical proposal</h2>
          <p className="hint">
            Select one root from the loaded twin snapshot, then optionally add
            operations. Nothing is sent until you choose Run simulation or Evaluate
            policy.
          </p>
        </div>
        <span className="badge">Live only</span>
      </div>

      {entities.length === 0 ? (
        <EmptyPanel
          title="No entities available"
          body="Load a twin snapshot before selecting an analysis root."
        />
      ) : (
        <div className="form-row">
          <label className="field-label" htmlFor={`${prefix}-root`}>
            Analysis root
          </label>
          <select
            id={`${prefix}-root`}
            className="input"
            value={rootId ?? ''}
            onChange={(event) => onRootChange?.(event.target.value || null)}
            aria-invalid={validation.root ? 'true' : undefined}
            aria-describedby={validation.root ? `${prefix}-root-error` : undefined}
          >
            <option value="">Select one root entity</option>
            {entities.map((entity) => (
              <option key={entity.id} value={entity.id}>
                {entity.name} · {entity.type} · {entity.externalRef ?? entity.id}
              </option>
            ))}
          </select>
          <ErrorText id={`${prefix}-root-error`}>{validation.root}</ErrorText>
          {!root ? (
            <p className="hint">
              Select one root entity to enable the simulation and policy actions.
            </p>
          ) : null}
        </div>
      )}

      {root ? (
        <div className="analysis-selection proposal-builder__root" aria-live="polite">
          <span className="analysis-selection__label">Selected root</span>
          <span className="analysis-selection__name">{root.name}</span>
          <span className="badge badge--type">{titleCaseType(root.type)}</span>
          <span className="mono">{root.externalRef ?? root.id}</span>
        </div>
      ) : null}

      <div className="proposal-builder__operations-head">
        <div>
          <h3 className="analysis-subtitle">Operations ({operations.length}/10)</h3>
          <p className="hint">
            At most 10 operations can be added here. This is a frontend usability
            limit only, not a backend validation or security limit. The server remains
            authoritative.
          </p>
        </div>
        <div className="proposal-builder__add">
          <label className="visually-hidden" htmlFor={`${prefix}-new-type`}>
            New operation type
          </label>
          <select
            id={`${prefix}-new-type`}
            className="input"
            value={newType}
            onChange={(event) => setNewType(event.target.value)}
            disabled={!root}
          >
            {OPERATION_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="btn"
            disabled={!root || atLimit}
            onClick={() => onAddOperation?.(newType)}
          >
            Add operation
          </button>
        </div>
      </div>

      {validation.form ? (
        <ErrorText id={`${prefix}-form-error`}>{validation.form}</ErrorText>
      ) : null}

      {operations.length === 0 ? (
        <EmptyPanel
          title="No operations added"
          body="The request will be a baseline-only simulation: operations will be sent as an empty list."
        />
      ) : (
        <div className="operation-list">
          {operations.map((operation, index) => (
            <div className="operation-row" key={operation.id}>
              <OperationFields
                operation={operation}
                index={index}
                entities={entities}
                root={root}
                error={validation.operations?.[operation.id] ?? null}
                disabled={false}
                onChange={onUpdateOperation}
              />
              <button
                type="button"
                className="btn btn--danger"
                onClick={() => onRemoveOperation?.(operation.id)}
              >
                Remove operation {index + 1}
              </button>
            </div>
          ))}
        </div>
      )}

      <p className="hint">
        Client checks are advisory only. The server resolves identifiers, validates
        the proposal shape, and reports 409 conflicts authoritatively.
      </p>
    </section>
  );
}
