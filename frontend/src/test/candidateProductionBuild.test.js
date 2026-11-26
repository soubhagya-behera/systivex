// @vitest-environment node
import { describe, expect, it } from 'vitest';
import { spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { basename, extname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  FIXTURE_BANNER,
  SYNTHETIC_REFERENCE_NOTICE,
  loadSyntheticCandidateFixtures,
} from '../preview/syntheticCandidates.js';

const FRONTEND = fileURLToPath(new URL('../../', import.meta.url));
const OUTPUT_PREFIX = '.candidate-boundary-check-';
const OUTPUT = resolve(
  FRONTEND,
  `${OUTPUT_PREFIX}${process.pid}-${Date.now()}-${randomBytes(4).toString('hex')}`,
);
const SCANNABLE = new Set(['.html', '.js', '.css', '.json']);

const FIXTURE_VIEW_STRINGS = [
  'Synthetic candidate presentation',
  'Illustrative synthetic data only',
  'Synthetic fixture candidates',
  'Excluded from this synthetic fixture presentation',
  'No synthetic candidates in this fixture set',
  'Candidates are model-generated and untrusted',
  'no human review is recorded by this surface',
];

function collect(directory) {
  const result = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) result.push(...collect(path));
    else if (SCANNABLE.has(extname(entry.name))) result.push(path);
  }
  return result;
}

function removeAllocatedOutput(allocatedByTest) {
  if (allocatedByTest && existsSync(OUTPUT)) {
    rmSync(OUTPUT, { recursive: true, force: true });
  }
}

function runProductionBuild() {
  const script = `import { build } from 'vite';
await build({
  configFile: ${JSON.stringify(resolve(FRONTEND, 'vite.config.js'))},
  mode: 'production',
  logLevel: 'silent',
  build: {
    outDir: ${JSON.stringify(OUTPUT)},
    emptyOutDir: false,
    sourcemap: false,
  },
});
`;
  return spawnSync(process.execPath, ['--input-type=module', '-e', script], {
    cwd: FRONTEND,
    env: { ...process.env, NODE_ENV: 'production' },
    encoding: 'utf8',
    timeout: 120000,
  });
}

describe('candidate production build exclusion', () => {
  it(
    'emits no candidate fixture data, component chunk, CSS, or entry control in production',
    () => {
      expect(basename(OUTPUT)).toMatch(/^\.candidate-boundary-check-/);
      expect(OUTPUT).not.toBe(resolve(FRONTEND, 'dist'));
      expect(existsSync(OUTPUT)).toBe(false);
      let allocatedByTest = true;
      try {
        const result = runProductionBuild();
        expect(result.error, `vite build could not start: ${result.error}`).toBeUndefined();
        expect(
          result.status,
          `vite build exited ${result.status}: ${result.stderr}`,
        ).toBe(0);
        expect(existsSync(OUTPUT)).toBe(true);

        const assets = collect(OUTPUT);
        expect(assets.length).toBeGreaterThan(0);
        const bundleText = assets
          .map((asset) => readFileSync(asset, 'utf8'))
          .join('\n');

        const sets = loadSyntheticCandidateFixtures();
        const sentinels = [
          sets[0].candidates[0].statement.slice(0, 60),
          sets[0].evidence[0].source,
          sets[1].evidence[0].content.note,
          FIXTURE_BANNER,
          SYNTHETIC_REFERENCE_NOTICE,
          'Synthetic fixture candidate',
          'Synthetic fixture evidence item',
        ];
        for (const sentinel of sentinels) {
          expect(bundleText, `fixture sentinel leaked: ${sentinel}`).not.toContain(
            sentinel,
          );
        }
        for (const leaked of FIXTURE_VIEW_STRINGS) {
          expect(bundleText, `fixture view string leaked: ${leaked}`).not.toContain(
            leaked,
          );
        }
        expect(bundleText).not.toContain('loadSyntheticCandidateFixtures');

        for (const asset of assets) {
          const name = relative(OUTPUT, asset).replace(/\\/g, '/');
          expect(name).not.toMatch(
            /candidatepresentation|candidatecard|candidateevidence|candidateexcludedsummary|syntheticcandidates/i,
          );
        }

        const cssText = assets
          .filter((asset) => extname(asset) === '.css')
          .map((asset) => readFileSync(asset, 'utf8'))
          .join('\n');
        for (const className of [
          '.fixture-banner',
          '.fixture-card',
          '.fixture-synthetic-label',
          '.fixture-excluded',
        ]) {
          expect(cssText).not.toContain(className);
        }

        const htmlText = assets
          .filter((asset) => extname(asset) === '.html')
          .map((asset) => readFileSync(asset, 'utf8'))
          .join('\n');
        expect(htmlText).not.toContain('Open candidate presentation');

        expect(bundleText).not.toContain('./preview/syntheticCandidates.js');
        expect(bundleText).not.toContain('./views/CandidatePresentation.jsx');
      } finally {
        removeAllocatedOutput(allocatedByTest);
      }
    },
    180000,
  );

  it('removes only an allocated partial output and respects the ownership guard', () => {
    mkdirSync(OUTPUT, { recursive: true });
    writeFileSync(resolve(OUTPUT, 'partial.txt'), 'partial output');
    try {
      expect(existsSync(resolve(OUTPUT, 'partial.txt'))).toBe(true);
      removeAllocatedOutput(false);
      expect(existsSync(OUTPUT)).toBe(true);
    } finally {
      removeAllocatedOutput(true);
    }
    expect(existsSync(OUTPUT)).toBe(false);
  });
});
