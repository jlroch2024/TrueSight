// Tests the whole website shell: the login guard, no sidebar, and "/" opening the first portfolio, with a fake backend.
// This is the example for page tests: draw the page inside a router, fake the backend, check what the user sees.
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { tokenStore } from './api/client';

// A fake backend that answers by address, so a test can follow the user from page to page.
function fakeBackend(answers: Record<string, unknown>) {
  return vi.fn((url: string) => {
    const path = url.replace(/^\/api/, '').split('?')[0]!;
    const body = path in answers ? answers[path] : [];
    return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
  });
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App', () => {
  it('sends somebody who is not logged in to the Log In page', () => {
    vi.stubGlobal('fetch', vi.fn());

    renderAt('/');

    expect(screen.getByRole('heading', { name: 'Log In' })).toBeInTheDocument();
  });

  it('opens the first portfolio once logged in, on its workspace, with no Home page and no sidebar', async () => {
    tokenStore.set('a-token');
    vi.stubGlobal('fetch', fakeBackend({
      '/portfolios': [{ id: 7, name: 'Consumer Goods', createdAt: '2026-10-01T00:00:00Z' }],
      '/auth/me': { email: 'pm@example.com', role: 'PORTFOLIO_MANAGER' },
    }));

    renderAt('/');

    // Portfolios are switched and managed from the workspace's portfolio menu.
    expect(await screen.findByRole('button', { name: 'Portfolio: Consumer Goods' })).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'Portfolio' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Home' })).not.toBeInTheDocument();
  });

  it("shows the backend's problem when it is not reachable", async () => {
    tokenStore.set('a-token');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    renderAt('/');

    expect(await screen.findByText('Cannot reach the server. Is the backend running?')).toBeInTheDocument();
  });

  it('shows Page Not Found for an unknown address', () => {
    tokenStore.set('a-token');
    vi.stubGlobal('fetch', vi.fn());

    renderAt('/no-such-page');

    expect(screen.getByRole('heading', { name: 'Page Not Found' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open My Portfolios' })).toBeInTheDocument();
  });
});
