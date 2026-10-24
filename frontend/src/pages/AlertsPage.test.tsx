import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Alert, AlertStatus, AlertsResponse, EventType } from '../api/alerts';
import { tokenStore } from '../api/client';
import type { Portfolio } from '../api/portfolios';
import { sampleNews } from '../test/sampleNews';
import { AlertsPage } from './AlertsPage';

const portfolio: Portfolio = {
  id: 7,
  name: 'Semiconductors',
  createdAt: '2026-10-01T00:00:00Z',
  newsStatus: 'IDLE',
  newsStatusReason: null,
  newsCheckedAt: '2026-10-14T02:42:00Z',
};

const [firstArticle, secondArticle] = sampleNews(2);

function alert(id: number, severity: 'HIGH' | 'LOW', article = firstArticle!): Alert {
  return {
    id,
    status: 'NEW',
    exposure: severity === 'HIGH' ? 40 : 90,
    createdAt: '2026-10-14T02:42:00Z',
    article: {
      title: article.title,
      source: article.source,
      publishedAt: article.publishedAt,
      url: article.url,
    },
    assessment: {
      eventType: severity === 'HIGH' ? 'PRODUCTION_STOP' : 'SUPPLY_SHORTAGE',
      severity,
      confidence: 'MEDIUM',
      summary: severity === 'HIGH'
        ? 'A fire stopped production at one TSMC plant.'
        : 'A shortage affected production.',
      reason: severity === 'HIGH'
        ? 'The headline reports a production stoppage.'
        : 'The headline reports a shortage.',
    },
    holdings: [{
      holdingId: 3,
      ticker: 'NVDA',
      weight: 40,
      supplierName: 'Taiwan Semiconductor Manufacturing Company Limited',
      customerName: 'NVIDIA Corporation',
      reviewStatus: 'UNREVIEWED',
      relationshipId: 9,
    }],
  };
}

let savedAlerts: Alert[];
let otherPortfolioAlerts: Alert[];
let weighted: boolean;
let checkedPortfolio: Portfolio;
const otherPortfolio: Portfolio = { ...portfolio, id: 8, name: 'Other Portfolio' };

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function fakeBackend(url: string, init: RequestInit = {}): Response {
  if (url === '/api/portfolios') return json([checkedPortfolio, otherPortfolio]);
  if (url === '/api/portfolios/7/news-check') return new Response(null, { status: 202 });
  const statusPath = url.match(/^\/api\/portfolios\/(7|8)\/alerts\/(\d+)$/);
  if (statusPath && init.method === 'PUT') {
    const currentAlerts = statusPath[1] === '7' ? savedAlerts : otherPortfolioAlerts;
    const id = Number(statusPath[2]);
    const status = JSON.parse(String(init.body)).status as AlertStatus;
    const updated = currentAlerts.map((item) => item.id === id ? { ...item, status } : item);
    if (statusPath[1] === '7') savedAlerts = updated;
    else otherPortfolioAlerts = updated;
    return json(updated.find((item) => item.id === id));
  }
  const alertsPath = url.match(/^\/api\/portfolios\/(7|8)\/alerts/);
  if (alertsPath) {
    const currentAlerts = alertsPath[1] === '7' ? savedAlerts : otherPortfolioAlerts;
    const includeDismissed = url.includes('includeDismissed=true');
    const response: AlertsResponse = {
      weighted,
      holdingCount: 7,
      alerts: currentAlerts.filter((item) => includeDismissed || item.status !== 'DISMISSED'),
    };
    return json(response);
  }
  return json({ message: 'Not found.' }, 404);
}

