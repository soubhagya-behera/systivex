import { describe, expect, it } from 'vitest';
import {
  FIXTURE_BANNER,
  SYNTHETIC_REFERENCE_NOTICE,
  loadSyntheticCandidateFixtures,
} from '../preview/syntheticCandidates.js';

const CANDIDATE_KEYS = [
  'statement',
  'category',
  'evidenceReferences',
  'claimedQualification',
  'computedQualification',
  'limitations',
  'deterministicVerdict',
];

const EVIDENCE_KEYS = [
  'ref',
  'category',
  'source',
  'scope',
  'sourceVersion',
  'timestamps',
  'content',
  'limitations',
  'qualification',
  'status',
  'includedCount',
  'omittedCount',
];

const CATEGORIES = [
  'OBSERVED_FACT',
  'DETERMINISTIC_FINDING',
  'HYPOTHETICAL_RESULT',
  'GOVERNANCE_RECORD',
  'AUDIT_EVIDENCE',
];

const QUALIFICATIONS = [
  'CURRENT',
  'STALE',
  'UNCERTAIN',
  'TRUNCATED',
  'MISSING',
  'HYPOTHETICAL',
  'HISTORICAL',
  'ADVISORY',
];

const SOURCE_STATUSES = ['AVAILABLE', 'UNAVAILABLE', 'OMITTED', 'TRUNCATED'];

const PROHIBITED_STATE =
  /\b(review|accept|reject|decision|S-C|S-D|approved|verified|confirmed|correct|safe)\b/i;
const PROHIBITED_IDENTITY_KEYS = /run|provider|model|packet|prompt|duration|generation|review|accept|reject/i;
const PROHIBITED_METADATA = /promptBytes|durationMillis|runId|generationRun|packetRef|modelId|providerId|modelName|providerName|"model"\s*:|"provider"\s*:/i;

