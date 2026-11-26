export function CandidateExcludedSummary({ summary }) {
  return (
    <section
      className="fixture-excluded"
      role="region"
      aria-label="Excluded presentation summary"
    >
      <h2 className="fixture-subhead">Excluded from this synthetic fixture presentation</h2>
      <p>
        Fabricated aggregate count: <strong>{summary.total}</strong>
      </p>
      <ul className="fixture-inline-list">
        {summary.byFailureClass.map((entry) => (
          <li key={entry.failureClass}>
            <span className="fixture-failure-class">{entry.failureClass}</span>: {entry.count} —{' '}
            <span>{entry.explanation}</span>
          </li>
        ))}
      </ul>
      <p className="fixture-note">{summary.notice}</p>
    </section>
  );
}
