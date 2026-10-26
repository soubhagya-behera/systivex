import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { backendProxyTarget, normalizeBackendPort } from './devProxy.js';

// The console is local-development-only (DECISIONS.md D33):
//  - it binds to 127.0.0.1 and is never exposed on 0.0.0.0;
//  - browser calls to /api are proxied to the local control plane on the
//    loopback host, defaulting to port 8080 (see DEVELOPMENT.md);
//  - SYSTIVEX_BACKEND_PORT may select a numeric loopback port when 8080 is
//    occupied; host, scheme, and path remain fixed;
//  - no production/static hosting is configured (deferred by D33).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const rawPort = process.env.SYSTIVEX_BACKEND_PORT ?? env.SYSTIVEX_BACKEND_PORT;
  const backendPort = normalizeBackendPort(rawPort);
  const controlPlane = backendProxyTarget(backendPort);

  return {
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: controlPlane,
          changeOrigin: false,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test/setup.js',
      css: false,
      restoreMocks: true,
    },
  };
});
