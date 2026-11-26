import { useEffect, useRef, useState } from 'react';
import { AppShell } from '../components/AppShell.jsx';
import { CandidateCard } from '../components/CandidateCard.jsx';
import { CandidateEvidence } from '../components/CandidateEvidence.jsx';
import { CandidateExcludedSummary } from '../components/CandidateExcludedSummary.jsx';
import '../styles/fixture.css';

const NON_ENTAILMENT_NOTICE =
  'Candidates are model-generated and untrusted. Deterministic validation checks structure, provenance, category, qualification, limitation inheritance, and wording only. It does not establish that the cited evidence supports the statement. Nothing on this screen is approved, authorized, or executed, and no human review is recorded by this surface.';

export function CandidatePresentation({ fixtureSets = [], onExit }) {
  const [selectedSetIndex, setSelectedSetIndex] = useState(0);
  const [selectedRef, setSelectedRef] = useState(null);
  const [announcement, setAnnouncement] = useState('');
  const evidenceNodes = useRef({});
  const headingRef = useRef(null);

  const current = fixtureSets[selectedSetIndex] ?? null;

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  useEffect(() => {
    if (!selectedRef) return;
    evidenceNodes.current[selectedRef]?.focus();
  }, [selectedRef, selectedSetIndex]);

  function selectSet(index) {
    setSelectedSetIndex(index);
    setSelectedRef(null);
    evidenceNodes.current = {};
    setAnnouncement(`Synthetic fixture set shown: ${fixtureSets[index]?.label ?? ''}`);
  }

  function focusReference(ref) {
    setSelectedRef(ref);
    setAnnouncement(`Synthetic presentation reference ${ref} focused in the fixture evidence.`);
  }

  function exit() {
    setSelectedRef(null);
    setAnnouncement('');
    onExit?.();
  }

  if (!current) {
    return (
      <main className="content fixture-root" id="main-content">
        <p className="fixture-empty">No synthetic fixture sets were supplied.</p>
        <button type="button" className="fixture-exit" onClick={exit}>
          Exit candidate preview
        </button>
      </main>
    );
  }

  const navItems = fixtureSets.map((set) => ({
    key: set.id,
    label: set.label,
    icon: '◇',
  }));

  return (
    <AppShell
      navItems={navItems}
      activeKey={current.id}
      onNavigate={(key) =>
        selectSet(fixtureSets.findIndex((set) => set.id === key))
      }
      statusSlot={
        <span className="fixture-status">Synthetic candidate preview</span>
      }
      banner={
        <div className="fixture-banner" role="note">
          {current.banner}
        </div>
      }
    >
      <section className="fixture-notice" role="note" aria-label="Non-entailment notice">
        {NON_ENTAILMENT_NOTICE}
      </section>
      <div className="fixture-announcement" role="status" aria-live="polite">
        {announcement}
      </div>
      <div className="fixture-toolbar">
        <button type="button" className="fixture-exit" onClick={exit}>
          Exit candidate preview
        </button>
      </div>

      <h1 className="fixture-title" tabIndex={-1} ref={headingRef}>
        Synthetic candidate presentation
      </h1>
      <p className="fixture-reference-notice">{current.referenceNotice}</p>

      <h2 className="fixture-subhead">Synthetic fixture candidates</h2>
      {current.candidates.length === 0 ? (
        <p className="fixture-empty" role="status">
          No synthetic candidates in this fixture set. Nothing has been evaluated.
        </p>
      ) : (
        <ul className="fixture-list">
          {current.candidates.map((candidate, index) => (
            <li key={`${current.id}-candidate-${index}`}>
              <CandidateCard candidate={candidate} onSelectReference={focusReference} />
            </li>
          ))}
        </ul>
      )}

      <h2 className="fixture-subhead">Synthetic fixture evidence</h2>
      <ul className="fixture-list">
        {current.evidence.map((item, index) => (
          <li key={`${current.id}-evidence-${index}`}>
            <CandidateEvidence
              item={item}
              headingRef={(node) => {
                evidenceNodes.current[item.ref] = node;
              }}
            />
          </li>
        ))}
      </ul>

      <CandidateExcludedSummary summary={current.excludedSummary} />
    </AppShell>
  );
}
