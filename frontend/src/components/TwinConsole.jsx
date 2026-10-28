import { useCallback, useMemo, useState } from 'react';
import { AppShell } from '../components/AppShell.jsx';
import { ErrorPanel, LoadingPanel } from '../components/StatePanels.jsx';
import { useAsyncResource } from '../hooks/useAsyncResource.js';
import { useNow } from '../hooks/useNow.js';
import { createAnalysisApi } from '../api/analysis.js';
import { createApiClient } from '../api/client.js';
import { ErrorKind } from '../api/errors.js';
import { createTwinApi } from '../api/twin.js';
import { countBy } from '../lib/filter.js';
import { formatAge, formatInstant } from '../lib/format.js';
import { Overview } from '../views/Overview.jsx';
import { Entities } from '../views/Entities.jsx';
import { Relationships } from '../views/Relationships.jsx';
import { GraphView } from '../views/GraphView.jsx';
import { ImpactRisk } from '../views/ImpactRisk.jsx';

const NAV = [
  { key: 'overview', label: 'Overview', icon: '◎' },
  { key: 'entities', label: 'Entities', icon: '▣' },
  { key: 'relationships', label: 'Relationships', icon: '⇄' },
  { key: 'graph', label: 'Graph', icon: '◈' },
];

const ANALYSIS_NAV = { key: 'impact-risk', label: 'Impact & Risk', icon: '⚠' };

const ACCESS_STATES = {
  supplied: {
    label: 'Token supplied',
    tone: 'pending',
    detail: 'Waiting for a successful protected read.',
  },
  confirmed: {
    label: 'Access confirmed',
    tone: 'confirmed',
    detail:
      'A protected read succeeded. Identity and full permissions are not verified.',
  },
  rejected: {
    label: 'Access rejected',
    tone: 'rejected',
    detail: 'The control plane rejected the read with HTTP 401 or 403.',
  },
  unavailable: {
    label: 'Connection unavailable',
    tone: 'unavailable',
    detail: 'The control plane could not be reached or returned a server error.',
  },
  unconfirmed: {
    label: 'Access unconfirmed',
    tone: 'pending',
    detail: 'The request failed before access could be confirmed.',
  },
};

const PREVIEW_ACCESS = {
  label: 'Local preview',
  tone: 'preview',
  detail:
    'Synthetic sample data only. No backend request or authenticated session is connected.',
};

function accessState(error, status) {
  if (error) {
    if (error.kind === ErrorKind.UNAUTHORIZED || error.kind === ErrorKind.FORBIDDEN) {
      return ACCESS_STATES.rejected;
    }
    if (error.kind === ErrorKind.NETWORK || error.kind === ErrorKind.SERVER) {
      return ACCESS_STATES.unavailable;
    }
    return ACCESS_STATES.unconfirmed;
  }
  if (status === 'success') {
    return ACCESS_STATES.confirmed;
  }
  return ACCESS_STATES.supplied;
}

