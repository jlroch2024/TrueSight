// The Portfolio page, as the user sees it: the holdings table, Upload CSV and TrueSight's own confirmation before
// replacing, and adding, re-weighting and removing one holding.
// The backend is a small fake that keeps each portfolio's holdings in memory.
import { act, render, screen, waitFor, within } from '@testing-library/react';
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
// What the fake backend knows about each holding beyond its ticker, by "portfolio/ticker". A weight not set here is
// 10, and a status not set here is Waiting. changes records every add, weight change and removal it was asked for.
let weights: Map<string, number | null>;
let statuses: Map<string, string>;
let ids: Map<string, number>;
let changes: { method: string; url: string; body: unknown }[];
// What each test file contains. The test browser cannot read a File back, so the fake backend looks it up here.
const contents = new Map<File, string>();

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

// A holding's id stays the same as others are added and removed, like the real one.
function idFor(portfolio: string, ticker: string) {
  const key = `${portfolio}/${ticker}`;
  if (!ids.has(key)) ids.set(key, ids.size + 1);
  return ids.get(key)!;
}

function asHoldings(portfolio: string, tickers: string[]) {
  return tickers.map((ticker) => ({
    id: idFor(portfolio, ticker),
    ticker,
    weight: weights.has(`${portfolio}/${ticker}`) ? weights.get(`${portfolio}/${ticker}`) : 10,
    companyName: null,
    companyDisplayName: null,
    status: statuses.get(`${portfolio}/${ticker}`) ?? 'WAITING',
    statusReason: null,
    reportType: null,
    reportDate: null,
    reportLink: null,
    lastAnalysed: null,
  }));
}

// Add, change a weight, or remove one holding. The real backend starts an added holding's analysis at once, so the
// fake does too: the answer shows it Waiting, and the next time the list is read it is Analysing.
function editHolding(id: string, url: string, init: RequestInit): Response {
  const method = init.method ?? 'GET';
  const body = init.body ? JSON.parse(String(init.body)) : undefined;
  changes.push({ method, url, body });
  const tickers = holdings[id] ?? [];
  if (method === 'POST') {
    const ticker = String(body.ticker).toUpperCase();
    if (tickers.includes(ticker)) return json({ message: `${ticker} is already in this portfolio.` }, 409);
    holdings[id] = [...tickers, ticker];
    weights.set(`${id}/${ticker}`, body.weight);
    const added = asHoldings(id, [ticker])[0];
    statuses.set(`${id}/${ticker}`, 'ANALYSING');
    return json(added, 201);
  }
  const holdingId = Number(url.split('/').pop());
  const ticker = tickers.find((t) => idFor(id, t) === holdingId)!;
  if (method === 'PUT') {
    if (body.weight !== null && (body.weight < 0 || body.weight > 100)) {
      return json({ message: 'The weight must be between 0 and 100.' }, 400);
    }
    weights.set(`${id}/${ticker}`, body.weight);
    return json(asHoldings(id, [ticker])[0]);
  }
  holdings[id] = tickers.filter((t) => t !== ticker);
  return new Response(null, { status: 204 });
}

