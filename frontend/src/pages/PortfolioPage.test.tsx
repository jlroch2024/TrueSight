// Upload a Portfolio CSV, as the user sees it: the holdings table, Upload CSV, and the confirmation before replacing.
// The backend is a small fake that keeps each portfolio's holdings in memory.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { tokenStore } from '../api/client';

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
    const confirm = vi.spyOn(window, 'confirm');
    renderAt('/portfolios/1');

    expect(await screen.findByText('This portfolio has no holdings yet. Upload a CSV to add them.')).toBeInTheDocument();
    await userEvent.upload(screen.getByLabelText('Upload CSV'), csv('ticker,weight\nNVDA,10\nAMD,10\n'));

    expect(await screen.findByRole('cell', { name: 'NVDA' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'AMD' })).toBeInTheDocument();
    expect(confirm).not.toHaveBeenCalled();
  });

  it('asks before replacing existing holdings, then shows only the new ones', async () => {
    holdings['1'] = ['IBM', 'ORCL', 'SAP'];
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderAt('/portfolios/1');

    await userEvent.upload(await screen.findByLabelText('Upload CSV'), csv('ticker\nNVDA\n'));

    expect(confirm).toHaveBeenCalledWith('This replaces the 3 holdings in Tech.');
    expect(await screen.findByRole('cell', { name: 'NVDA' })).toBeInTheDocument();
    expect(screen.queryByRole('cell', { name: 'IBM' })).not.toBeInTheDocument();
  });

  it('leaves the old holdings unchanged when the confirmation is cancelled', async () => {
    holdings['1'] = ['IBM'];
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderAt('/portfolios/1');

    await userEvent.upload(await screen.findByLabelText('Upload CSV'), csv('ticker\nNVDA\n'));

    expect(uploads).toBe(0);
    expect(screen.getByRole('cell', { name: 'IBM' })).toBeInTheDocument();
  });

  it("shows the backend's reason when a file is refused", async () => {
    renderAt('/portfolios/1');

    await userEvent.upload(await screen.findByLabelText('Upload CSV'), csv('company,weight\nApple,10\n'));

    expect(await screen.findByText('The file needs a ticker column.')).toBeInTheDocument();
  });

  it("shows the holdings saved before, and switching shows the other portfolio's", async () => {
    holdings['1'] = ['NVDA'];
    holdings['2'] = ['XOM'];
    renderAt('/portfolios/1');

    expect(await screen.findByRole('cell', { name: 'NVDA' })).toBeInTheDocument();

    await userEvent.selectOptions(await screen.findByLabelText('Open Portfolio'), 'Energy');

    expect(await screen.findByRole('cell', { name: 'XOM' })).toBeInTheDocument();
    expect(screen.queryByRole('cell', { name: 'NVDA' })).not.toBeInTheDocument();
  });
});
