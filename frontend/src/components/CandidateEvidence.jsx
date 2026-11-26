export function CandidateEvidence({ item, headingRef }) {
  return (
    <article className="fixture-evidence" aria-label="Synthetic fixture evidence">
      <h3 className="fixture-evidence-heading" tabIndex={-1} ref={headingRef}>
        Synthetic fixture evidence {item.ref}
      </h3>
      <span className="fixture-synthetic-label">Synthetic fixture evidence item</span>
      <p className="fixture-ref-description">
        Synthetic presentation reference {item.ref} — not a packet identity or durable
        evidence id.
      </p>

      <dl className="fixture-fields">
        <div>
          <dt>Category</dt>
          <dd>{item.category}</dd>
        </div>
        <div>
          <dt>Source (synthetic)</dt>
          <dd>{item.source}</dd>
        </div>
        <div>
          <dt>Scope</dt>
          <dd>{item.scope}</dd>
        </div>
        <div>
          <dt>Source version (synthetic)</dt>
          <dd>{item.sourceVersion}</dd>
        </div>
        <div>
          <dt>Qualification (synthetic)</dt>
          <dd>{item.qualification}</dd>
        </div>
        <div>
          <dt>Status (synthetic)</dt>
          <dd>{item.status}</dd>
        </div>
        <div>
          <dt>Included count</dt>
          <dd>{item.includedCount}</dd>
        </div>
        <div>
          <dt>Omitted count</dt>
          <dd>{item.omittedCount}</dd>
        </div>
      </dl>

      <h4 className="fixture-subhead">Timestamps (synthetic)</h4>
      <ul className="fixture-inline-list">
        {item.timestamps.map((timestamp, index) => (
          <li key={index}>
            {timestamp.name}: {timestamp.value}
          </li>
        ))}
      </ul>

      <h4 className="fixture-subhead">Content (synthetic fixture text)</h4>
      <p className="fixture-content">
        <code>{JSON.stringify(item.content)}</code>
      </p>

      <h4 className="fixture-subhead">Limitations (synthetic)</h4>
      <ul className="fixture-inline-list">
        {item.limitations.map((limitation, index) => (
          <li key={index}>{limitation}</li>
        ))}
      </ul>
    </article>
  );
}
