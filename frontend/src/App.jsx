import { useCallback, useState } from 'react';
import { TokenProvider, useToken } from './auth/TokenContext.jsx';
import { TokenGate } from './components/TokenGate.jsx';
import { TwinConsole } from './components/TwinConsole.jsx';
import { canUsePreview } from './preview/localPreview.js';

function Connector() {
  const { token, setToken, clear, hasToken } = useToken();
  const [previewData, setPreviewData] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const previewAvailable = canUsePreview(import.meta.env);

  const openPreview = useCallback(async () => {
    if (!previewAvailable) return;
    setPreviewLoading(true);
    try {
      // Vite removes this development-only branch from production builds.
      if (!import.meta.env.DEV) return;
      const { loadSyntheticPreview } = await import('./preview/syntheticTwin.js');
      setPreviewData(loadSyntheticPreview());
    } finally {
      setPreviewLoading(false);
    }
  }, [previewAvailable]);

  if (previewData && previewAvailable) {
    return (
      <TwinConsole
        preview
        previewData={previewData}
        onDisconnect={() => setPreviewData(null)}
      />
    );
  }

  if (!hasToken) {
    return (
      <main className="content" id="main-content">
        <TokenGate
          onSubmit={setToken}
          onPreview={previewAvailable ? openPreview : undefined}
          previewLoading={previewLoading}
        />
      </main>
    );
  }
  return <TwinConsole token={token} onDisconnect={clear} />;
}

export default function App() {
  return (
    <TokenProvider>
      <Connector />
    </TokenProvider>
  );
}
