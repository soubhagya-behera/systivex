import { describe, expect, it } from 'vitest';
import { parseRuntimeEvidence } from '../lib/runtimeEvidence.js';

const runtime = (overrides = {}) => ({
  managedBy: 'telemetry-connector',
  connectorVersion: 'phase6-v1',
  observedAt: '2026-10-14T09:50:00Z',
  health: { available: true, status: 'UP' },
  counters: { 'checkout.success': 4 },
  limitations: ['health only'],
  ...overrides,
});

describe('parseRuntimeEvidence', () => {
  it('reports missing evidence without inventing a health state', () => {
    const missing = parseRuntimeEvidence({ owner: 'team-payments' });
    const empty = parseRuntimeEvidence(null);

    expect(missing.state).toBe('missing');
    expect(missing.health.kind).toBe('unavailable');
    expect(missing.countersState).toBe('missing');
    expect(empty.state).toBe('missing');
    expect(empty.health.status).toBeNull();
    expect(empty.countersState).toBe('missing');
  });

  it('rejects non-object runtime values and invalid observation times', () => {
    expect(parseRuntimeEvidence({ runtime: 'not-an-object' })).toMatchObject({
      state: 'unrecognized',
      reason: 'The runtime metadata value was not an object.',
    });

    const invalid = parseRuntimeEvidence({
      runtime: runtime({ observedAt: 'not-a-timestamp' }),
    });
    expect(invalid.state).toBe('unrecognized');
    expect(invalid.health.kind).toBe('unavailable');
    expect(invalid.health.status).toBeNull();
  });

  it('parses stored source, observation time, health, counters, and limitations', () => {
    const evidence = parseRuntimeEvidence({
      runtime: runtime({
        counters: { 'checkout.success': 4, 'checkout.failure': 1 },
      }),
    });

    expect(evidence).toMatchObject({
      state: 'available',
      source: 'telemetry-connector',
      connectorVersion: 'phase6-v1',
      observedAt: '2026-10-14T09:50:00.000Z',
      health: { kind: 'observed', status: 'UP' },
      countersState: 'present',
      limitations: ['health only'],
    });
    expect(evidence.counters).toEqual([
      { name: 'checkout.failure', value: 1 },
      { name: 'checkout.success', value: 4 },
    ]);
  });

  it('distinguishes missing, blank, and malformed source metadata', () => {
    const withoutSource = runtime();
    delete withoutSource.managedBy;

    const missing = parseRuntimeEvidence({ runtime: withoutSource });
    const blank = parseRuntimeEvidence({ runtime: runtime({ managedBy: '   ' }) });
    const malformed = parseRuntimeEvidence({ runtime: runtime({ managedBy: 42 }) });

    expect(missing).toMatchObject({
      state: 'available',
      source: null,
      sourceState: 'missing',
    });
    expect(blank).toMatchObject({ source: null, sourceState: 'missing' });
    expect(malformed).toMatchObject({
      state: 'available',
      source: null,
      sourceState: 'malformed',
    });
    expect(malformed.observedAt).toBe('2026-10-14T09:50:00.000Z');
  });

  it('distinguishes missing, malformed, and valid but empty counters', () => {
    const withoutCounters = runtime();
    delete withoutCounters.counters;

    const missing = parseRuntimeEvidence({ runtime: withoutCounters });
    const empty = parseRuntimeEvidence({ runtime: runtime({ counters: {} }) });
    const malformed = parseRuntimeEvidence({
      runtime: runtime({ counters: 'not-an-object' }),
    });

    expect(missing).toMatchObject({
      state: 'available',
      source: 'telemetry-connector',
      counters: [],
      countersState: 'missing',
    });
    expect(empty).toMatchObject({ counters: [], countersState: 'empty' });
    expect(malformed).toMatchObject({
      state: 'available',
      counters: [],
      countersState: 'malformed',
    });
    expect(malformed.observedAt).toBe('2026-10-14T09:50:00.000Z');
    expect(malformed.notes).toContain(
      'Counter metadata was malformed and is not shown.',
    );
  });

  it('distinguishes missing, empty, malformed, and partially valid limitations', () => {
    const withoutLimitations = runtime();
    delete withoutLimitations.limitations;

    const missing = parseRuntimeEvidence({ runtime: withoutLimitations });
    const empty = parseRuntimeEvidence({ runtime: runtime({ limitations: [] }) });
    const malformed = parseRuntimeEvidence({
      runtime: runtime({ limitations: 'not-an-array' }),
    });
    const invalidOnly = parseRuntimeEvidence({
      runtime: runtime({ limitations: [123, '', null] }),
    });
    const mixed = parseRuntimeEvidence({
      runtime: runtime({ limitations: ['health only', 123, ''] }),
    });

    expect(missing).toMatchObject({
      limitations: [],
      limitationsState: 'missing',
    });
    expect(empty).toMatchObject({ limitations: [], limitationsState: 'empty' });
    expect(malformed).toMatchObject({
      limitations: [],
      limitationsState: 'malformed',
    });
    expect(malformed.notes).toContain(
      'Limitations metadata was malformed and is not shown.',
    );
    expect(invalidOnly).toMatchObject({
      limitations: [],
      limitationsState: 'malformed',
    });
    expect(invalidOnly.notes).toContain(
      'Limitations metadata contained no valid entries and is not shown.',
    );
    expect(mixed).toMatchObject({
      limitations: ['health only'],
      limitationsState: 'partial',
    });
    expect(mixed.notes).toContain(
      'Some limitation values were invalid and are not shown.',
    );
  });

  it('keeps unavailable health unavailable instead of inferring DOWN', () => {
    const evidence = parseRuntimeEvidence({
      runtime: runtime({ health: { available: false, status: null } }),
    });

    expect(evidence.state).toBe('available');
    expect(evidence.health).toEqual({ kind: 'unavailable', status: null });
  });

  it('flags malformed health and counter values without fabricating results', () => {
    const evidence = parseRuntimeEvidence({
      runtime: runtime({
        health: { available: 'yes', status: 'UP' },
        counters: { valid: 2, invalid: 'many' },
        limitations: 'not-a-list',
      }),
    });

    expect(evidence.health).toEqual({ kind: 'unrecognized', status: null });
    expect(evidence.counters).toEqual([{ name: 'valid', value: 2 }]);
    expect(evidence.countersState).toBe('partial');
    expect(evidence.limitations).toEqual([]);
    expect(evidence.notes).toEqual(
      expect.arrayContaining([
        'Health availability was not a boolean; no health status is inferred.',
        'Some counter values were not finite numbers and are not shown.',
        'Limitations metadata was malformed and is not shown.',
      ]),
    );
  });
});
