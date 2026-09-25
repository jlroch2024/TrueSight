import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../App';

function renderPortfolio(fetchMock: ReturnType<typeof vi.fn>) {
  vi.stubGlobal('fetch', fetchMock);
  return render(
    <MemoryRouter initialEntries={['/portfolios/7']}>
      <App />
    </MemoryRouter>,
  );
}

describe('PortfolioPage', () => {
  it('shows company status, report details and a link to the SEC report', async () => {
    const holdings = [
      {
        id: 1,
        ticker: 'NVDA',
        companyName: 'NVIDIA Corporation',
        status: 'DONE',
        statusReason: null,
        reportType: '10-K',
        filingDate: '2026-02-25',
        reportUrl: 'https://www.sec.gov/Archives/edgar/data/1045810/report.htm',
      },
    ];
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(holdings)));

    renderPortfolio(fetchMock);

    expect(await screen.findByText('NVIDIA Corporation')).toBeInTheDocument();
    expect(screen.getByText('Done')).toBeInTheDocument();
    expect(screen.getByText('10-K')).toBeInTheDocument();
    expect(screen.getByText('2026-02-25')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Show in Report' })).toHaveAttribute('href', holdings[0].reportUrl);
  });

  it('shows the SEC failure reason and disables Analyse when a holding failed', async () => {
    const holdings = [
      {
        id: 1,
        ticker: 'NVDA',
        companyName: 'NVIDIA Corporation',
        status: 'FAILED',
        statusReason: 'The SEC could not be reached.',
        reportType: null,
        filingDate: null,
        reportUrl: null,
      },
    ];
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(holdings)));

    renderPortfolio(fetchMock);

    expect(await screen.findByText('Failed')).toBeInTheDocument();
    expect(screen.getByText('The SEC could not be reached.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Analyse' })).toBeDisabled();
  });

  it('starts analysis for the portfolio in the address', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([
        { id: 1, ticker: 'NVDA', companyName: 'NVIDIA Corporation', status: 'WAITING', statusReason: null, reportType: null, filingDate: null, reportUrl: null },
      ])))
      .mockResolvedValueOnce(new Response('', { status: 202 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([
        { id: 1, ticker: 'NVDA', companyName: 'NVIDIA Corporation', status: 'ANALYSING', statusReason: null, reportType: null, filingDate: null, reportUrl: null },
      ])));

    renderPortfolio(fetchMock);
    fireEvent.click(await screen.findByRole('button', { name: 'Analyse' }));

    expect(await screen.findByText('Analysing')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/portfolios/7/analysis', expect.objectContaining({ method: 'POST' }));
  });
});
