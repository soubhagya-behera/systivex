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

export function formatAge(value, now = Date.now()) {
  if (value === null || value === undefined) return 'unknown age';
  const date = value instanceof Date ? value : new Date(value);
  const time = date.getTime();
  if (Number.isNaN(time)) return 'unknown age';

  const elapsed = now - time;
  if (elapsed < -1000) return 'timestamp in the future';
  if (elapsed < 10000) return 'just now';

  const seconds = Math.floor(elapsed / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
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
