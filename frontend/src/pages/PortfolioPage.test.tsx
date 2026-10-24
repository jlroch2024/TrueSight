// Upload a Portfolio CSV, as the user sees it: the holdings table, Upload CSV, and TrueSight's own confirmation before
// replacing.
// The backend is a small fake that keeps each portfolio's holdings in memory.
import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { tokenStore } from '../api/client';
import { portfolioChanges } from '../api/portfolios';
import { PortfolioPage } from './PortfolioPage';

const portfolios = [
  { id: 1, name: 'Tech', createdAt: '' },
  { id: 2, name: 'Energy', createdAt: '' },
];
let holdings: Record<string, string[]>;
let uploads: number;
// What each test file contains. The test browser cannot read a File back, so the fake backend looks it up here.
const contents = new Map<File, string>();

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function asHoldings(tickers: string[]) {
  return tickers.map((ticker, i) => ({
    id: i + 1,
    ticker,
    weight: 10,
    companyName: null,
    status: 'WAITING',
    statusReason: null,
    reportType: null,
    reportDate: null,
    reportLink: null,
    lastAnalysed: null,
  }));
}

function fakeBackend(url: string, init: RequestInit = {}): Response {
  const id = url.split('/')[3];
  if (url === '/api/portfolios') {
    return json(portfolios);
  }
  if (url.endsWith('/holdings/upload')) {
    uploads++;
    const file = (init.body as FormData).get('file') as File;
    const [header, ...rows] = contents.get(file)!.trim().split('\n');
    if (!/ticker|symbol/i.test(header)) {
      return json({ message: 'The file needs a ticker column.' }, 400);
    }
    holdings[id] = rows.map((row) => row.split(',')[0]);
  }
  return json(asHoldings(holdings[id] ?? []));
}

function csv(text: string, name = 'holdings.csv') {
  const file = new File([text], name, { type: 'text/csv' });
  contents.set(file, text);
  return file;
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
  tokenStore.set('a-token');
  holdings = {};
  uploads = 0;
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => fakeBackend(url, init)));
});

describe('Portfolio page', () => {
  it('shows the holdings after uploading into an empty portfolio, without asking first', async () => {
    renderAt('/portfolios/1');

    expect(await screen.findByText('No holdings yet. Upload CSV adds them from a file with a ticker column.')).toBeInTheDocument();
    await userEvent.upload(screen.getByLabelText('CSV file'), csv('ticker,weight\nNVDA,10\nAMD,10\n'));

    expect(await screen.findByRole('cell', { name: 'NVDA' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'AMD' })).toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('asks before replacing existing holdings, then shows only the new ones', async () => {
    holdings['1'] = ['IBM', 'ORCL', 'SAP'];
    renderAt('/portfolios/1');

    await userEvent.upload(await screen.findByLabelText('CSV file'), csv('ticker\nNVDA\n'));

    const ask = await screen.findByRole('alertdialog', { name: 'Replace Holdings' });
    expect(ask).toHaveAccessibleDescription('This replaces the 3 holdings in Tech.');
    expect(uploads).toBe(0);
    const replace = within(ask).getByRole('button', { name: 'Replace Holdings' });
    expect(replace).toHaveClass('danger');
    await userEvent.click(replace);
    expect(await screen.findByRole('cell', { name: 'NVDA' })).toBeInTheDocument();
    expect(screen.queryByRole('cell', { name: 'IBM' })).not.toBeInTheDocument();
  });

  it('leaves the old holdings unchanged when the confirmation is cancelled', async () => {
    holdings['1'] = ['IBM'];
    renderAt('/portfolios/1');

    await userEvent.upload(await screen.findByLabelText('CSV file'), csv('ticker\nNVDA\n'));
    const ask = await screen.findByRole('alertdialog', { name: 'Replace Holdings' });
    await userEvent.click(within(ask).getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(uploads).toBe(0);
    expect(screen.getByRole('cell', { name: 'IBM' })).toBeInTheDocument();
  });

  it("shows the backend's reason when a file is refused", async () => {
    renderAt('/portfolios/1');

    await userEvent.upload(await screen.findByLabelText('CSV file'), csv('company,weight\nApple,10\n'));

    expect(await screen.findByText('The file needs a ticker column.')).toBeInTheDocument();
  });

  it("shows the holdings saved before, and switching shows the other portfolio's", async () => {
    holdings['1'] = ['NVDA'];
    holdings['2'] = ['XOM'];
    renderAt('/portfolios/1');

    expect(await screen.findByRole('cell', { name: 'NVDA' })).toBeInTheDocument();

    await userEvent.click(await screen.findByLabelText('Portfolio'));
    await userEvent.click(screen.getByRole('option', { name: 'Energy' }));

    expect(await screen.findByRole('cell', { name: 'XOM' })).toBeInTheDocument();
    expect(screen.queryByRole('cell', { name: 'NVDA' })).not.toBeInTheDocument();
  });

  it('uses the renamed portfolio from the announcement without listing portfolios again', async () => {
    render(
      <MemoryRouter initialEntries={['/portfolios/1']}>
        <Routes>
          <Route path="/portfolios/:portfolioId" element={<PortfolioPage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByRole('heading', { name: 'Tech' })).toBeInTheDocument();
    const listCalls = () => vi.mocked(fetch).mock.calls.filter(([url]) => url === '/api/portfolios');
    const before = listCalls().length;

    act(() => {
      portfolioChanges.announce({
        id: 1,
        name: 'Technology',
        createdAt: '',
        newsStatus: 'IDLE',
        newsStatusReason: null,
        newsCheckedAt: null,
      });
    });

    expect(await screen.findByRole('heading', { name: 'Technology' })).toBeInTheDocument();
    expect(listCalls()).toHaveLength(before);
  });
});
