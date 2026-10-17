// Open on the Portfolio Page: "/" opens the first portfolio, or the Portfolios page for a user with none.
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { OpenFirstPortfolioPage } from './OpenFirstPortfolioPage';

function answer(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status });
}

// Stand-ins for the pages "/" can send the user to, so each test can see where it went.
function renderAtRoot() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route index element={<OpenFirstPortfolioPage />} />
        <Route path="/portfolios" element={<p>Portfolios page</p>} />
        <Route path="/portfolios/:portfolioId" element={<p>Portfolio page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('OpenFirstPortfolioPage', () => {
  it('opens the first portfolio in the list', async () => {
    const fetch = vi.fn().mockResolvedValue(answer(200, [
      { id: 7, name: 'Consumer Goods', createdAt: '2026-10-01T00:00:00Z' },
      { id: 3, name: 'Pharma and Biotech', createdAt: '2026-10-02T00:00:00Z' },
    ]));
    vi.stubGlobal('fetch', fetch);

    renderAtRoot();

    expect(await screen.findByText('Portfolio page')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/portfolios', expect.anything());
  });

  it('sends a user with no portfolio to the Portfolios page, to create their first', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(answer(200, [])));

    renderAtRoot();

    expect(await screen.findByText('Portfolios page')).toBeInTheDocument();
  });

  it('says what went wrong, and offers Open My Portfolios, when the portfolios cannot be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    renderAtRoot();

    expect(await screen.findByText('Cannot reach the server. Is the backend running?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open My Portfolios' })).toBeInTheDocument();
  });

  it('shows that it is opening the portfolio while the list loads', () => {
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})));

    renderAtRoot();

    expect(screen.getByText('Opening your portfolio…')).toBeInTheDocument();
  });
});
