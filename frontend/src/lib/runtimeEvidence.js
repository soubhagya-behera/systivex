const isRecord = (value) =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const asString = (value) => {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
};

function missing() {
  return {
    state: 'missing',
    reason: null,
    source: null,
    sourceState: 'missing',
    connectorVersion: null,
    observedAt: null,
    observedAtMs: null,
    health: { kind: 'unavailable', status: null },
    counters: [],
    countersState: 'missing',
    limitations: [],
    limitationsState: 'missing',
    notes: [],
  };
}

function parseSource(value) {
  if (value === null || value === undefined) {
    return { state: 'missing', value: null };
  }
  if (typeof value !== 'string') {
    return { state: 'malformed', value: null };
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return { state: 'missing', value: null };
  }
  return { state: 'present', value: trimmed };
}

function unrecognized(reason, source = null) {
  return {
    ...missing(),
    state: 'unrecognized',
    reason,
    source,
  };
}

function parseHealth(value, notes) {
  if (value === null || value === undefined) {
    return { kind: 'unavailable', status: null };
  }
  if (!isRecord(value)) {
    notes.push('Health metadata was malformed; no health status is inferred.');
    return { kind: 'unrecognized', status: null };
  }
  if (value.available === false) {
    return { kind: 'unavailable', status: null };
  }
  if (value.available !== true) {
    notes.push('Health availability was not a boolean; no health status is inferred.');
    return { kind: 'unrecognized', status: null };
  }
  const status = asString(value.status);
  if (!status) {
    notes.push('Health status was missing; no health status is inferred.');
    return { kind: 'unrecognized', status: null };
  }
  return { kind: 'observed', status };
}

function parseCounters(value, notes) {
  if (value === null || value === undefined) {
    return { state: 'missing', values: [] };
  }
  if (!isRecord(value)) {
    notes.push('Counter metadata was malformed and is not shown.');
    return { state: 'malformed', values: [] };
  }
  const counters = [];
  let invalid = false;
  for (const [name, count] of Object.entries(value)) {
    if (Number.isFinite(count)) {
      counters.push({ name, value: count });
    } else {
      invalid = true;
    }
  }
  if (invalid) {
    notes.push('Some counter values were not finite numbers and are not shown.');
    return {
      state: counters.length > 0 ? 'partial' : 'malformed',
      values: counters.sort((a, b) => a.name.localeCompare(b.name)),
    };
  }
  return {
    state: counters.length > 0 ? 'present' : 'empty',
    values: counters.sort((a, b) => a.name.localeCompare(b.name)),
  };
}

function parseLimitations(value, notes) {
  if (value === null || value === undefined) {
    return { state: 'missing', values: [] };
  }
  if (!Array.isArray(value)) {
    notes.push('Limitations metadata was malformed and is not shown.');
    return { state: 'malformed', values: [] };
  }

  const values = value.filter(
    (item) => typeof item === 'string' && item.trim().length > 0,
  );
  const invalidCount = value.length - values.length;

  if (invalidCount === 0) {
    return { state: values.length > 0 ? 'present' : 'empty', values };
  }
  if (values.length === 0) {
    notes.push('Limitations metadata contained no valid entries and is not shown.');
    return { state: 'malformed', values: [] };
  }

  notes.push('Some limitation values were invalid and are not shown.');
  return { state: 'partial', values };
}

export function parseRuntimeEvidence(metadata) {
  if (metadata === null || metadata === undefined) return missing();
  if (!isRecord(metadata)) {
    return unrecognized('Entity metadata was not an object.');
  }
  if (!Object.prototype.hasOwnProperty.call(metadata, 'runtime') || metadata.runtime == null) {
    return missing();
  }

  const runtime = metadata.runtime;
  if (!isRecord(runtime)) {
    return unrecognized('The runtime metadata value was not an object.');
  }

  const source = parseSource(runtime.managedBy);
  const observedAtText = asString(runtime.observedAt);
  if (!observedAtText) {
    return unrecognized('The observation timestamp was missing.', source.value);
  }

  const observed = new Date(observedAtText);
  if (Number.isNaN(observed.getTime())) {
    return unrecognized('The observation timestamp was invalid.', source.value);
  }

  const notes = [];
  const counters = parseCounters(runtime.counters, notes);
  const limitations = parseLimitations(runtime.limitations, notes);
  return {
    state: 'available',
    reason: null,
    source: source.value,
    sourceState: source.state,
    connectorVersion: asString(runtime.connectorVersion),
    observedAt: observed.toISOString(),
    observedAtMs: observed.getTime(),
    health: parseHealth(runtime.health, notes),
    counters: counters.values,
    countersState: counters.state,
    limitations: limitations.values,
    limitationsState: limitations.state,
    notes,
  };
}
