import { prettyJson } from '../lib/format.js';

export function MetadataView({ metadata }) {
  const empty =
    metadata === null ||
    metadata === undefined ||
    (typeof metadata === 'object' && Object.keys(metadata).length === 0);

  if (empty) {
    return <p className="hint">No metadata recorded for this entity.</p>;
  }
  return (
    <details className="metadata-disclosure">
      <summary>Raw metadata (JSON)</summary>
      <pre className="meta-block" aria-label="Entity metadata (JSON)">
        {prettyJson(metadata)}
      </pre>
    </details>
  );
}
