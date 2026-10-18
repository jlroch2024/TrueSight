import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Alert, AlertStatus, AlertsResponse } from '../api/alerts';
import { tokenStore } from '../api/client';
import type { Portfolio } from '../api/portfolios';
import { sampleNews, sampleNoise } from '../test/sampleNews';
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
      link: 'TSMC supplies NVIDIA',
      reviewStatus: 'UNREVIEWED',
    }],
  };
}

let savedAlerts: Alert[];
let checkedPortfolio: Portfolio;
const noise = sampleNoise(2);

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function fakeBackend(url: string, init: RequestInit = {}): Response {
  if (url === '/api/portfolios') return json([checkedPortfolio]);
  if (url === '/api/portfolios/7/news?noise=true') return json(noise);
  if (url === '/api/portfolios/7/news-check') return new Response(null, { status: 202 });
  if (url.startsWith('/api/portfolios/7/alerts/') && init.method === 'PUT') {
    const id = Number(url.split('/').at(-1));
    const status = JSON.parse(String(init.body)).status as AlertStatus;
    savedAlerts = savedAlerts.map((item) => item.id === id ? { ...item, status } : item);
    return json(savedAlerts.find((item) => item.id === id));
  }
  if (url.startsWith('/api/portfolios/7/alerts')) {
    const includeDismissed = url.includes('includeDismissed=true');
    const response: AlertsResponse = {
      articlesChecked: 42,
      noiseCount: 37,
      alerts: savedAlerts.filter((item) => includeDismissed || item.status !== 'DISMISSED'),
    };
    return json(response);
  }
  return json({ message: 'Not found.' }, 404);
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/portfolios/7/alerts']}>
      <Routes>
        <Route path="/portfolios/:portfolioId/alerts" element={<AlertsPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  tokenStore.set('a-token');
  checkedPortfolio = { ...portfolio };
  savedAlerts = [alert(7, 'HIGH'), alert(8, 'LOW', secondArticle!)];
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => fakeBackend(url, init)));
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
    expect(high).toHaveTextContent('40%');
    expect(high).toHaveTextContent('Medium');
    expect(high).toHaveTextContent('A fire stopped production at one TSMC plant.');
    expect(high).toHaveTextContent('The headline reports a production stoppage.');
    expect(high).toHaveTextContent('Based on the headline only');
    expect(high).toHaveTextContent('NVDA');
    expect(high).toHaveTextContent('TSMC supplies NVIDIA');
    expect(high).toHaveTextContent('Not yet reviewed by an analyst');
    expect(within(high).getByRole('button', { name: 'Open Article' })).toBeInTheDocument();
  });

  it('acknowledges and dismisses an alert, with both changes surviving a refresh', async () => {
    savedAlerts = [alert(7, 'HIGH')];
    const firstRender = renderPage();
    const card = (await screen.findByRole('heading', { level: 2 })).closest('li')!;

    await userEvent.click(within(card).getByRole('button', { name: 'Acknowledge' }));
    expect(await within(card).findByText('Acknowledged')).toBeInTheDocument();

    firstRender.unmount();
    const refreshed = renderPage();
    expect(await screen.findByText('Acknowledged')).toBeInTheDocument();
    const refreshedCard = screen.getByRole('heading', { level: 2 }).closest('li')!;
    await userEvent.click(within(refreshedCard).getByRole('button', { name: 'Dismiss' }));
    expect(await screen.findByText('No events found in the last 7 days.')).toBeInTheDocument();

    refreshed.unmount();
    renderPage();
    expect(await screen.findByText('No events found in the last 7 days.')).toBeInTheDocument();
  });

  it('shows dismissed alerts and restores one to New', async () => {
    savedAlerts = [{ ...alert(7, 'HIGH'), status: 'DISMISSED' }];
    renderPage();
    expect(await screen.findByText('No events found in the last 7 days.')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Show Dismissed'));
    const card = (await screen.findByRole('heading', { level: 2 })).closest('li')!;
    expect(card).toHaveTextContent('Dismissed');
    await userEvent.click(within(card).getByRole('button', { name: 'Restore' }));

    await waitFor(() => expect(card).toHaveTextContent('New'));
    expect(savedAlerts[0]!.status).toBe('NEW');
  });

  it('shows filtered news with each headline and the AIs reason', async () => {
    renderPage();
    expect(await screen.findByText('Checked 42 articles. 37 were not supply-chain events.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Show Filtered News' }));

    expect(await screen.findByText(noise[0]!.assessment!.reason)).toBeInTheDocument();
    expect(screen.getByText(noise[1]!.assessment!.reason)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Open Article' })).toHaveLength(4);
  });

  it('shows the checked empty state and offers Check News', async () => {
    savedAlerts = [];
    renderPage();

    expect(await screen.findByText('No events found in the last 7 days.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Check News' })).toBeInTheDocument();
  });

  it('shows the not-yet-checked empty state and offers Check News', async () => {
    savedAlerts = [];
    checkedPortfolio = { ...portfolio, newsCheckedAt: null };
    renderPage();

    expect(await screen.findByText('News has not been checked yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Check News' })).toBeInTheDocument();
  });
});
