// Create Your First Portfolio, at /portfolios for a user with none: Try a Sample Portfolio (TS-231).
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { PortfolioStart } from './PortfolioStart';

function answer(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status });
}

const SAMPLE = {
  id: 42,
  name: 'Sample Portfolio',
  createdAt: '2026-11-08T00:00:00Z',
  newsStatus: 'IDLE',
  newsStatusReason: null,
  newsCheckedAt: null,
};

function renderStart() {
  return render(
    <MemoryRouter initialEntries={['/portfolios']}>
      <Routes>
        <Route path="/portfolios" element={<PortfolioStart />} />
        <Route path="/portfolios/:portfolioId" element={<p>Workspace 42</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('PortfolioStart', () => {
  it('shows Try a Sample Portfolio beside Create Portfolio to a user with no portfolios', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(answer(200, [])));

    renderStart();

    expect(await screen.findByRole('heading', { name: 'Create Your First Portfolio' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create Portfolio' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try a Sample Portfolio' })).toBeEnabled();
  });

  it('creates the sample portfolio and opens its workspace', async () => {
    const fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/portfolios/sample' && init?.method === 'POST') return Promise.resolve(answer(201, SAMPLE));
      return Promise.resolve(answer(200, []));
    });
    vi.stubGlobal('fetch', fetch);

    renderStart();
    fireEvent.click(await screen.findByRole('button', { name: 'Try a Sample Portfolio' }));

    expect(await screen.findByText('Workspace 42')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/portfolios/sample', expect.objectContaining({ method: 'POST' }));
  });

  it('disables the button while the sample portfolio is being created', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) =>
      url === '/api/portfolios/sample' ? new Promise(() => {}) : Promise.resolve(answer(200, []))));

    renderStart();
    const button = await screen.findByRole('button', { name: 'Try a Sample Portfolio' });
    fireEvent.click(button);

    expect(button).toBeDisabled();
  });

  it('shows the error when the sample portfolio cannot be created', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) =>
      url === '/api/portfolios/sample'
        ? Promise.resolve(answer(500, { message: 'The sample portfolio could not be created.' }))
        : Promise.resolve(answer(200, []))));

    renderStart();
    fireEvent.click(await screen.findByRole('button', { name: 'Try a Sample Portfolio' }));

    expect(await screen.findByText('The sample portfolio could not be created.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try a Sample Portfolio' })).toBeEnabled();
  });
});