function fakeBackend(url: string, init: RequestInit = {}): Response {
  const id = url.split('/')[3];
  if (url === '/api/portfolios') {
    return json(portfolios);
  }
  if (/\/holdings(\/\d+)?$/.test(url) && (init.method === 'POST' || init.method === 'PUT' || init.method === 'DELETE')) {
    return editHolding(id, url, init);
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
  return json(asHoldings(id, holdings[id] ?? []));
}

function csv(text: string, name = 'holdings.csv') {
  const file = new File([text], name, { type: 'text/csv' });
  contents.set(file, text);
  return file;
}

// The weight cell of one holding's row: the third, after Company and Ticker.
function weightCell(ticker: string) {
  const row = screen.getByRole('cell', { name: ticker }).closest('tr')!;
  return within(row).getAllByRole('cell')[2]!;
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
  weights = new Map();
  statuses = new Map();
  ids = new Map();
  changes = [];
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

  describe('adding, re-weighting and removing one holding', () => {
    it('adds a holding that shows Waiting, then Analysing, then its result', async () => {
      holdings['1'] = ['IBM'];
      renderAt('/portfolios/1');
      await screen.findByRole('cell', { name: 'IBM' });

      await userEvent.type(screen.getByLabelText('Ticker'), 'nvda');
      await userEvent.type(screen.getByLabelText('Weight (%)'), '12.5');
      await userEvent.click(screen.getByRole('button', { name: 'Add Holding' }));

      // The ticker is sent as typed; the backend makes it upper-case.
      expect(changes).toEqual([{ method: 'POST', url: '/api/portfolios/1/holdings', body: { ticker: 'nvda', weight: 12.5 } }]);
      expect(await screen.findByRole('cell', { name: 'NVDA' })).toBeInTheDocument();
      expect(screen.getByRole('cell', { name: '12.5%' })).toBeInTheDocument();
      // The page looks again straight after adding, and finds the analysis started.
      expect(await screen.findByText('Analysing')).toBeInTheDocument();
      expect(screen.getByLabelText('Ticker')).toHaveValue('');
      expect(screen.getByLabelText('Weight (%)')).toHaveValue('');

      // While it is Analysing the page keeps looking, so the result appears without reloading.
      statuses.set('1/NVDA', 'DONE');
      expect(await screen.findByText('Done', undefined, { timeout: 4000 })).toBeInTheDocument();
    }, 10000);

    it('adds a holding with no weight', async () => {
      renderAt('/portfolios/1');
      await screen.findByText(/No holdings yet/);

      await userEvent.type(screen.getByLabelText('Ticker'), 'XOM');
      await userEvent.click(screen.getByRole('button', { name: 'Add Holding' }));

      expect(await screen.findByRole('cell', { name: 'XOM' })).toBeInTheDocument();
      expect(changes[0]!.body).toEqual({ ticker: 'XOM', weight: null });
      expect(weightCell('XOM')).toHaveTextContent('—');
    });

    it('does not add without a ticker', async () => {
      renderAt('/portfolios/1');
      await screen.findByText(/No holdings yet/);

      expect(screen.getByRole('button', { name: 'Add Holding' })).toBeDisabled();
    });

    it("shows the backend's message when the ticker is already in the portfolio", async () => {
      holdings['1'] = ['AMD'];
      renderAt('/portfolios/1');
      await screen.findByRole('cell', { name: 'AMD' });

      await userEvent.type(screen.getByLabelText('Ticker'), 'amd');
      await userEvent.click(screen.getByRole('button', { name: 'Add Holding' }));

      expect(await screen.findByText('AMD is already in this portfolio.')).toBeInTheDocument();
      expect(screen.getAllByRole('cell', { name: 'AMD' })).toHaveLength(1);
    });

    it('does not send a weight that is not a number', async () => {
      renderAt('/portfolios/1');
      await screen.findByText(/No holdings yet/);

      await userEvent.type(screen.getByLabelText('Ticker'), 'NVDA');
      await userEvent.type(screen.getByLabelText('Weight (%)'), 'lots');
      await userEvent.click(screen.getByRole('button', { name: 'Add Holding' }));

      expect(await screen.findByText('The weight must be a number between 0 and 100.')).toBeInTheDocument();
      expect(changes).toEqual([]);
    });

    it('edits a weight in place, accepting a percent sign', async () => {
      holdings['1'] = ['NVDA'];
      renderAt('/portfolios/1');

      await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
      const box = screen.getByLabelText('Weight for NVDA');
      expect(box).toHaveValue('10');
      await userEvent.clear(box);
      await userEvent.type(box, '25%');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      expect(await screen.findByRole('cell', { name: '25%' })).toBeInTheDocument();
      expect(screen.queryByLabelText('Weight for NVDA')).not.toBeInTheDocument();
      expect(changes).toEqual([{ method: 'PUT', url: '/api/portfolios/1/holdings/1', body: { weight: 25 } }]);
    });

    it('saves a blank weight as no weight', async () => {
      holdings['1'] = ['NVDA'];
      renderAt('/portfolios/1');

      await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
      await userEvent.clear(screen.getByLabelText('Weight for NVDA'));
      await userEvent.keyboard('{Enter}');

      await waitFor(() => expect(weightCell('NVDA')).toHaveTextContent('—'));
      expect(changes[0]!.body).toEqual({ weight: null });
    });

    it("shows the backend's message for a weight outside 0 to 100, and Cancel keeps the old weight", async () => {
      holdings['1'] = ['NVDA'];
      renderAt('/portfolios/1');

      await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
      const box = screen.getByLabelText('Weight for NVDA');
      await userEvent.clear(box);
      await userEvent.type(box, '150');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      expect(await screen.findByText('The weight must be between 0 and 100.')).toBeInTheDocument();
      await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
      expect(screen.getByRole('cell', { name: '10%' })).toBeInTheDocument();
    });

    it('asks before removing a holding, then removes it', async () => {
      holdings['1'] = ['IBM', 'ORCL'];
      renderAt('/portfolios/1');
      await screen.findByRole('cell', { name: 'IBM' });

      const row = screen.getByRole('cell', { name: 'IBM' }).closest('tr')!;
      await userEvent.click(within(row).getByRole('button', { name: 'Remove' }));

      const ask = await screen.findByRole('alertdialog', { name: 'Remove Holding' });
      expect(ask).toHaveAccessibleDescription('This removes IBM from Tech, and from its alerts.');
      expect(changes).toEqual([]);
      const confirm = within(ask).getByRole('button', { name: 'Remove Holding' });
      expect(confirm).toHaveClass('danger');
      await userEvent.click(confirm);

      await waitFor(() => expect(screen.queryByRole('cell', { name: 'IBM' })).not.toBeInTheDocument());
      expect(screen.getByRole('cell', { name: 'ORCL' })).toBeInTheDocument();
      expect(changes).toEqual([{ method: 'DELETE', url: '/api/portfolios/1/holdings/1', body: undefined }]);
    });

    it('keeps the holding when the confirmation is cancelled', async () => {
      holdings['1'] = ['IBM'];
      renderAt('/portfolios/1');

      await userEvent.click(await screen.findByRole('button', { name: 'Remove' }));
      const ask = await screen.findByRole('alertdialog', { name: 'Remove Holding' });
      await userEvent.click(within(ask).getByRole('button', { name: 'Cancel' }));

      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
      expect(screen.getByRole('cell', { name: 'IBM' })).toBeInTheDocument();
      expect(changes).toEqual([]);
    });
  });
});
