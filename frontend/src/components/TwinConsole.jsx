import { useCallback, useMemo, useState } from 'react';
import { AppShell } from '../components/AppShell.jsx';
import { ErrorPanel, LoadingPanel } from '../components/StatePanels.jsx';
import { useAsyncResource } from '../hooks/useAsyncResource.js';
import { createApiClient } from '../api/client.js';
import { createTwinApi } from '../api/twin.js';
import { countBy } from '../lib/filter.js';
import { Overview } from '../views/Overview.jsx';
import { Entities } from '../views/Entities.jsx';
import { Relationships } from '../views/Relationships.jsx';
import { GraphView } from '../views/GraphView.jsx';

const NAV = [
  { key: 'overview', label: 'Overview', icon: '◎' },
  { key: 'entities', label: 'Entities', icon: '▣' },
  { key: 'relationships', label: 'Relationships', icon: '⇄' },
  { key: 'graph', label: 'Graph', icon: '◈' },
];

export function TwinConsole({ token, onDisconnect }) {
  const [view, setView] = useState('overview');
  const [selectedId, setSelectedId] = useState(null);

  const twinApi = useMemo(() => {
    const client = createApiClient({ getToken: () => token });
    return createTwinApi(client);
  }, [token]);

  const entitiesRes = useAsyncResource(() => twinApi.listEntities(), [twinApi]);
  const relsRes = useAsyncResource(() => twinApi.listRelationships(), [twinApi]);

  const entities = useMemo(() => entitiesRes.data ?? [], [entitiesRes.data]);
  const relationships = useMemo(() => relsRes.data ?? [], [relsRes.data]);

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

  const selectEntity = useCallback((id) => {
    setSelectedId(id);
    setView('entities');
  }, []);

  const retry = useCallback(() => {
    entitiesRes.reload();
    relsRes.reload();
  }, [entitiesRes, relsRes]);

  const navItems = useMemo(
    () =>
      NAV.map((item) => {
        if (item.key === 'entities') return { ...item, count: entities.length };
        if (item.key === 'relationships') return { ...item, count: relationships.length };
        return item;
      }),
    [entities.length, relationships.length],
  );

  const statusSlot = (
    <div className="token-chip">
      <span className="status-dot" aria-hidden="true" />
      <span>Connected</span>
      <button type="button" className="btn" onClick={onDisconnect}>
        Disconnect
      </button>
    </div>
  );

  const error = entitiesRes.error ?? relsRes.error ?? null;
  const loading =
    !error && (entitiesRes.status === 'loading' || relsRes.status === 'loading');

  return (
    <AppShell
      navItems={navItems}
      activeKey={view}
      onNavigate={setView}
      statusSlot={statusSlot}
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
        />
      ) : view === 'entities' ? (
        <Entities
          entities={entities}
          relationships={relationships}
          selectedId={selectedId}
          onSelectEntity={setSelectedId}
          entityTypes={entityTypes}
        />
      ) : view === 'relationships' ? (
        <Relationships
          entities={entities}
          relationships={relationships}
          entitiesById={entitiesById}
          relTypes={relTypes}
          onSelectEntity={selectEntity}
        />
      ) : (
        <GraphView
          entities={entities}
          relationships={relationships}
          selectedId={selectedId}
          onSelectEntity={selectEntity}
        />
      )}
    </AppShell>
  );
}