describe('synthetic candidate fixtures', () => {
  it('are locally defined with the approved candidate and evidence shapes', () => {
    const sets = loadSyntheticCandidateFixtures();
    expect(sets.length).toBeGreaterThan(1);

    for (const set of sets) {
      expect(Object.keys(set).sort()).toEqual(
        [
          'id',
          'label',
          'banner',
          'referenceNotice',
          'candidates',
          'evidence',
          'excludedSummary',
        ].sort(),
      );
      expect(set.banner).toBe(FIXTURE_BANNER);
      expect(set.referenceNotice).toBe(SYNTHETIC_REFERENCE_NOTICE);
      expect(Array.isArray(set.candidates)).toBe(true);
      expect(Array.isArray(set.evidence)).toBe(true);

      for (const candidate of set.candidates) {
        expect(Object.keys(candidate).sort()).toEqual([...CANDIDATE_KEYS].sort());
        expect(CATEGORIES).toContain(candidate.category);
        expect(QUALIFICATIONS).toContain(candidate.claimedQualification);
        expect(QUALIFICATIONS).toContain(candidate.computedQualification);
        expect(Array.isArray(candidate.evidenceReferences)).toBe(true);
        expect(Array.isArray(candidate.limitations)).toBe(true);
      }

      for (const item of set.evidence) {
        expect(Object.keys(item).sort()).toEqual([...EVIDENCE_KEYS].sort());
        expect(CATEGORIES).toContain(item.category);
        expect(QUALIFICATIONS).toContain(item.qualification);
        expect(SOURCE_STATUSES).toContain(item.status);
        expect(Array.isArray(item.timestamps)).toBe(true);
        expect(Array.isArray(item.limitations)).toBe(true);
        for (const timestamp of item.timestamps) {
          expect(Object.keys(timestamp).sort()).toEqual(['name', 'value']);
        }
      }
    }
  });

  it('represent only S-A candidates and S-B deterministic verdicts', () => {
    const sets = loadSyntheticCandidateFixtures();
    const illustrative = sets.find((set) => set.id === 'illustrative');
    expect(illustrative.candidates).toHaveLength(1);
    expect(illustrative.evidence).toHaveLength(1);
    expect(illustrative.candidates[0].deterministicVerdict).toBe('PASSED');

    for (const set of sets) {
      for (const candidate of set.candidates) {
        expect(candidate.deterministicVerdict).toBe('PASSED');
      }
      expect(JSON.stringify(set)).not.toMatch(PROHIBITED_STATE);
    }
  });

  it('marks every P/E-style reference as a synthetic presentation token', () => {
    for (const set of loadSyntheticCandidateFixtures()) {
      expect(set.referenceNotice).toMatch(/synthetic/i);
      expect(set.referenceNotice).toMatch(/not a packet identity/i);
      expect(set.referenceNotice).toMatch(/not durable evidence/i);
      for (const candidate of set.candidates) {
        for (const reference of candidate.evidenceReferences) {
          expect(reference).toMatch(/^E[1-9]\d*$/);
        }
      }
      for (const item of set.evidence) {
        expect(item.ref).toMatch(/^E[1-9]\d*$/);
      }
    }
  });

  it('exposes no generation-run identity or durable evidence identity', () => {
    for (const set of loadSyntheticCandidateFixtures()) {
      for (const candidate of set.candidates) {
        expect(candidate).not.toHaveProperty('id');
        expect(Object.keys(candidate).some((key) => PROHIBITED_IDENTITY_KEYS.test(key))).toBe(
          false,
        );
      }
      for (const item of set.evidence) {
        expect(item).not.toHaveProperty('id');
        expect(Object.keys(item).some((key) => PROHIBITED_IDENTITY_KEYS.test(key))).toBe(
          false,
        );
      }
      expect(JSON.stringify(set)).not.toMatch(/\bP[1-9]\d*\b/);
    }
  });

  it('keeps excluded summaries limited to counts and fixed explanatory text', () => {
    for (const set of loadSyntheticCandidateFixtures()) {
      const summary = set.excludedSummary;
      expect(Object.keys(summary).sort()).toEqual(
        ['total', 'byFailureClass', 'notice'].sort(),
      );
      expect(Number.isInteger(summary.total)).toBe(true);
      expect(Array.isArray(summary.byFailureClass)).toBe(true);
      const summaryText = JSON.stringify(summary);
      for (const entry of summary.byFailureClass) {
        expect(Object.keys(entry).sort()).toEqual(
          ['failureClass', 'count', 'explanation'].sort(),
        );
        expect(Number.isInteger(entry.count)).toBe(true);
      }
      for (const candidate of set.candidates) {
        expect(summaryText).not.toContain(candidate.statement);
      }
      for (const item of set.evidence) {
        expect(summaryText).not.toContain(item.ref);
        expect(summaryText).not.toContain(JSON.stringify(item.content));
        for (const limitation of item.limitations) {
          expect(summaryText).not.toContain(limitation);
        }
      }
    }
  });

  it('does not let a caller mutate canonical fixture records', () => {
    const before = loadSyntheticCandidateFixtures();
    const originalStatement = before[0].candidates[0].statement;
    const originalContent = before[0].evidence[0].content.note;
    const originalTimestamp = before[0].evidence[0].timestamps[0].value;
    const originalTotal = before[0].excludedSummary.total;
    const originalLabel = before[0].label;

    const altered = loadSyntheticCandidateFixtures();
    expect(altered).not.toBe(before);
    expect(altered[0]).not.toBe(before[0]);
    altered[0].candidates[0].statement = 'MUTATED';
    altered[0].evidence[0].content.note = 'MUTATED';
    altered[0].evidence[0].timestamps[0].value = 'MUTATED';
    altered[0].excludedSummary.total = -1;
    altered[0].label = 'MUTATED';

    const after = loadSyntheticCandidateFixtures();
    expect(after[0].candidates[0].statement).toBe(originalStatement);
    expect(after[0].evidence[0].content.note).toBe(originalContent);
    expect(after[0].evidence[0].timestamps[0].value).toBe(originalTimestamp);
    expect(after[0].excludedSummary.total).toBe(originalTotal);
    expect(after[0].label).toBe(originalLabel);
  });

  it('contains no provider, model, or generation-run metadata', () => {
    const serialized = JSON.stringify(loadSyntheticCandidateFixtures());
    expect(serialized).not.toMatch(PROHIBITED_METADATA);
    expect(serialized).not.toMatch(/"review|"accepted|"rejected|"decision/i);
  });
});
