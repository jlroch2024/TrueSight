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
    // The website's own folder, plus the shared sample data the website tests read (e.g. the saved Google News
    // answer, as text). Nothing else above the website is reachable, so .env never is.
    fs: {
      allow: ['.', '../docs/examples'],
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    // Vitest empties CSS files; this lets tests read the stylesheets as text (the look's checks in design.test.ts, the
    // Select test). Imported styles stay empty.
    css: { include: [/(styles|livingGraph)\.css\?raw/] },
    // `npm run coverage` fails below 50% of lines (the course's minimum); the report is coverage/index.html.
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx'],
      reporter: ['text-summary', 'html'],
      thresholds: { lines: 50 },
    },
  },
});
