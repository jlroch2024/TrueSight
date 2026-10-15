// Proves the sidebar shows a portfolio's own pages only while that portfolio is open.
// The list of portfolio pages is replaced with a sample here, because the real one is empty until stories add pages.
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { tokenStore } from '../api/client';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { Layout } from './Layout';

vi.mock('../navigation', () => ({
  NAV_ITEMS: [{ label: 'Home', path: '/' }],
  ANALYST_NAV_ITEMS: [{ label: 'Analyst Review', path: '/review' }],
  PORTFOLIO_NAV_ITEMS: [{ label: 'Supply Chain', path: (id: string) => `/portfolios/${id}/supply-chain` }],
}));

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/portfolios/:portfolioId" element={<Layout />}>
          <Route path="supply-chain" element={<h1>Supply Chain</h1>} />
        </Route>
        <Route path="*" element={<Layout />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Layout', () => {
  it("shows the open portfolio's navigation button", async () => {
    renderAt('/portfolios/7');

    const button = screen.getByRole('button', { name: 'Supply Chain' });
    await userEvent.setup().click(button);
    expect(await screen.findByRole('heading', { name: 'Supply Chain' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Supply Chain' })).toHaveAttribute('aria-current', 'page');
  });

  it('Log Out forgets the login and goes to the Log In page', async () => {
    tokenStore.set('a-token');
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{"email":"pm@example.com","role":"PORTFOLIO_MANAGER"}')));
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/login" element={<h1>Log In</h1>} />
          <Route path="*" element={<Layout />} />
        </Routes>
      </MemoryRouter>,
    );

    const header = screen.getByRole('banner');
    await userEvent.setup().click(within(header).getByRole('button', { name: 'Log Out' }));

    expect(tokenStore.get()).toBeNull();
    expect(screen.getByRole('heading', { name: 'Log In' })).toBeInTheDocument();
  });

  it('hides portfolio pages when no portfolio is open', () => {
    renderAt('/');

    expect(screen.queryByRole('button', { name: 'Supply Chain' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Analyst Review' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Home' })).toBeInTheDocument();
  });

  it('loads the current role for analyst navigation when logged in', async () => {
    tokenStore.set('a-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{"email":"analyst@example.com","role":"ANALYST"}'));
    renderAt('/');
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({
      headers: expect.any(Headers),
    })));
    expect(await screen.findByRole('button', { name: 'Analyst Review' })).toBeInTheDocument();
    expect(await screen.findByText('analyst@example.com')).toBeInTheDocument();
    fetchMock.mockRestore();
    tokenStore.clear();
  });
});
