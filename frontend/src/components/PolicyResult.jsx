import {
  DeltaSummary,
  Fact,
  GraphSummary,
  Limitations,
  SettingsSummary,
  text,
} from './SimulationResult.jsx';

function DecisionBadge({ decision }) {
  return (
    <span
      className="badge policy-decision"
      data-decision={text(decision, 'unknown').toLowerCase()}
    >
      {text(decision)}
    </span>
  );
}

function RulesFired({ rules }) {
  if (!Array.isArray(rules) || rules.length === 0) {
    return (
      <div className="state" role="status">
        <h4 className="state__title">No rules fired</h4>
        <p className="state__body">
          The advisory evaluation returned an empty rule list.
        </p>
      </div>
    );
  }

  return (
    <ol className="policy-rules" aria-label="Policy rules in server order">
      {rules.map((rule, index) => (
        <li className="policy-rule" key={`${index}-${rule?.code ?? 'rule'}`}>
          <div className="policy-rule__head">
            <span className="mono">{text(rule?.code)}</span>
            <span className="badge">{text(rule?.outcome)}</span>
          </div>
          <p className="policy-rule__detail">{text(rule?.detail)}</p>
        </li>
      ))}
    </ol>
  );
}

export function PolicyResult({ policy }) {
  return (
    <section
      className="analysis-section policy-result"
      role="region"
      aria-label="Policy evaluation result"
    >
      <div className="analysis-section__head">
        <div>
          <h2 className="analysis-section__title">Policy evaluation result</h2>
          <p className="hint">
            One independent, server-computed advisory evaluation. It is not an
            authorization and does not consume the simulation result displayed
            elsewhere.
          </p>
        </div>
        <span className="badge">POST /policy-evaluations</span>
      </div>

      <div className="simulation-advisory" role="note">
        <strong>{policy?.advisory}</strong>
      </div>

      <dl className="analysis-facts">
        <Fact label="Decision">
          <DecisionBadge decision={policy?.decision} />
        </Fact>
        <Fact label="Policy version">{text(policy?.policyVersion)}</Fact>
      </dl>

      <h3 className="analysis-subtitle">Rules fired</h3>
      <RulesFired rules={policy?.rulesFired} />

      <SettingsSummary settings={policy?.settings} />
      <GraphSummary graph={policy?.simulation?.graph} />
      <DeltaSummary delta={policy?.simulation?.delta} />
      <Limitations limitations={policy?.simulation?.limitations} />

      <p className="analysis-note">{text(policy?.note)}</p>

      <div className="notice" role="note">
        <strong>Bounded simulation summary.</strong> This policy response contains
        only graph counts, delta, and limitations. It does not include before/after
        scopes or server timestamps, and this view does not infer them.
      </div>
    </section>
  );
}
