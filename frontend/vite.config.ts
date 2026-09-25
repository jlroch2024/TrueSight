// Vite builds and serves the website. `npm run dev` starts it at http://localhost:5173.
//
// The proxy sends every request for /api/... to the backend on port 8080, so the website and the backend look like
// one site to the browser, exactly as they will on the live site.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
});
