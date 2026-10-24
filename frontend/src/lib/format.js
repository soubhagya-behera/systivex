/** Presentation helpers for twin records. */

export function titleCaseType(type) {
  if (!type) return 'Unknown';
  return String(type)
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function formatInstant(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toISOString().replace('T', ' ').replace('.000Z', ' UTC').replace('Z', ' UTC');
}

export function prettyJson(value) {
  if (value === null || value === undefined) return '—';
  try {
    const text = JSON.stringify(value, null, 2);
    return text === undefined ? '—' : text;
  } catch {
    return '—';
  }
}

export function shortId(id) {
  const text = String(id ?? '');
  return text.length > 14 ? `${text.slice(0, 8)}…${text.slice(-4)}` : text;
}

export function pluralize(count, singular, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}
