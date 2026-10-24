import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The console is local-development-only (DECISIONS.md D33):
//  - it binds to 127.0.0.1 and is never exposed on 0.0.0.0;
//  - browser calls to /api are proxied to the local control plane at
//    http://127.0.0.1:8080, so no backend CORS rules are needed;
//  - no production/static hosting is configured (deferred by D33).
const CONTROL_PLANE = 'http://127.0.0.1:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: CONTROL_PLANE,
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
});
