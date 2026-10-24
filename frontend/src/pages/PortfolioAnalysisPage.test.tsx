import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStore } from '../api/client';
import { formatDateTime } from '../format';
import { sampleNews } from '../test/sampleNews';
import { PortfolioPage } from './PortfolioPage';

const portfolio = { id: 7, name: 'Demo Portfolio', createdAt: '' };
const reportUrl = 'https://www.sec.gov/Archives/edgar/data/1045810/report.htm';

function row(status: string, extra: Record<string, unknown> = {}) {
  return {
    id: 1,
    ticker: 'NVDA',
    weight: null,
    // The SEC's name for NVIDIA, and the display name the API sends for it: NVIDIA's own report writes "NVIDIA".
    companyName: 'NVIDIA CORP',
    companyDisplayName: 'NVIDIA',
    status,
    statusReason: null,
    reportType: status === 'DONE' ? '10-K' : null,
    reportDate: status === 'DONE' ? '2026-02-25' : null,
    reportLink: status === 'DONE' ? reportUrl : null,
    lastAnalysed: status === 'DONE' ? '2026-03-01T09:30:00Z' : null,
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
  it('shows last analysed time and opens the report without a text fragment', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(
      url.endsWith('/holdings') ? [row('DONE')] : [portfolio],
    ))));

    renderPage();

    expect(await screen.findByText('NVIDIA')).toBeInTheDocument();
    expect(screen.queryByText('Nvidia')).not.toBeInTheDocument();
    expect(screen.getByText('Done')).toBeInTheDocument();
    expect(screen.getByText('10-K')).toBeInTheDocument();
    expect(screen.getByText('Feb 25, 2026')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Last Analysed' })).toBeInTheDocument();
    expect(screen.getByText(formatDateTime('2026-03-01T09:30:00Z'))).toBeInTheDocument();
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    fireEvent.click(screen.getByRole('button', { name: 'Show Report' }));
    expect(open).toHaveBeenCalledWith(reportUrl, '_blank', 'noopener,noreferrer');
  });

  it('shows neither the analysis time nor report button before a holding is done', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(
      url.endsWith('/holdings') ? [row('ANALYSING', { reportLink: reportUrl, lastAnalysed: '2026-03-01T09:30:00Z' })] : [portfolio],
    ))));

    renderPage();

    expect(await screen.findByText('Analysing')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Show Report' })).not.toBeInTheDocument();
    const rowElement = screen.getByRole('row', { name: /NVIDIA/ });
    expect(rowElement.querySelectorAll('td')[4]).toHaveTextContent('—');
  });

  // TS-90: a failure is usually the SEC or the AI being briefly unreachable, so Analyse stays available to retry it.
  it('shows the SEC failure reason, and keeps Analyse available to try again', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(
      url.endsWith('/holdings') ? [row('FAILED', { statusReason: 'The SEC could not be reached.' })] : [portfolio],
    ))));

    renderPage();

    expect(await screen.findByText('Failed')).toBeInTheDocument();
    expect(screen.getByText('The SEC could not be reached.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Analyse' })).toBeEnabled();
    expect(screen.getByText('Some holdings failed. Press Analyse to try them again.')).toBeInTheDocument();
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

  it('moves the subtitle from reading reports to the count analysed when analysis ends, without reloading', async () => {
    let status = 'ANALYSING';
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(
      url.endsWith('/holdings') ? [row(status)] : [portfolio],
    ))));

    renderPage();
    expect(await screen.findByText('Reading Annual Reports: 0 of 1 Done')).toBeInTheDocument();

    status = 'DONE';
    expect(await screen.findByText(/1 of 1 Holding Analysed/, undefined, { timeout: 3000 })).toBeInTheDocument();
  });

  it('opens the same portfolio\'s Supply Chain from the subtitle once a holding is Done', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(
      url.endsWith('/holdings') ? [row('DONE')] : [portfolio],
    ))));
    render(
      <MemoryRouter initialEntries={['/portfolios/7']}>
        <Routes>
          <Route path="/portfolios/:portfolioId" element={<PortfolioPage />} />
          <Route path="/portfolios/:portfolioId/supply-chain" element={<p>Supply Chain of portfolio 7</p>} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(await screen.findByRole('button', { name: 'Open Supply Chain' }));

    expect(screen.getByText('Supply Chain of portfolio 7')).toBeInTheDocument();
  });

  it('shows how many articles the last news check found, and See News opens them on the Supply Chain page', async () => {
    const checked = { ...portfolio, newsStatus: 'IDLE', newsStatusReason: null, newsCheckedAt: '2026-10-13T00:00:00Z' };
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      if (url.endsWith('/news')) return new Response(JSON.stringify(sampleNews(2)));
      return new Response(JSON.stringify(url.endsWith('/holdings') ? [row('DONE')] : [checked]));
    }));
    render(
      <MemoryRouter initialEntries={['/portfolios/7']}>
        <Routes>
          <Route path="/portfolios/:portfolioId" element={<PortfolioPage />} />
          <Route path="/portfolios/:portfolioId/supply-chain" element={<SearchShown />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText(/· 2 Articles/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'See News' }));

    expect(screen.getByText('Supply Chain with ?panel=news')).toBeInTheDocument();
  });
});

function SearchShown() {
  return <p>{`Supply Chain with ${useLocation().search}`}</p>;
}
