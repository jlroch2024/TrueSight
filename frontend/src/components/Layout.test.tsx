// Proves the sidebar shows a portfolio's own pages only while that portfolio is open.
// The list of portfolio pages is replaced with a sample here, because the real one is empty until stories add pages.
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { Layout } from './Layout';

vi.mock('../navigation', () => ({
  NAV_ITEMS: [{ label: 'Home', path: '/' }],
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

  it('hides portfolio pages when no portfolio is open', () => {
    renderAt('/');

    expect(screen.queryByRole('link', { name: 'Supply Chain' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Home' })).toBeInTheDocument();
  });
});
