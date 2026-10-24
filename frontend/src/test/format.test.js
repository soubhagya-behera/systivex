import { describe, expect, it } from 'vitest';
import { formatAge } from '../lib/format.js';

describe('formatAge', () => {
  const base = Date.parse('2026-10-14T09:50:00Z');

  it('describes elapsed time from the supplied reference', () => {
    expect(formatAge(base, base + 5_000)).toBe('just now');
    expect(formatAge(base, base + 30_000)).toBe('30s ago');
    expect(formatAge(base, base + 120_000)).toBe('2m ago');
    expect(formatAge(base, base + 7_200_000)).toBe('2h ago');
  });

  it('does not present a future timestamp as an elapsed age', () => {
    expect(formatAge(base, base - 2_000)).toBe('timestamp in the future');
    expect(formatAge('not-a-timestamp', base)).toBe('unknown age');
  });
});