function SwitchPortfolioButton() {
  const navigate = useNavigate();
  return <button type="button" onClick={() => navigate('/portfolios/8/alerts')}>Switch portfolio</button>;
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/portfolios/7/alerts']}>
      <Routes>
        <Route path="/portfolios/:portfolioId/alerts" element={<><SwitchPortfolioButton /><AlertsPage /></>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function firstCard() {
  return (await screen.findAllByRole('heading', { level: 2 }))[0]!.closest('li')!;
}

beforeEach(() => {
  tokenStore.set('a-token');
  checkedPortfolio = { ...portfolio };
  weighted = true;
  savedAlerts = [alert(7, 'HIGH'), alert(8, 'LOW', secondArticle!)];
  otherPortfolioAlerts = [alert(7, 'HIGH')];
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => fakeBackend(url, init)));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('Alerts page', () => {
  it('shows every alert field in backend severity order', async () => {
    renderPage();

    const headings = await screen.findAllByRole('heading', { level: 2 });
    expect(headings.map((heading) => heading.textContent)).toEqual([
      firstArticle!.title,
      secondArticle!.title,
    ]);
    const high = headings[0]!.closest('li')!;
    expect(high).toHaveTextContent('Yahoo Finance');
    expect(high).toHaveTextContent('Production Stop');
    expect(high).toHaveTextContent('High');
    expect(high).toHaveTextContent('Medium');
    expect(high).toHaveTextContent('Open');
    expect(high).toHaveTextContent('A fire stopped production at one TSMC plant.');
    expect(high).toHaveTextContent('The headline reports a production stoppage.');
    expect(high).toHaveTextContent('Based on the Headline Only');
    expect(high).toHaveTextContent('NVDA');
    expect(high).toHaveTextContent('Taiwan Semiconductor Manufacturing supplies NVIDIA');
    expect(high).toHaveTextContent('Not Yet Reviewed by an Analyst');
    expect(within(high).getByRole('button', { name: 'Open Article' })).toBeInTheDocument();
  });

  it('shows the total weight and each holding\'s weight in a weighted portfolio', async () => {
    savedAlerts = [{
      ...alert(7, 'HIGH'),
      holdings: [
        { ...alert(7, 'HIGH').holdings[0]! },
        { holdingId: 4, ticker: 'TSM', weight: null, supplierName: null, customerName: null, reviewStatus: null, relationshipId: null },
      ],
    }];
    renderPage();
    const card = await firstCard();

    expect(card).toHaveTextContent('40%');
    const rows = within(card).getAllByRole('listitem');
    expect(rows[0]).toHaveTextContent('40%');
    expect(rows[1]).toHaveTextContent('Weight Not Provided');
  });

  it('counts the holdings reached, and mentions no weight, in a portfolio without weights', async () => {
    weighted = false;
    savedAlerts = [{
      ...alert(7, 'HIGH'),
      exposure: 2,
      holdings: [
        { ...alert(7, 'HIGH').holdings[0]!, weight: null },
        { holdingId: 4, ticker: 'TSM', weight: null, supplierName: null, customerName: null, reviewStatus: null, relationshipId: null },
      ],
    }];
    renderPage();
    const card = await firstCard();

    expect(card).toHaveTextContent('2 of 7 Holdings');
    expect(card).not.toHaveTextContent('%');
    expect(card).not.toHaveTextContent(/Weight/i);
  });

  it('names a linked holding through both companies\' display names, and one in the article as such', async () => {
    savedAlerts = [{
      ...alert(7, 'HIGH'),
      holdings: [
        { ...alert(7, 'HIGH').holdings[0]!, supplierName: 'ASML HOLDING NV', customerName: 'Taiwan Semiconductor Manufacturing Company Limited' },
        { holdingId: 4, ticker: 'TSM', weight: 10, supplierName: null, customerName: null, reviewStatus: null, relationshipId: null },
      ],
    }];
    renderPage();
    const card = await firstCard();

    const rows = within(card).getAllByRole('listitem');
    expect(rows[0]).toHaveTextContent('ASML Holding supplies Taiwan Semiconductor Manufacturing');
    expect(rows[1]).toHaveTextContent('Named in the Article');
  });

  it.each<[EventType, string]>([
    ['PRODUCTION_STOP', 'Production Stop'],
    ['SUPPLY_SHORTAGE', 'Supply Shortage'],
    ['LABOUR_ACTION', 'Labour Action'],
    ['NATURAL_DISASTER', 'Natural Disaster'],
    ['TRADE_RESTRICTION', 'Trade Restriction'],
    ['FINANCIAL_DISTRESS', 'Financial Distress'],
    ['LEGAL_OR_REGULATORY', 'Legal or Regulatory'],
    ['CYBER_ATTACK', 'Cyber Attack'],
    ['OTHER_DISRUPTION', 'Other Disruption'],
  ])('reads the event type %s as "%s"', async (eventType, label) => {
    const base = alert(7, 'HIGH');
    savedAlerts = [{ ...base, assessment: { ...base.assessment, eventType } }];
    renderPage();

    expect(await firstCard()).toHaveTextContent(label);
  });

  it('offers no Acknowledge, only Dismiss', async () => {
    renderPage();
    const card = await firstCard();

    expect(within(card).queryByRole('button', { name: 'Acknowledge' })).not.toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Dismiss' })).toBeInTheDocument();
  });

  it('filters Open, Dismissed and All with their counts', async () => {
    savedAlerts = [alert(7, 'HIGH'), { ...alert(8, 'LOW', secondArticle!), status: 'DISMISSED' }];
    renderPage();
    await firstCard();

    expect(screen.getByLabelText('Open (1)')).toBeChecked();
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([firstArticle!.title]);

    await userEvent.click(screen.getByLabelText('Dismissed (1)'));
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([secondArticle!.title]);

    await userEvent.click(screen.getByLabelText('All (2)'));
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(2);
  });

  it('dims and tags a dismissed alert under All, and Restore makes it Open again', async () => {
    savedAlerts = [alert(7, 'HIGH'), { ...alert(8, 'LOW', secondArticle!), status: 'DISMISSED' }];
    renderPage();
    await firstCard();
    await userEvent.click(screen.getByLabelText('All (2)'));

    const [open, dismissed] = screen.getAllByRole('heading', { level: 2 }).map((h) => h.closest('li')!);
    expect(open).not.toHaveClass('dismissed');
    expect(dismissed).toHaveClass('alert-card', 'dismissed');
    expect(within(dismissed!).getAllByText('Dismissed')[0]).toHaveClass('news-tag');

    await userEvent.click(within(dismissed!).getByRole('button', { name: 'Restore' }));
    await waitFor(() => expect(dismissed).not.toHaveClass('dismissed'));
    expect(savedAlerts[1]!.status).toBe('NEW');
    expect(screen.getByLabelText('Open (2)')).toBeInTheDocument();
  });

  it('dismisses an alert, which stays dismissed after a refresh', async () => {
    savedAlerts = [alert(7, 'HIGH')];
    const firstRender = renderPage();
    const card = await firstCard();

    await userEvent.click(within(card).getByRole('button', { name: 'Dismiss' }));
    expect(await screen.findByText('No open alerts. Dismissed alerts are under Dismissed.')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Alert Dismissed. Undo');

    firstRender.unmount();
    renderPage();
    expect(await screen.findByText('No open alerts. Dismissed alerts are under Dismissed.')).toBeInTheDocument();
    expect(screen.getByLabelText('Dismissed (1)')).toBeInTheDocument();
  });

  it('undoes a Dismiss, making the alert Open again', async () => {
    savedAlerts = [alert(7, 'HIGH')];
    renderPage();
    const card = await firstCard();

    await userEvent.click(within(card).getByRole('button', { name: 'Dismiss' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Undo' }));

    expect(await screen.findByRole('heading', { level: 2 })).toHaveTextContent(firstArticle!.title);
    expect(savedAlerts[0]!.status).toBe('NEW');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('disablesUndoWhileTheRestoreIsBeingSent', async () => {
    savedAlerts = [alert(7, 'HIGH')];
    renderPage();
    const card = await firstCard();
    await userEvent.click(within(card).getByRole('button', { name: 'Dismiss' }));
    const undo = await screen.findByRole('button', { name: 'Undo' });

    let finishRestore!: () => void;
    const restoreGate = new Promise<void>((resolve) => { finishRestore = resolve; });
    vi.mocked(fetch).mockImplementationOnce(async (url: RequestInfo | URL, init?: RequestInit) => {
      await restoreGate;
      return fakeBackend(String(url), init);
    });

    await userEvent.click(undo);
    expect(undo).toBeDisabled();
    finishRestore();

    expect(await screen.findByRole('heading', { level: 2 })).toHaveTextContent(firstArticle!.title);
    expect(savedAlerts[0]!.status).toBe('NEW');
  });

  it('clears Undo when switching portfolios', async () => {
    savedAlerts = [alert(7, 'HIGH')];
    otherPortfolioAlerts = [alert(8, 'HIGH')];
    renderPage();
    const card = await firstCard();

    await userEvent.click(within(card).getByRole('button', { name: 'Dismiss' }));
    expect(await screen.findByRole('button', { name: 'Undo' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Switch portfolio' }));

    expect(await screen.findByRole('heading', { level: 2 })).toHaveTextContent(firstArticle!.title);
    expect(screen.queryByRole('button', { name: 'Undo' })).not.toBeInTheDocument();
    expect(vi.mocked(fetch).mock.calls.some(([url, init]) =>
      String(url).startsWith('/api/portfolios/8/alerts/') && init?.method === 'PUT')).toBe(false);
    expect(otherPortfolioAlerts[0]?.status).toBe('NEW');
  });

  it('offers Undo for 8 seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    savedAlerts = [alert(7, 'HIGH')];
    renderPage();
    const card = await firstCard();

    await user.click(within(card).getByRole('button', { name: 'Dismiss' }));
    expect(await screen.findByRole('button', { name: 'Undo' })).toBeInTheDocument();

    act(() => { vi.advanceTimersByTime(7_000); });
    expect(screen.getByRole('button', { name: 'Undo' })).toBeInTheDocument();
    act(() => { vi.advanceTimersByTime(1_000); });
    expect(screen.queryByRole('button', { name: 'Undo' })).not.toBeInTheDocument();
  });

  it('shows no filtered news and no article counts', async () => {
    renderPage();
    await firstCard();

    expect(screen.queryByRole('button', { name: /Filtered News/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Checked \d+ articles?/)).not.toBeInTheDocument();
    expect(vi.mocked(fetch).mock.calls.map(([url]) => String(url))).not.toContainEqual(
      expect.stringContaining('/news?'),
    );
  });

  it('shows the checked empty state and offers Check News', async () => {
    savedAlerts = [];
    renderPage();

    expect(await screen.findByText('No events found in the last 7 days.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Check News' })).toBeInTheDocument();
  });

  it('saysThereAreNoOpenAlertsWhenEveryAlertIsDismissed', async () => {
    savedAlerts = [{ ...alert(7, 'HIGH'), status: 'DISMISSED' }];
    renderPage();

    expect(await screen.findByText('No open alerts. Dismissed alerts are under Dismissed.')).toBeInTheDocument();
    expect(screen.queryByText('No events found in the last 7 days.')).not.toBeInTheDocument();
  });

  it('shows the not-yet-checked empty state and offers Check News', async () => {
    savedAlerts = [];
    checkedPortfolio = { ...portfolio, newsCheckedAt: null };
    renderPage();

    expect(await screen.findByText('News has not been checked yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Check News' })).toBeInTheDocument();
  });
});
