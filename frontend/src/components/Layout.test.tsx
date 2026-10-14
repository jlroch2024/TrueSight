// Proves the sidebar shows a portfolio's own pages only while that portfolio is open.
// The list of portfolio pages is replaced with a sample here, because the real one is empty until stories add pages.
import { render, screen, waitFor } from '@testing-library/react';
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
        <Route path="*" element={<Layout />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Layout', () => {
  it("shows the open portfolio's pages, linked to that portfolio", () => {
    renderAt('/portfolios/7');

    expect(screen.getByRole('link', { name: 'Supply Chain' })).toHaveAttribute('href', '/portfolios/7/supply-chain');
  });

  it('Log Out forgets the login and goes to the Log In page', async () => {
    tokenStore.set('a-token');
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/login" element={<h1>Log In</h1>} />
          <Route path="*" element={<Layout />} />
        </Routes>
      </MemoryRouter>,
    );

    await userEvent.setup().click(screen.getByRole('button', { name: 'Log Out' }));

    expect(tokenStore.get()).toBeNull();
    expect(screen.getByRole('heading', { name: 'Log In' })).toBeInTheDocument();
  });

  it('hides portfolio pages when no portfolio is open', () => {
    renderAt('/');

    expect(screen.queryByRole('link', { name: 'Supply Chain' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Analyst Review' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Home' })).toBeInTheDocument();
  });

  it('loads the current role for analyst navigation when logged in', async () => {
    tokenStore.set('a-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{"email":"analyst@example.com","role":"ANALYST"}'));
    renderAt('/');
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({
      headers: expect.any(Headers),
    })));
    expect(await screen.findByRole('link', { name: 'Analyst Review' })).toHaveAttribute('href', '/review');
    fetchMock.mockRestore();
    tokenStore.clear();
  });
});
