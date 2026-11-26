import { describe, expect, it } from 'vitest';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, extname, relative, resolve } from 'node:path';

const SRC = resolve(process.cwd(), 'src');

const ENTRY = resolve(SRC, 'main.jsx');
const PRESENTATION = resolve(SRC, 'views/CandidatePresentation.jsx');
const FIXTURES = resolve(SRC, 'preview/syntheticCandidates.js');

const ALLOWED_GRAPH = [
  PRESENTATION,
  resolve(SRC, 'components/AppShell.jsx'),
  resolve(SRC, 'components/CandidateCard.jsx'),
  resolve(SRC, 'components/CandidateEvidence.jsx'),
  resolve(SRC, 'components/CandidateExcludedSummary.jsx'),
  resolve(SRC, 'styles/fixture.css'),
];

const CANDIDATE_MODULE = /syntheticCandidates|CandidatePresentation|CandidateCard|CandidateEvidence|CandidateExcludedSummary|fixture\.css/i;

const SIDE_EFFECTS = [
  /\bfetch\s*\(/,
  /\bXMLHttpRequest\b/,
  /\bWebSocket\b/,
  /\blocalStorage\b/,
  /\bsessionStorage\b/,
  /\bindexedDB\b/,
  /document\.cookie/,
  /history\.pushState/,
  /history\.replaceState/,
  /\bnew\s+URL\s*\(/,
  /\binnerHTML\b/,
  /dangerouslySetInnerHTML/,
  /\beval\s*\(/,
  /createElement\s*\(/,
  /insertAdjacentHTML\s*\(/,
];

const DECISION_ACTIONS =
  /\b(window\.print|navigator\.clipboard|download|onAccept|onReject|onApprove|onDismiss|onSubmit|onPromote|onExecute|accept|reject|approve|dismiss|submit|promote|execute|decide)\b/i;

const PROHIBITED_STATE =
  /\b(review|reviewed|reviewState|accept|accepted|reject|rejected|approve|approved|decision|S-C|S-D)\b/i;

const PROHIBITED_SEGMENTS = new Set([
  'api',
  'backend',
  'generation',
  'localmodel',
  'evidence',
]);

const DYNAMIC_MARKUP = /<[^>]*\s(?:href|src|style|action)\s*=/i;

const RESOLVE_SUFFIXES = ['', '.js', '.jsx', '.css', '.json', '/index.js', '/index.jsx'];
const IMPORT_PATTERN = /(?<![\w.])import\s+(?:[^'";]*?\bfrom\s+)?(['"])([^'"]+)\1/g;
const EXPORT_FROM_PATTERN = /(?<![\w.])export\s+[^'";]*?\bfrom\s+(['"])([^'"]+)\1/g;

function stripComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
}

function blankStringLiterals(text) {
  return text.replace(/(['"`])(?:\\.|(?!\1)[^\\])*\1/g, '""');
}

function withoutCanonicalNotice(text) {
  const match = text.match(/const NON_ENTAILMENT_NOTICE\s*=\s*'([^']*)';/);
  return match ? text.replace(match[1], '') : text;
}

function staticImports(text) {
  const result = [];
  const source = stripComments(text);
  for (const pattern of [IMPORT_PATTERN, EXPORT_FROM_PATTERN]) {
    pattern.lastIndex = 0;
    let match;
    while ((match = pattern.exec(source)) !== null) result.push(match[2]);
  }
  return result;
}

function resolveRelative(fromFile, specifier) {
  const base = resolve(dirname(fromFile), specifier);
  for (const suffix of RESOLVE_SUFFIXES) {
    const candidate = base + suffix;
    if (existsSync(candidate) && statSync(candidate).isFile()) return candidate;
  }
  throw new Error(`Unresolved production import "${specifier}" from ${fromFile}`);
}

function staticGraph(entry) {
  if (!existsSync(entry)) throw new Error(`Missing production entry: ${entry}`);
  const modules = new Set();
  const bare = new Set();
  const queue = [entry];
  while (queue.length > 0) {
    const file = queue.shift();
    if (modules.has(file)) continue;
    modules.add(file);
    if (extname(file) === '.css') continue;
    for (const specifier of staticImports(read(file))) {
      if (specifier.startsWith('.')) {
        queue.push(resolveRelative(file, specifier));
      } else {
        bare.add(specifier);
      }
    }
  }
  return { modules, bare };
}

function read(file) {
  return readFileSync(file, 'utf8');
}

function isInTestTree(file) {
  const path = relative(SRC, file).split(/[\\/]/);
  return path[0] !== '..' && path.includes('test');
}

function walk(directory) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) files.push(...walk(path));
    else if (/\.(js|jsx)$/.test(entry.name)) files.push(path);
  }
  return files;
}

function assertNoProhibitedSegments(file) {
  const segments = file.split(/[\\/]/);
  for (const segment of segments) {
    expect(PROHIBITED_SEGMENTS.has(segment.toLowerCase())).toBe(false);
  }
  expect(segmentEndsWith(file, 'StatePanels.jsx')).toBe(false);
  expect(segmentEndsWith(file, 'SimulationService')).toBe(false);
  expect(segmentEndsWith(file, 'PolicyService')).toBe(false);
}

function segmentEndsWith(file, suffix) {
  return file.split(/[\\/]/).some((part) => part.endsWith(suffix));
}

describe('candidate presentation static boundary', () => {
  it('starts the production graph at the real frontend entry and excludes test files', () => {
    const { modules } = staticGraph(ENTRY);
    expect(modules.has(ENTRY)).toBe(true);
    expect([...modules].some(isInTestTree)).toBe(false);
  });

  it('resolves relative imports and treats package imports as external', () => {
    const { modules, bare } = staticGraph(ENTRY);
    expect(modules.has(resolve(SRC, 'App.jsx'))).toBe(true);
    expect(modules.has(resolve(SRC, 'components/TwinConsole.jsx'))).toBe(true);
    expect(modules.has(resolve(SRC, 'styles/base.css'))).toBe(true);
    expect(bare.has('react')).toBe(true);
    expect(bare.has('react-dom/client')).toBe(true);
    expect([...bare].some((specifier) => specifier.startsWith('.'))).toBe(false);
  });

  it('fails closed on an unresolved production-relative import', () => {
    expect(() => resolveRelative(ENTRY, './does-not-exist.js')).toThrow(
      /Unresolved production import/,
    );
    expect(() => staticGraph(resolve(SRC, 'does-not-exist.js'))).toThrow(
      /Missing production entry/,
    );
  });

  it('limits the CandidatePresentation static import graph to permitted modules', () => {
    const { modules, bare } = staticGraph(PRESENTATION);
    expect([...modules].sort()).toEqual([...ALLOWED_GRAPH].sort());
    expect([...bare].sort()).toEqual(['react']);
  });

  it('has no transitive static path to API, panel, backend, or generation modules', () => {
    for (const entry of [...staticGraph(PRESENTATION).modules, FIXTURES]) {
      assertNoProhibitedSegments(entry);
    }
  });

  it('keeps candidate fixtures and presentation out of the production entry graph', () => {
    const { modules, bare } = staticGraph(ENTRY);
    for (const file of modules) {
      expect(CANDIDATE_MODULE.test(relative(SRC, file)), `${file}`).toBe(false);
    }
    for (const specifier of bare) {
      expect(CANDIDATE_MODULE.test(specifier), `${specifier}`).toBe(false);
    }
  });

  it('keeps candidate fixtures and presentation out of App.jsx static imports', () => {
    const specifiers = staticImports(read(resolve(SRC, 'App.jsx')));
    for (const specifier of specifiers) {
      expect(CANDIDATE_MODULE.test(specifier)).toBe(false);
    }
  });

  it('guards both candidate dynamic imports with preview availability and DEV checks', () => {
    const app = read(resolve(SRC, 'App.jsx'));
    expect(app).toMatch(/const previewAvailable = canUsePreview\(import\.meta\.env\);/);

    const start = app.indexOf('const openCandidatePreview');
    const end = app.indexOf('const closeCandidatePreview');
    expect(start).toBeGreaterThan(-1);
    expect(end).toBeGreaterThan(start);
    const loader = app.slice(start, end);

    const gate = loader.indexOf('if (!previewAvailable) return;');
    const dev = loader.indexOf('if (!import.meta.env.DEV) return;');
    const fixtures = loader.indexOf("import('./preview/syntheticCandidates.js')");
    const presentation = loader.indexOf(
      "import('./views/CandidatePresentation.jsx')",
    );

    expect(gate).toBeGreaterThan(-1);
    expect(dev).toBeGreaterThan(gate);
    expect(fixtures).toBeGreaterThan(dev);
    expect(presentation).toBeGreaterThan(dev);
  });

  it('contains no network, storage, URL, or markup-injection side effects', () => {
    const files = [...staticGraph(PRESENTATION).modules, FIXTURES];
    for (const file of files) {
      const text = stripComments(read(file));
      for (const pattern of SIDE_EFFECTS) {
        expect(pattern.test(text), `${file} matches ${pattern}`).toBe(false);
      }
      expect(
        DYNAMIC_MARKUP.test(blankStringLiterals(text)),
        `${file} matches dynamic markup`,
      ).toBe(false);
    }
  });

  it('exposes no export, print, copy-as-evidence, or candidate-decision action', () => {
    for (const file of ALLOWED_GRAPH) {
      if (file.endsWith('.css')) continue;
      const text = stripComments(read(file));
      expect(DECISION_ACTIONS.test(text), `${file} matches decision action`).toBe(false);
    }
    const fixtures = stripComments(read(FIXTURES));
    expect(DECISION_ACTIONS.test(fixtures)).toBe(false);
  });

  it('has no prohibited review or acceptance identifiers or S-C/S-D fields', () => {
    for (const file of [...ALLOWED_GRAPH, FIXTURES]) {
      if (file.endsWith('.css')) continue;
      expect(
        PROHIBITED_STATE.test(withoutCanonicalNotice(stripComments(read(file)))),
        `${file}`,
      ).toBe(false);
    }
  });

  it('keeps production source modules free of candidate static imports', () => {
    const candidateGraph = new Set(ALLOWED_GRAPH);
    for (const file of walk(SRC)) {
      if (isInTestTree(file) || candidateGraph.has(file)) continue;
      for (const specifier of staticImports(read(file))) {
        expect(CANDIDATE_MODULE.test(specifier), `${file} imports ${specifier}`).toBe(
          false,
        );
      }
    }
  });
});