export function TwinConsole({ token, onDisconnect, preview = false, previewData = null }) {
  const [view, setView] = useState('overview');
  const [selectedId, setSelectedId] = useState(null);
  const [focusDetail, setFocusDetail] = useState(false);
  const now = useNow();

  const { twinApi, analysisApi } = useMemo(() => {
    if (preview) return { twinApi: null, analysisApi: null };
    const client = createApiClient({ getToken: () => token });
    return {
      twinApi: createTwinApi(client),
      analysisApi: createAnalysisApi(client),
    };
  }, [token, preview]);

  const { data, error, status, reload } = useAsyncResource(async () => {
    if (preview) {
      return {
        entities: previewData?.entities ?? [],
        relationships: previewData?.relationships ?? [],
        snapshotLoadedAt: previewData?.snapshotLoadedAt ?? Date.now(),
      };
    }
    const [entities, relationships] = await Promise.all([
      twinApi.listEntities(),
      twinApi.listRelationships(),
    ]);
    return { entities, relationships, snapshotLoadedAt: Date.now() };
  }, [twinApi, preview, previewData]);

  const entities = useMemo(() => data?.entities ?? [], [data]);
  const relationships = useMemo(() => data?.relationships ?? [], [data]);

  const entitiesById = useMemo(
    () => new Map(entities.map((entity) => [entity.id, entity])),
    [entities],
  );

  const entityTypes = useMemo(() => {
    const present = countBy(entities, 'type').map((row) => row.label);
    return present.length > 0 ? present : [];
  }, [entities]);

  const relTypes = useMemo(() => countBy(relationships, 'type').map((row) => row.label), [
    relationships,
  ]);

  const selectEntity = useCallback((id, options = {}) => {
    setSelectedId(id);
    setView('entities');
    setFocusDetail(options.focusDetail === true);
  }, []);

  const detailFocused = useCallback(() => setFocusDetail(false), []);

  const retry = useCallback(() => {
    reload();
  }, [reload]);

  const navItems = useMemo(
    () =>
      (preview ? NAV : [...NAV, ANALYSIS_NAV]).map((item) => {
        if (item.key === 'entities') return { ...item, count: entities.length };
        if (item.key === 'relationships') return { ...item, count: relationships.length };
        return item;
      }),
    [preview, entities.length, relationships.length],
  );

  const loading = !error && status !== 'success';
  const access = preview ? PREVIEW_ACCESS : accessState(error, status);
  const snapshotLoadedAt = status === 'success' ? data?.snapshotLoadedAt ?? null : null;

  const snapshotText = preview ? (
    <span>Synthetic preview snapshot · not live</span>
  ) : snapshotLoadedAt ? (
    <>
      <span>Snapshot loaded</span>{' '}
      <time dateTime={new Date(snapshotLoadedAt).toISOString()}>
        {formatInstant(snapshotLoadedAt)}
      </time>
      <span className="snapshot-age">
        {' '}
        · {formatAge(snapshotLoadedAt, Math.max(now, snapshotLoadedAt))}
      </span>
    </>
  ) : (
    <span>{loading ? 'Loading snapshot…' : 'No snapshot loaded'}</span>
  );

  const statusSlot = (
    <div className="header-status">
      <div className="snapshot-chip">{snapshotText}</div>
      <div className={`token-chip token-chip--${access.tone}`}>
        <span
          className={`status-dot status-dot--${access.tone}`}
          aria-hidden="true"
        />
        <span
          role="status"
          aria-live="polite"
          aria-label={`${access.label}. ${access.detail}`}
        >
          {access.label}
        </span>
        <button type="button" className="btn" onClick={onDisconnect}>
          {preview ? 'Exit preview' : 'Disconnect'}
        </button>
      </div>
    </div>
  );

  return (
    <AppShell
      navItems={navItems}
      activeKey={view}
      onNavigate={setView}
      statusSlot={statusSlot}
      banner={
        preview ? (
          <div className="preview-banner" role="note">
            {previewData?.banner}
          </div>
        ) : null
      }
    >
      {error ? (
        <ErrorPanel error={error} onRetry={retry} />
      ) : loading ? (
        <LoadingPanel label="Loading System Twin" />
      ) : view === 'overview' ? (
        <Overview
          entities={entities}
          relationships={relationships}
          onSelectEntity={selectEntity}
          preview={preview}
        />
      ) : view === 'entities' ? (
        <Entities
          entities={entities}
          relationships={relationships}
          selectedId={selectedId}
          onSelectEntity={setSelectedId}
          entityTypes={entityTypes}
          focusDetail={focusDetail}
          onDetailFocused={detailFocused}
          preview={preview}
        />
      ) : view === 'relationships' ? (
        <Relationships
          entities={entities}
          relationships={relationships}
          entitiesById={entitiesById}
          relTypes={relTypes}
          onSelectEntity={selectEntity}
          preview={preview}
        />
      ) : view === 'impact-risk' && !preview ? (
        <ImpactRisk
          entities={entities}
          entityTypes={entityTypes}
          analysisApi={analysisApi}
        />
      ) : (
        <GraphView
          entities={entities}
          relationships={relationships}
          selectedId={selectedId}
          onSelectEntity={selectEntity}
          preview={preview}
        />
      )}
    </AppShell>
  );
}
