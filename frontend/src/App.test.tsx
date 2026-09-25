// Tests the whole website shell: the sidebar, and the home page talking to a fake backend.
// This is the example for page tests: draw the page inside a router, fake the backend, check what the user sees.
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App', () => {
  it('shows the sidebar and that the backend is running', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'UP' }))));

    renderAt('/');

    expect(screen.getByRole('navigation', { name: 'Main' })).toHaveTextContent('Home');
    expect(await screen.findByText('Running')).toBeInTheDocument();
  });

  it("shows the backend's problem when it is not reachable", async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    renderAt('/');

    expect(await screen.findByText('Cannot reach the server. Is the backend running?')).toBeInTheDocument();
  });

  it('shows Page Not Found for an unknown address', () => {
    vi.stubGlobal('fetch', vi.fn());

    renderAt('/no-such-page');

    expect(screen.getByRole('heading', { name: 'Page Not Found' })).toBeInTheDocument();
  });
});
