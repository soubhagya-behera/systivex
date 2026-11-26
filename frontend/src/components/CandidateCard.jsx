export function CandidateCard({ candidate, onSelectReference }) {
  return (
    <article className="fixture-card" aria-label="Synthetic fixture candidate">
      <span className="fixture-synthetic-label">Synthetic fixture candidate</span>
      <p className="fixture-illustrative">
        Illustrative synthetic candidate — not a real model result.
      </p>
      <p className="fixture-statement">{candidate.statement}</p>

      <dl className="fixture-fields">
        <div>
          <dt>Category</dt>
          <dd>{candidate.category}</dd>
        </div>
        <div>
          <dt>Claimed qualification (synthetic)</dt>
          <dd>{candidate.claimedQualification}</dd>
        </div>
        <div>
          <dt>Computed qualification (synthetic)</dt>
          <dd>{candidate.computedQualification}</dd>
        </div>
      </dl>

      <p className="fixture-verdict">
        Synthetic deterministic validation verdict: {candidate.deterministicVerdict}
      </p>
      <p className="fixture-note">
        Illustrative label only; it does not establish semantic support.
      </p>

      <h3 className="fixture-subhead">Synthetic presentation references</h3>
      <ul className="fixture-inline-list">
        {candidate.evidenceReferences.map((reference) => (
          <li key={reference}>
            <button
              type="button"
              className="fixture-reference-button"
              onClick={() => onSelectReference?.(reference)}
            >
              Synthetic presentation reference {reference}
            </button>
          </li>
        ))}
      </ul>

      <h3 className="fixture-subhead">Limitations (synthetic)</h3>
      <ul className="fixture-inline-list">
        {candidate.limitations.map((limitation, index) => (
          <li key={index}>{limitation}</li>
        ))}
      </ul>
    </article>
  );
}
