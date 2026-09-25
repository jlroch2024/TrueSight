import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStore } from '../api/client';
import { PortfolioPage } from './PortfolioPage';

const portfolio = { id: 7, name: 'Demo Portfolio', createdAt: '' };
const reportUrl = 'https://www.sec.gov/Archives/edgar/data/1045810/report.htm';

function row(status: string, extra: Record<string, unknown> = {}) {
  return {
    id: 1,
    ticker: 'NVDA',
    weight: null,
    companyName: 'NVIDIA Corporation',
    status,
    statusReason: null,
    reportType: status === 'DONE' ? '10-K' : null,
    reportDate: status === 'DONE' ? '2026-02-25' : null,
    reportLink: status === 'DONE' ? reportUrl : null,
    ...extra,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/portfolios/7']}>
      <Routes><Route path="/portfolios/:portfolioId" element={<PortfolioPage />} /></Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  tokenStore.set('test-token');
});

describe('Portfolio annual report analysis', () => {
  it('shows the company status, report details, and SEC link', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(
      url.endsWith('/holdings') ? [row('DONE')] : [portfolio],
    ))));

    renderPage();

    expect(await screen.findByText('NVIDIA Corporation')).toBeInTheDocument();
    expect(screen.getByText('Done')).toBeInTheDocument();
    expect(screen.getByText('10-K')).toBeInTheDocument();
    expect(screen.getByText('2026-02-25')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Show in Report' })).toHaveAttribute('href', reportUrl);
  });

  it('shows the SEC failure reason and disables Analyse after failure', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(
      url.endsWith('/holdings') ? [row('FAILED', { statusReason: 'The SEC could not be reached.' })] : [portfolio],
    ))));

    renderPage();

    expect(await screen.findByText('Failed')).toBeInTheDocument();
    expect(screen.getByText('The SEC could not be reached.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Analyse' })).toBeDisabled();
  });

  it('starts analysis for the portfolio shown in the page address', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/portfolios/7/analysis') return new Response('', { status: 202 });
      const data = url.endsWith('/holdings') ? [row('WAITING')] : [portfolio];
      return new Response(JSON.stringify(data));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Analyse' }));

    expect(fetchMock).toHaveBeenCalledWith('/api/portfolios/7/analysis', expect.objectContaining({ method: 'POST' }));
  });
});
