// The workspace, as a portfolio manager sees it: the top bar, the drawer's Holdings and Alerts tabs, the graph and the
// Inspector, and everything the Portfolio, Supply Chain and Alerts pages did before them.
//
// The backend is a small fake that keeps one portfolio in memory and answers like the real one. Its suppliers and
// customers are the real sample in docs/examples/relationships.json, and its alerts are about the real TSMC headlines
// in docs/examples/google-news-tsmc.xml (test/sampleAlerts.ts). The graph draws on a <canvas>, which jsdom cannot draw
// on, so it is replaced with a fake that shows what it was given and has a button per company and per link; what the
// real graph does with it is tested in src/graph/.
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import sampleRelationshipsJson from '../../../docs/examples/relationships.json';
import { App } from '../App';
import type { AlertEvent, ReachedHolding } from '../api/alerts';
import { tokenStore } from '../api/client';
import type { Holding, Portfolio } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import type { GraphFocus } from '../graph/LivingGraph';
import { showInReportUrl } from '../format';
import { sampleAlert, tsmcSuppliesNvidia } from '../test/sampleAlerts';
import { TSMC } from '../test/sampleNews';
import type { GraphEdge, GraphNode } from './supplyChain';

vi.mock('../graph/LivingGraph', () => ({
  LivingGraph: (props: {
    nodes: GraphNode[];
    edges: GraphEdge[];
    onEdgeSelect: (relationshipId: number) => void;
    onNodeSelect?: (nodeId: string | null) => void;
    selectedNodeId?: string | null;
    selectedRelationshipId?: number | null;
    logoToken?: string;
    logoDomains?: Record<string, string>;
    risks?: Map<number, unknown>;
    alertDots?: Map<string, string>;
    focus?: GraphFocus | null;
    peekNodeId?: string | null;
    bare?: boolean;
    insets?: { top: number; right: number; bottom: number; left: number };
    barStart?: ReactNode;
  }) => (
    <div data-testid="fake-graph">
      {props.barStart}
      <span data-testid="bare">{String(!!props.bare)}</span>
      <span data-testid="insets">{JSON.stringify(props.insets)}</span>
      <span data-testid="node-count">{props.nodes.length}</span>
      <span data-testid="selected-node">{props.selectedNodeId ?? ''}</span>
      <span data-testid="selected-relationship">{props.selectedRelationshipId ?? ''}</span>
      <span data-testid="focus-nodes">{props.focus ? [...props.focus.nodeIds].sort().join(',') : ''}</span>
      <span data-testid="focus-links">{props.focus ? [...props.focus.relationshipIds].sort((a, b) => a - b).join(',') : ''}</span>
      <span data-testid="logo-token">{props.logoToken}</span>
      <span data-testid="logo-domains">{JSON.stringify(props.logoDomains ?? {})}</span>
      <span data-testid="risk-links">{[...(props.risks?.keys() ?? [])].sort().join(',')}</span>
      <span data-testid="alert-dots">
        {[...(props.alertDots ?? [])].map(([nodeId, severity]) => `${nodeId}:${severity}`).sort().join(',')}
      </span>
      {props.edges.map((edge) => (
        <button key={edge.id} type="button" onClick={() => props.onEdgeSelect(edge.relationshipId)}>
          {`Link ${edge.relationshipId}`}
        </button>
      ))}
      {props.nodes.map((node) => (
        <button key={node.id} type="button" onClick={() => props.onNodeSelect?.(node.id)}>
          {`Company ${node.id}`}
        </button>
      ))}
      <button type="button" onClick={() => props.onNodeSelect?.(null)}>Graph Background</button>
    </div>
  ),
}));

const sample = sampleRelationshipsJson as Relationship[];
// The ids the sample gives its companies.
const NVIDIA = sample[0]!.company;
const AMD = sample[2]!.company;
const LAM = sample[10]!.company;

/** A holding of one of the sample's companies, analysed, with its annual report. */
function held(company: Relationship['company'], id: number, overrides: Partial<Holding> = {}): Holding {
  const report = sample.find((relationship) => relationship.company.id === company.id)!.report;
  return {
    id,
    ticker: company.ticker!,
    weight: 10,
    companyName: company.name,
    companyDisplayName: company.displayName,
    status: 'DONE',
    statusReason: null,
    reportType: report.form,
    reportDate: report.filingDate,
    reportLink: report.url,
    lastAnalysed: '2026-10-27T09:00:00Z',
    ...overrides,
  };
}

/** A holding not analysed yet: no company, no report. */
function waiting(ticker: string, id: number, overrides: Partial<Holding> = {}): Holding {
  return {
    id,
    ticker,
    weight: 10,
    companyName: null,
    companyDisplayName: null,
    status: 'WAITING',
    statusReason: null,
    reportType: null,
    reportDate: null,
    reportLink: null,
    lastAnalysed: null,
    ...overrides,
  };
}

// ---- The fake backend --------------------------------------------------------------------------------------------

type Backend = {
  me: { email: string; role: 'PORTFOLIO_MANAGER' | 'ANALYST'; displayName: string | null };
  portfolio: Portfolio;
  holdings: Holding[];
  relationships: Relationship[];
  alerts: AlertEvent[];
  weighted: boolean;
  logoToken: string;
  // A status code for the portfolio list to fail with.
  portfoliosFail?: number;
  // What a news check finds, once it has run.
  found?: AlertEvent[];
  // What an analysis leaves each holding as.
  analysed?: (holding: Holding) => Holding;
  sent: { method: string; url: string; body: unknown }[];
};

let backend: Backend;
// What each test file contains. The test browser cannot read a File back, so the fake backend looks it up here.
const contents = new Map<File, string>();

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function answer(url: string, init: RequestInit = {}): Response {
  const method = init.method ?? 'GET';
  const body = typeof init.body === 'string' ? JSON.parse(init.body) : undefined;
  if (method !== 'GET') backend.sent.push({ method, url, body });
  const base = `/api/portfolios/${backend.portfolio.id}`;

  if (url === '/api/auth/me') return json(backend.me);
  if (url === '/api/settings') return json({ logoToken: backend.logoToken });
  if (url.startsWith('/api/logos/domains')) return json({ 'Samsung Electronics Co., Ltd.': 'samsung.example' });
  if (url === '/api/portfolios') {
    if (backend.portfoliosFail) return json({ message: 'The server is restarting.' }, backend.portfoliosFail);
    // A check runs on the backend: listed again, it has finished.
    if (backend.portfolio.newsStatus === 'CHECKING') {
      backend.portfolio = { ...backend.portfolio, newsStatus: 'IDLE', newsCheckedAt: new Date().toISOString() };
      if (backend.found) backend.alerts = backend.found;
    }
    return json([backend.portfolio, { id: 9, name: 'Utilities', createdAt: '', newsStatus: 'IDLE', newsStatusReason: null, newsCheckedAt: null }]);
  }
  if (url === `${base}/news-check` && method === 'POST') {
    backend.portfolio = { ...backend.portfolio, newsStatus: 'CHECKING' };
    return new Response(null, { status: 202 });
  }
  if (url === `${base}/analysis` && method === 'POST') {
    backend.holdings = backend.holdings.map((holding) => ({ ...holding, status: 'ANALYSING' }));
    return new Response(null, { status: 202 });
  }
  if (url === `${base}/holdings/upload`) {
    const file = (init.body as FormData).get('file') as File;
    const [header, ...rows] = contents.get(file)!.trim().split('\n');
    if (!/ticker|symbol/i.test(header!)) return json({ message: 'The file needs a ticker column.' }, 400);
    backend.holdings = rows.map((row, index) => waiting(row.split(',')[0]!, 100 + index));
    return json(backend.holdings);
  }
  if (url === `${base}/holdings` && method === 'POST') {
    const ticker = String(body.ticker).toUpperCase();
    if (backend.holdings.some((holding) => holding.ticker === ticker)) {
      return json({ message: `${ticker} is already in this portfolio.` }, 409);
    }
    const added = waiting(ticker, 200 + backend.holdings.length, { weight: body.weight });
    // The real backend starts an added holding's analysis at once: read again, it is Analysing.
    backend.holdings = [...backend.holdings, { ...added, status: 'ANALYSING' }];
    return json(added, 201);
  }
  const one = new RegExp(`^${base}/holdings/(\\d+)$`).exec(url);
  if (one && method === 'PUT') {
    if (body.weight !== null && (body.weight < 0 || body.weight > 100)) {
      return json({ message: 'The weight must be between 0 and 100.' }, 400);
    }
    backend.holdings = backend.holdings.map((holding) =>
      holding.id === Number(one[1]) ? { ...holding, weight: body.weight } : holding,
    );
    return json(backend.holdings.find((holding) => holding.id === Number(one[1])));
  }
  if (one && method === 'DELETE') {
    backend.holdings = backend.holdings.filter((holding) => holding.id !== Number(one[1]));
    return new Response(null, { status: 204 });
  }
  if (url === `${base}/holdings`) {
    // Reading the holdings again while they are analysed: the analysis has finished.
    const shown = backend.holdings;
    if (backend.analysed && shown.some((holding) => holding.status === 'ANALYSING')) {
      backend.holdings = shown.map((holding) => (holding.status === 'ANALYSING' ? backend.analysed!(holding) : holding));
    }
    return json(shown);
  }
  if (url === `${base}/relationships`) return json(backend.relationships);
  if (url === `${base}/alerts/status` && method === 'PUT') {
    backend.alerts = backend.alerts.map((alert) =>
      alert.alertIds.some((id) => body.alertIds.includes(id)) ? { ...alert, status: body.status } : alert,
    );
    return json(backend.alerts.find((alert) => alert.alertIds.some((id) => body.alertIds.includes(id))));
  }
  if (url.startsWith(`${base}/alerts`)) {
    return json({ weighted: backend.weighted, holdingCount: backend.holdings.length, alerts: backend.alerts });
  }
  if (url.startsWith('/api/portfolios/')) return json({ message: 'Portfolio not found.' }, 404);
  return json([]);
}

/** Where the app is, so a test can check the address. */
function Address() {
  const { pathname, search } = useLocation();
  return <output aria-label="Address">{pathname + search}</output>;
}

function renderAt(path = '/portfolios/7') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <Address />
    </MemoryRouter>,
  );
}

function csv(text: string) {
  const file = new File([text], 'holdings.csv', { type: 'text/csv' });
  contents.set(file, text);
  return file;
}

const inspector = () => screen.getByRole('complementary', { name: 'Inspector' });
const drawer = () => screen.getByRole('complementary', { name: 'Portfolio' });
/** The graph, once the portfolio has loaded and the drawer shows it rather than its loading rows. */
async function graph() {
  const shown = await screen.findByTestId('fake-graph');
  await waitFor(() => expect(within(drawer()).queryByLabelText('Loading')).not.toBeInTheDocument());
  return shown;
}
const row = (name: string) => within(drawer()).getByRole('listitem', { name });
const insets = () => JSON.parse(screen.getByTestId('insets').textContent!) as Record<string, number>;
// The toast saying what just happened (the address readout below the app is a status too).
const toast = () => screen.getAllByRole('status').find((element) => element.classList.contains('toast'))!;
const sentTo = (method: string, url: string) => backend.sent.filter((call) => call.method === method && call.url === url);

beforeEach(() => {
  tokenStore.set('a-token');
  // No drawing in jsdom: tiles and links have no canvas to draw on.
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
  backend = {
    me: { email: 'pm@example.com', role: 'PORTFOLIO_MANAGER', displayName: null },
    portfolio: {
      id: 7,
      name: 'Semiconductors',
      createdAt: '2026-10-01T00:00:00Z',
      newsStatus: 'IDLE',
      newsStatusReason: null,
      newsCheckedAt: new Date(Date.now() - 3 * 3_600_000).toISOString(),
    },
    holdings: [held(AMD, 2), held(LAM, 6), held(NVIDIA, 1, { weight: 18 })],
    relationships: sample.filter((relationship) => [1, 2].includes(relationship.company.id) || relationship.id === 14),
    alerts: [],
    weighted: true,
    logoToken: '',
    sent: [],
  };
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => answer(url, init)));
});

afterEach(() => {
  vi.restoreAllMocks();
  tokenStore.clear();
});

// ---- 1. The workspace ---------------------------------------------------------------------------------------------

describe('the workspace', () => {
  it('opensAPortfolioOnTheTopBarTheDrawerAndTheGraphFillingTheScreen', async () => {
    renderAt();
    await graph();

    expect(screen.getByRole('button', { name: 'Portfolio: Semiconductors' })).toHaveTextContent('3 of 3 Analysed');
    for (const action of ['Upload CSV', 'Check News', 'Analyse']) {
      expect(screen.getByRole('button', { name: action })).toBeInTheDocument();
    }
    expect(within(drawer()).getByRole('tab', { name: 'Holdings' })).toHaveAttribute('aria-selected', 'true');
    expect(within(drawer()).getByRole('tab', { name: 'Alerts' })).toHaveAttribute('aria-selected', 'false');
    // The graph is the ground, edge to edge, told how much of each side the panels cover.
    expect(screen.getByTestId('bare')).toHaveTextContent('true');
    expect(insets()).toEqual({ top: 76, right: 12, bottom: 12, left: 404 });
  });

  it('opensTheInspectorOnlyOnceSomethingIsChosenAndTheGraphKeepsClearOfIt', async () => {
    renderAt();
    await graph();
    expect(screen.queryByRole('complementary', { name: 'Inspector' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: `Company ${NVIDIA.id}` }));

    expect(within(inspector()).getByRole('heading', { name: 'NVIDIA' })).toBeInTheDocument();
    expect(insets().right).toBe(444);
  });

  it('letsGoOfTheChoiceWhenTheGraphsBackgroundIsClicked', async () => {
    renderAt();
    await userEvent.click(await screen.findByRole('button', { name: `Company ${NVIDIA.id}` }));

    await userEvent.click(screen.getByRole('button', { name: 'Graph Background' }));

    await waitFor(() => expect(screen.queryByRole('complementary', { name: 'Inspector' })).not.toBeInTheDocument());
  });

  it('namesEachCompanyByTheDisplayNameTheApiSends', async () => {
    renderAt();
    await graph();

    expect(row('NVIDIA (NVDA)')).toBeInTheDocument();
    expect(screen.queryByText(/NVIDIA Corporation/)).not.toBeInTheDocument();
  });
});

// ---- 2. Everything the Portfolio page did ------------------------------------------------------------------------

describe('holdings', () => {
  it('showsEachHoldingsStatusAndWeightAndItsReportAndShowReportInTheInspector', async () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    renderAt();
    await graph();

    const nvidia = row('NVIDIA (NVDA)');
    expect(nvidia).toHaveTextContent('18%');
    expect(nvidia).toHaveTextContent(/2\s*Suppliers/);
    await userEvent.click(nvidia);

    expect(inspector()).toHaveTextContent('Status');
    expect(within(inspector()).getByText('Done')).toBeInTheDocument();
    expect(inspector()).toHaveTextContent('10-K · Filed Feb 25, 2026');
    await userEvent.click(within(inspector()).getByRole('button', { name: 'Show Report' }));
    // The report itself, at the top: no text fragment.
    expect(open).toHaveBeenCalledWith(sample[0]!.report.url, '_blank', 'noopener,noreferrer');
  });

  it('showsAHoldingNotOnTheGraphWithItsStatusAndNoShowReport', async () => {
    backend.holdings = [...backend.holdings, waiting('NEW', 30)];
    renderAt();
    await graph();

    const added = row('NEW (NEW)');
    expect(added).toHaveTextContent('Waiting');
    await userEvent.click(added);

    expect(within(inspector()).getByRole('heading', { name: 'NEW' })).toBeInTheDocument();
    expect(within(inspector()).getByRole('button', { name: 'Edit Weight' })).toBeInTheDocument();
    expect(within(inspector()).queryByRole('button', { name: 'Show Report' })).not.toBeInTheDocument();
  });

  it('showsWhyAHoldingFailedAndKeepsAnalyseAvailableToTryAgain', async () => {
    backend.holdings = [held(NVIDIA, 1), waiting('ACME', 2, { status: 'FAILED', statusReason: 'The SEC website did not answer.' })];
    renderAt();
    await graph();

    expect(screen.getByText('Some holdings failed. Press Analyse to try them again.')).toBeInTheDocument();
    await userEvent.click(row('ACME (ACME)'));
    expect(within(inspector()).getByText('The SEC website did not answer.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Analyse' })).toBeEnabled();
  });

  it('addsAHoldingThatShowsAnalysingThenItsResult', async () => {
    backend.analysed = (holding) => ({ ...holding, status: 'NO_REPORT_FOUND' });
    renderAt();
    await graph();

    await userEvent.click(within(drawer()).getByRole('button', { name: 'Add Holding' }));
    await userEvent.type(screen.getByLabelText('Ticker'), 'msft');
    await userEvent.type(screen.getByLabelText('Weight (%)'), '12.5%');
    await userEvent.click(within(drawer()).getAllByRole('button', { name: 'Add Holding' }).at(-1)!);

    await waitFor(() => expect(row('MSFT (MSFT)')).toHaveTextContent('Analysing'));
    expect(sentTo('POST', '/api/portfolios/7/holdings')[0]!.body).toEqual({ ticker: 'msft', weight: 12.5 });
    await waitFor(() => expect(row('MSFT (MSFT)')).toHaveTextContent('No Report Found'), { timeout: 4000 });
  });

  it('addsAHoldingWithNoWeight', async () => {
    renderAt();
    await graph();

    await userEvent.click(within(drawer()).getByRole('button', { name: 'Add Holding' }));
    await userEvent.type(screen.getByLabelText('Ticker'), 'MSFT{Enter}');

    await screen.findByRole('listitem', { name: 'MSFT (MSFT)' });
    expect(sentTo('POST', '/api/portfolios/7/holdings')[0]!.body).toEqual({ ticker: 'MSFT', weight: null });
  });

  it('showsTheBackendsMessageWhenTheTickerIsAlreadyInThePortfolio', async () => {
    renderAt();
    await graph();

    await userEvent.click(within(drawer()).getByRole('button', { name: 'Add Holding' }));
    await userEvent.type(screen.getByLabelText('Ticker'), 'NVDA{Enter}');

    expect(await screen.findByText('NVDA is already in this portfolio.')).toBeInTheDocument();
  });

  it('doesNotSendAWeightThatIsNotANumber', async () => {
    renderAt();
    await graph();

    await userEvent.click(within(drawer()).getByRole('button', { name: 'Add Holding' }));
    await userEvent.type(screen.getByLabelText('Ticker'), 'MSFT');
    await userEvent.type(screen.getByLabelText('Weight (%)'), 'lots{Enter}');

    expect(await screen.findByText('The weight must be a number between 0 and 100.')).toBeInTheDocument();
    expect(sentTo('POST', '/api/portfolios/7/holdings')).toHaveLength(0);
  });

  it('editsAWeightInPlaceAcceptingAPercentSignAndUndoPutsItBack', async () => {
    renderAt();
    await graph();
    await userEvent.click(row('NVIDIA (NVDA)'));

    await userEvent.click(within(inspector()).getByRole('button', { name: 'Edit Weight' }));
    const box = within(inspector()).getByLabelText('Weight for NVDA');
    await userEvent.clear(box);
    await userEvent.type(box, '22%{Enter}');

    await waitFor(() => expect(row('NVIDIA (NVDA)')).toHaveTextContent('22%'));
    expect(sentTo('PUT', '/api/portfolios/7/holdings/1')[0]!.body).toEqual({ weight: 22 });
    await userEvent.click(within(toast()).getByRole('button', { name: 'Undo' }));
    await waitFor(() => expect(row('NVIDIA (NVDA)')).toHaveTextContent('18%'));
  });

  it('savesABlankWeightAsNoWeight', async () => {
    renderAt();
    await graph();
    await userEvent.click(row('NVIDIA (NVDA)'));

    await userEvent.click(within(inspector()).getByRole('button', { name: 'Edit Weight' }));
    await userEvent.clear(within(inspector()).getByLabelText('Weight for NVDA'));
    await userEvent.click(within(inspector()).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(row('NVIDIA (NVDA)')).toHaveTextContent('—'));
    expect(sentTo('PUT', '/api/portfolios/7/holdings/1')[0]!.body).toEqual({ weight: null });
  });

  it('showsTheBackendsMessageForAWeightOutside0To100AndCancelKeepsTheOldWeight', async () => {
    renderAt();
    await graph();
    await userEvent.click(row('NVIDIA (NVDA)'));

    await userEvent.click(within(inspector()).getByRole('button', { name: 'Edit Weight' }));
    const box = within(inspector()).getByLabelText('Weight for NVDA');
    await userEvent.clear(box);
    await userEvent.type(box, '140{Enter}');
    expect(await within(inspector()).findByText('The weight must be between 0 and 100.')).toBeInTheDocument();

    await userEvent.click(within(inspector()).getByRole('button', { name: 'Cancel' }));
    expect(row('NVIDIA (NVDA)')).toHaveTextContent('18%');
  });

  it('asksBeforeRemovingAHoldingAndCancelKeepsIt', async () => {
    renderAt();
    await graph();
    await userEvent.click(row('NVIDIA (NVDA)'));

    await userEvent.click(within(inspector()).getByRole('button', { name: 'Remove' }));
    const ask = screen.getByRole('alertdialog', { name: 'Remove Holding' });
    expect(ask).toHaveAccessibleDescription('This removes NVDA from Semiconductors, and from its alerts.');
    await userEvent.click(within(ask).getByRole('button', { name: 'Cancel' }));
    expect(row('NVIDIA (NVDA)')).toBeInTheDocument();
    expect(sentTo('DELETE', '/api/portfolios/7/holdings/1')).toHaveLength(0);

    await userEvent.click(within(inspector()).getByRole('button', { name: 'Remove' }));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Remove Holding' }));

    await waitFor(() => expect(within(drawer()).queryByRole('listitem', { name: 'NVIDIA (NVDA)' })).not.toBeInTheDocument());
    expect(sentTo('DELETE', '/api/portfolios/7/holdings/1')).toHaveLength(1);
  });
});

describe('Upload CSV', () => {
  it('uploadsIntoAnEmptyPortfolioWithoutAskingFirst', async () => {
    backend.holdings = [];
    backend.relationships = [];
    renderAt();

    await userEvent.click((await screen.findAllByRole('button', { name: 'Upload CSV' }))[0]!);
    await userEvent.upload(screen.getByLabelText('CSV File'), csv('ticker,weight\nAAPL,60\nMSFT,40'));

    expect(await screen.findByRole('listitem', { name: 'AAPL (AAPL)' })).toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(toast()).toHaveTextContent('Uploaded 2 Holdings');
  });

  it('asksBeforeReplacingHoldingsAndCancelKeepsTheOldOnes', async () => {
    renderAt();
    await graph();
    await userEvent.click(screen.getByRole('button', { name: 'Upload CSV' }));

    await userEvent.upload(screen.getByLabelText('CSV File'), csv('ticker\nAAPL'));
    const ask = screen.getByRole('alertdialog', { name: 'Replace Holdings' });
    expect(ask).toHaveAccessibleDescription('This replaces the 3 holdings in Semiconductors.');
    await userEvent.click(within(ask).getByRole('button', { name: 'Cancel' }));
    expect(row('NVIDIA (NVDA)')).toBeInTheDocument();
    expect(sentTo('POST', '/api/portfolios/7/holdings/upload')).toHaveLength(0);

    await userEvent.upload(screen.getByLabelText('CSV File'), csv('ticker\nAAPL'));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Replace Holdings' }));

    expect(await screen.findByRole('listitem', { name: 'AAPL (AAPL)' })).toBeInTheDocument();
    await waitFor(() => expect(within(drawer()).queryByRole('listitem', { name: 'NVIDIA (NVDA)' })).not.toBeInTheDocument());
  });

  it('showsTheBackendsReasonWhenAFileIsRefused', async () => {
    backend.holdings = [];
    backend.relationships = [];
    renderAt();

    await userEvent.click((await screen.findAllByRole('button', { name: 'Upload CSV' }))[0]!);
    await userEvent.upload(screen.getByLabelText('CSV File'), csv('name\nApple'));

    const window = screen.getByRole('dialog', { name: 'Upload a Portfolio CSV' });
    expect(await within(window).findByText('The file needs a ticker column.')).toBeInTheDocument();
  });
});

describe('Analyse', () => {
  it('startsAnalysisForThePortfolioInTheAddressAndTheRingShowsItRunning', async () => {
    renderAt();
    await graph();

    await userEvent.click(screen.getByRole('button', { name: 'Analyse' }));

    expect(sentTo('POST', '/api/portfolios/7/analysis')).toHaveLength(1);
    expect(await screen.findByRole('button', { name: 'Portfolio: Semiconductors' })).toHaveTextContent('Analysing · 0 of 3 Done');
    expect(screen.getByRole('button', { name: 'Analysing' })).toBeDisabled();
  });

  it('loadsTheSuppliersAndCustomersAgainWhenTheAnalysisFinishes', async () => {
    backend.analysed = (holding) => ({ ...holding, status: 'DONE' });
    renderAt();
    await graph();
    const relationshipReads = () => vi.mocked(fetch).mock.calls.filter(([url]) => url === '/api/portfolios/7/relationships').length;
    const before = relationshipReads();

    await userEvent.click(screen.getByRole('button', { name: 'Analyse' }));

    await waitFor(() => expect(toast()).toHaveTextContent('3 Holdings Analysed'), { timeout: 4000 });
    expect(relationshipReads()).toBe(before + 1);
  });

  it('offersAnalyseWhenThePortfolioHasHoldingsButNoSuppliersOrCustomersYet', async () => {
    backend.holdings = [waiting('NVDA', 1), waiting('AMD', 2)];
    backend.relationships = [];
    renderAt();

    expect(await screen.findByRole('heading', { name: 'No Supply Chain Yet' })).toBeInTheDocument();
    expect(screen.queryByTestId('fake-graph')).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Analyse' })).toHaveLength(2);
  });
});

describe('Check News', () => {
  it('startsACheckShowsItRunningThenShowsTheAlertsItFound', async () => {
    backend.found = [sampleAlert({ holdings: [tsmcSuppliesNvidia(1, 1)] })];
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    await userEvent.click(screen.getByRole('button', { name: 'Check News' }));

    expect(sentTo('POST', '/api/portfolios/7/news-check')).toHaveLength(1);
    expect(await screen.findByRole('button', { name: 'Checking News' })).toBeDisabled();
    await waitFor(() => expect(toast()).toHaveTextContent('News Checked · 1 Open Alert'), { timeout: 4000 });
    expect(within(drawer()).getByRole('listitem', { name: backend.found[0]!.headline.article.title })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '1 Open Alert' })).toBeInTheDocument();
  });

  it('saysWhyNewsCouldNotBeChecked', async () => {
    backend.portfolio = { ...backend.portfolio, newsStatus: 'FAILED', newsStatusReason: 'Google News did not answer.' };
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    expect(await within(drawer()).findByText('Google News did not answer.')).toBeInTheDocument();
  });
});

// ---- 3. The Alerts tab ---------------------------------------------------------------------------------------------

describe('the Alerts tab', () => {
  const open = () => sampleAlert({ id: 1, headline: 0, holdings: [tsmcSuppliesNvidia(1, 1)] });
  const closed = () => sampleAlert({ id: 2, headline: 1, severity: 'LOW', status: 'DISMISSED', holdings: [tsmcSuppliesNvidia(1, 1)] });

  it('listsTheAlertsWithOpenClosedAndAllAndTheirCounts', async () => {
    backend.alerts = [open(), closed()];
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    const cards = () => within(drawer()).queryAllByRole('listitem').map((card) => card.getAttribute('aria-label'));
    expect(within(drawer()).getByRole('tab', { name: 'Open (1)' })).toHaveAttribute('aria-selected', 'true');
    expect(cards()).toEqual([open().headline.article.title]);

    await userEvent.click(within(drawer()).getByRole('tab', { name: 'Closed (1)' }));
    await waitFor(() => expect(cards()).toEqual([closed().headline.article.title]));

    await userEvent.click(within(drawer()).getByRole('tab', { name: 'All (2)' }));
    await waitFor(() => expect(cards()).toHaveLength(2));
  });

  it('showsEachAlertsSeverityTypeAgeHeadlineTheHoldingsItReachesAndTheShareOfThePortfolio', async () => {
    backend.alerts = [{ ...open(), exposure: 18 }];
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    const card = within(drawer()).getByRole('listitem', { name: open().headline.article.title });
    expect(card).toHaveTextContent('High');
    expect(card).toHaveTextContent('Production Stop');
    expect(card).toHaveTextContent('18%');
    expect(card).toHaveTextContent('of Portfolio');
    expect(within(card).getByTitle('NVDA · Reached Directly')).toBeInTheDocument();
  });

  it('countsTheHoldingsReachedInAPortfolioWithoutWeights', async () => {
    backend.weighted = false;
    backend.alerts = [{ ...open(), exposure: 1 }];
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    expect(within(drawer()).getByRole('listitem', { name: open().headline.article.title })).toHaveTextContent('1of 3 Holdings');
  });

  it('closesAnAlertAndUndoPutsItBack', async () => {
    backend.alerts = [open()];
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    await userEvent.click(within(drawer()).getByRole('button', { name: 'Close Alert' }));

    expect(await within(drawer()).findByText('No Open Alerts')).toBeInTheDocument();
    expect(sentTo('PUT', '/api/portfolios/7/alerts/status')[0]!.body).toEqual({ alertIds: [1], status: 'DISMISSED' });
    await userEvent.click(within(toast()).getByRole('button', { name: 'Undo' }));
    expect(await within(drawer()).findByRole('listitem', { name: open().headline.article.title })).toBeInTheDocument();
    expect(sentTo('PUT', '/api/portfolios/7/alerts/status')[1]!.body).toEqual({ alertIds: [1], status: 'NEW' });
  });

  it('restoresAClosedAlertWithUndo', async () => {
    backend.alerts = [closed()];
    renderAt('/portfolios/7?tab=alerts');
    await graph();
    await userEvent.click(within(drawer()).getByRole('tab', { name: 'Closed (1)' }));

    await userEvent.click(await within(drawer()).findByRole('button', { name: 'Restore' }));

    expect(await within(drawer()).findByRole('tab', { name: 'Open (1)' })).toBeInTheDocument();
    expect(toast()).toHaveTextContent('Alert Restored');
    await userEvent.click(within(toast()).getByRole('button', { name: 'Undo' }));
    expect(await within(drawer()).findByRole('tab', { name: 'Closed (1)' })).toBeInTheDocument();
  });

  it('saysNewsHasNotBeenCheckedYetAndOffersCheckNews', async () => {
    backend.portfolio = { ...backend.portfolio, newsCheckedAt: null };
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    expect(within(drawer()).getAllByText('News Not Checked Yet').length).toBeGreaterThan(0);
    await userEvent.click(within(drawer()).getByRole('button', { name: 'Check News' }));
    expect(sentTo('POST', '/api/portfolios/7/news-check')).toHaveLength(1);
  });

  it('saysThereAreNoOpenAlertsWhenEveryAlertIsClosed', async () => {
    backend.alerts = [closed()];
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    expect(within(drawer()).getByText('No Open Alerts')).toBeInTheDocument();
  });

  it('keepsAPortfolioLeftBehindsAlertsOutOfTheOneSwitchedTo', async () => {
    backend.alerts = [open()];
    // Semiconductors' alerts are slow to arrive; Utilities holds AMD and has no alerts.
    let release = () => {};
    const late = new Promise<void>((resolve) => (release = resolve));
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      if (url.startsWith('/api/portfolios/7/alerts')) {
        await late;
        return answer(url, init);
      }
      if (url === '/api/portfolios/9/holdings') return json([held(AMD, 30)]);
      if (url === '/api/portfolios/9/relationships') return json([]);
      if (url.startsWith('/api/portfolios/9/alerts')) return json({ weighted: true, holdingCount: 1, alerts: [] });
      return answer(url, init);
    }));
    renderAt('/portfolios/7?tab=alerts');

    await userEvent.click(await screen.findByRole('button', { name: 'Portfolio: Semiconductors' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: /Utilities/ }));
    await screen.findByRole('button', { name: 'Portfolio: Utilities' });
    await waitFor(() => expect(within(drawer()).getAllByText('News Not Checked Yet')[0]).toBeInTheDocument());
    release();

    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(within(drawer()).getAllByText('News Not Checked Yet')[0]).toBeInTheDocument();
    expect(within(drawer()).queryByRole('listitem', { name: open().headline.article.title })).not.toBeInTheDocument();
  });

  it('putsTheOpenTabInTheAddress', async () => {
    renderAt();
    await graph();

    await userEvent.click(within(drawer()).getByRole('tab', { name: 'Alerts' }));
    expect(screen.getByLabelText('Address')).toHaveTextContent('/portfolios/7?tab=alerts');
    await userEvent.click(within(drawer()).getByRole('tab', { name: 'Holdings' }));
    expect(screen.getByLabelText('Address')).toHaveTextContent(/^\/portfolios\/7$/);
  });
});

// ---- 4. A chosen alert, in the Inspector and spotlit on the graph --------------------------------------------------

describe('a chosen alert', () => {
  it('showsInTheInspectorAndSpotlightsItsCompanyHoldingsAndLinks', async () => {
    const alert = { ...sampleAlert({ holdings: [tsmcSuppliesNvidia(1, 1)] }), recommendation: 'Check the supplier delivery updates.' };
    backend.alerts = [alert];
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    await userEvent.click(within(drawer()).getByRole('button', { name: alert.headline.article.title }));

    expect(within(inspector()).getByRole('heading', { name: alert.headline.article.title })).toBeInTheDocument();
    expect(within(inspector()).getByText('Why It Matters')).toBeInTheDocument();
    expect(within(inspector()).getByText('The headline reports a production stoppage.')).toBeInTheDocument();
    expect(within(inspector()).getByText('Check the supplier delivery updates.')).toBeInTheDocument();
    // TSMC (11), the company it is about, and NVIDIA (1), the holding it reaches, by the link between them.
    expect(screen.getByTestId('focus-nodes')).toHaveTextContent(/^1,11$/);
    expect(screen.getByTestId('focus-links')).toHaveTextContent(/^1$/);
  });

  it('spotlightsBothLinksOfAHoldingReachedInTwoSteps', async () => {
    // Lam Research supplies TSMC (relationship 14), which supplies NVIDIA (relationship 1).
    const twoSteps: ReachedHolding = {
      ...tsmcSuppliesNvidia(1, 14),
      indirect: true,
      supplierName: 'Lam Research',
      middleName: TSMC.displayName,
      customerName: 'NVIDIA',
      secondRelationshipId: 1,
    };
    const alert = sampleAlert({ companyName: LAM.name, holdings: [twoSteps] });
    backend.alerts = [alert];
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    await userEvent.click(within(drawer()).getByRole('button', { name: alert.headline.article.title }));

    expect(screen.getByTestId('focus-links')).toHaveTextContent(/^1,14$/);
    expect(screen.getByTestId('focus-nodes')).toHaveTextContent(/^1,6$/);
    const path = within(inspector()).getByRole('region', { name: 'How It Reaches You' });
    expect(path).toHaveTextContent('Indirect');
    expect(path).toHaveTextContent(`Lam Research${TSMC.displayName}NVIDIA`);
  });

  // As the backend's RelationshipConfidence works it out for the path; the website only shows it (See How Confident
  // TrueSight Is). An alert about the holding itself reaches it through no relationship, so it has no tag.
  it('tagsEachPathWithHowConfidentTrueSightIsAndAHoldingNamedInTheArticleWithNone', async () => {
    const named: ReachedHolding = { ...tsmcSuppliesNvidia(1, 1), supplierName: null, customerName: null, relationshipId: null, confidence: null };
    const reaching: ReachedHolding = {
      ...tsmcSuppliesNvidia(1, 1),
      confidence: {
        level: 'HIGH',
        reasons: [
          { text: 'An Analyst Approved It', effect: 'RAISES' },
          { text: 'Quote Match Not Recorded', effect: 'NONE' },
          { text: 'Not Yet Second-Checked', effect: 'NONE' },
        ],
      },
    };
    const alert = sampleAlert({ holdings: [named, reaching] });
    backend.alerts = [alert];
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    await userEvent.click(within(drawer()).getByRole('button', { name: alert.headline.article.title }));

    const path = within(inspector()).getByRole('region', { name: 'How It Reaches You' });
    expect(within(path).getByText('High Confidence')).toHaveClass('confidence-high');
    expect(within(path).getByText('Named in the Article').closest('button')).not.toHaveTextContent(/Confidence/);
  });

  it('saysOnThePathsOwnLineWhenItsLinkIsNotYetReviewed', async () => {
    const alert = sampleAlert({ holdings: [tsmcSuppliesNvidia(1, 1)] });
    backend.alerts = [alert];
    renderAt('/portfolios/7?tab=alerts');
    await graph();

    await userEvent.click(within(drawer()).getByRole('button', { name: alert.headline.article.title }));

    const path = within(inspector()).getByRole('region', { name: 'How It Reaches You' });
    expect(within(path).getByText('Not Yet Reviewed')).toHaveClass('path-unreviewed');
  });

  it('putsTheMostSeriousOpenAlertFirstOnTheHoldingsTabAndThePillOpensTheAlertsTab', async () => {
    const alert = sampleAlert({ holdings: [tsmcSuppliesNvidia(1, 1)] });
    backend.alerts = [alert];
    renderAt();
    await graph();

    await userEvent.click(screen.getByRole('button', { name: '1 Open Alert' }));
    expect(within(drawer()).getByRole('tab', { name: 'Alerts' })).toHaveAttribute('aria-selected', 'true');

    await userEvent.click(within(drawer()).getByRole('tab', { name: 'Holdings' }));
    await userEvent.click(await within(drawer()).findByRole('button', { name: /^High · Production Stop/ }));
    expect(within(inspector()).getByRole('heading', { name: alert.headline.article.title })).toBeInTheDocument();
  });

  it('closesFromTheInspectorToo', async () => {
    const alert = sampleAlert({ holdings: [tsmcSuppliesNvidia(1, 1)] });
    backend.alerts = [alert];
    renderAt('/portfolios/7?tab=alerts');
    await graph();
    await userEvent.click(within(drawer()).getByRole('button', { name: alert.headline.article.title }));

    await userEvent.click(within(inspector()).getByRole('button', { name: 'Close Alert' }));

    expect(await within(drawer()).findByText('No Open Alerts')).toBeInTheDocument();
  });
});

// ---- The Inspector ---------------------------------------------------------------------------------------------------

describe('the Inspector', () => {
  it('listsACompanysOpenAlertsAndAHoldingsAlertsThatReachItThroughASupplier', async () => {
    const alert = sampleAlert({ holdings: [tsmcSuppliesNvidia(1, 1)] });
    const dismissed = sampleAlert({ id: 3, headline: 2, status: 'DISMISSED', holdings: [tsmcSuppliesNvidia(1, 1)] });
    backend.alerts = [alert, dismissed];
    renderAt();
    await graph();

    await userEvent.click(screen.getByRole('button', { name: `Company ${TSMC.id}` }));
    const aboutTsmc = within(inspector()).getByRole('region', { name: 'Open Alerts' });
    expect(within(aboutTsmc).getAllByRole('button')).toHaveLength(1);
    expect(aboutTsmc).toHaveTextContent(alert.headline.article.title);

    await userEvent.click(row('NVIDIA (NVDA)'));
    expect(within(inspector()).getByRole('region', { name: 'Open Alerts' })).toHaveTextContent(alert.headline.article.title);

    await userEvent.click(row('Advanced Micro Devices (AMD)'));
    expect(within(inspector()).queryByRole('region', { name: 'Open Alerts' })).not.toBeInTheDocument();
  });

  it('showsACompanysSuppliersAndCustomersAndOpensOnesRelationship', async () => {
    renderAt();
    await graph();
    await userEvent.click(screen.getByRole('button', { name: `Company ${NVIDIA.id}` }));

    const suppliers = within(inspector()).getByRole('region', { name: 'Suppliers' });
    expect(suppliers).toHaveTextContent('Taiwan Semiconductor Manufacturing');
    expect(suppliers).toHaveTextContent('Semiconductor Wafers');
    await userEvent.click(within(suppliers).getAllByRole('button')[0]!);

    expect(screen.getByTestId('selected-relationship')).toHaveTextContent('1');
    expect(within(inspector()).getByText(sample[0]!.evidence)).toBeInTheDocument();
  });

  it('showsARelationshipsEvidenceReportAndShowInReport', async () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    renderAt();
    await graph();

    await userEvent.click(screen.getByRole('button', { name: 'Link 1' }));

    expect(within(inspector()).getByRole('heading', { name: 'Taiwan Semiconductor Manufacturing Supplies NVIDIA' })).toBeInTheDocument();
    expect(inspector()).toHaveTextContent('From NVIDIA’s 10-K, Filed Feb 25, 2026');
    await userEvent.click(within(inspector()).getByRole('button', { name: 'Show in Report' }));
    expect(open).toHaveBeenCalledWith(showInReportUrl(sample[0]!.report.url, sample[0]!.evidence), '_blank', 'noopener,noreferrer');
  });

  it('showsCriticalityEvidenceAndOpensTheReportAtThatSentence', async () => {
    const source = sample[15]!;
    backend.relationships = [{ ...source, criticality: 'SOLE_SOURCE', criticalityEvidence: source.evidence }];
    backend.holdings = [held(source.company, 16)];
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    renderAt();
    await graph();

    await userEvent.click(screen.getByRole('button', { name: `Link ${source.id}` }));

    const critical = within(inspector()).getByRole('region', { name: 'How Critical' });
    expect(critical).toHaveTextContent('Sole Source');
    await userEvent.click(within(critical).getByRole('button', { name: 'Show Criticality in Report' }));
    expect(open).toHaveBeenCalledWith(showInReportUrl(source.report.url, source.evidence), '_blank', 'noopener,noreferrer');
  });

  it('saysWhetherAnAnalystApprovedAddedOrHasNotReviewedARelationship', async () => {
    backend.relationships = [
      { ...sample[0]!, reviewStatus: 'APPROVED', reviewedAt: '2026-10-14T12:00:00Z', reviewNote: 'Confirmed against the annual report.' },
      { ...sample[1]!, source: 'ANALYST', reviewStatus: 'APPROVED' },
      sample[2]!,
    ];
    renderAt();
    await graph();

    await userEvent.click(screen.getByRole('button', { name: 'Link 1' }));
    expect(within(inspector()).getByText('Approved by an Analyst on Oct 14, 2026')).toBeInTheDocument();
    expect(within(inspector()).getByText('Confirmed against the annual report.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Link 2' }));
    expect(within(inspector()).getByText('Added by an Analyst')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Link 3' }));
    expect(within(inspector()).getByText('Not Yet Reviewed by an Analyst')).toBeInTheDocument();
  });

  // As the backend's RelationshipConfidence works them out; the website only shows them.
  it('showsARelationshipsConfidenceWithEachReasonAndItsEffect', async () => {
    backend.relationships = [
      {
        ...sample[0]!,
        reviewStatus: 'APPROVED',
        confidence: {
          level: 'HIGH',
          reasons: [
            { text: 'An Analyst Approved It', effect: 'RAISES' },
            { text: 'Quote Match Not Recorded', effect: 'NONE' },
            { text: 'Second Check Agrees', effect: 'NONE' },
          ],
        },
      },
    ];
    renderAt();
    await graph();

    await userEvent.click(screen.getByRole('button', { name: 'Link 1' }));

    const confidence = within(inspector()).getByRole('region', { name: 'Confidence' });
    expect(within(confidence).getByText('High Confidence')).toHaveClass('confidence-high');
    expect(within(confidence).getAllByRole('listitem').map((reason) => reason.textContent)).toEqual([
      'An Analyst Approved ItRaises Confidence',
      'Quote Match Not RecordedNo Effect',
      'Second Check AgreesNo Effect',
    ]);
  });

  it('showsADoubtedRelationshipAsLowConfidenceWithTheDoubtLoweringIt', async () => {
    backend.relationships = [
      {
        ...sample[0]!,
        confidence: {
          level: 'LOW',
          reasons: [
            { text: 'Not Yet Reviewed', effect: 'NONE' },
            { text: 'Strong Quote Match (100%)', effect: 'NONE' },
            { text: 'Second Check Doubts', effect: 'LOWERS' },
          ],
        },
      },
    ];
    renderAt();
    await graph();

    await userEvent.click(screen.getByRole('button', { name: 'Link 1' }));

    expect(within(inspector()).getByText('Low Confidence')).toHaveClass('confidence-low');
    expect(within(inspector()).getByText('Second Check Doubts').closest('li')).toHaveTextContent('Lowers Confidence');
  });

  it('namesASecondChecksDoubtWithoutTheExplanationTheAnalystReviews', async () => {
    // The doubt as the backend wrote it for ASML's TSMC relationship on the demo portfolio (28 October 2026).
    const doubt =
      'Second Check Doubts: The sentence mentions receiving a supplier award from TSMC for energy-efficiency ' +
      'innovations, but it does not state that ASML sells product energy-efficiency innovations to TSMC.';
    backend.relationships = [
      {
        ...sample[0]!,
        confidence: {
          level: 'LOW',
          reasons: [
            { text: 'Not Yet Reviewed', effect: 'NONE' },
            { text: 'Quote Match Not Recorded', effect: 'NONE' },
            { text: doubt, effect: 'LOWERS' },
          ],
        },
      },
    ];
    renderAt();
    await graph();

    await userEvent.click(screen.getByRole('button', { name: 'Link 1' }));

    const confidence = within(inspector()).getByRole('region', { name: 'Confidence' });
    expect(within(confidence).getByText('Second Check Doubts')).toBeInTheDocument();
    expect(confidence).not.toHaveTextContent(/supplier award/);
  });
});

// ---- The graph's marks and logos -------------------------------------------------------------------------------------

describe('the graph', () => {
  it('marksEveryLinkAnOpenAlertRunsThroughAndEachCompanysMostSevereOpenAlert', async () => {
    backend.alerts = [
      sampleAlert({ id: 1, severity: 'MEDIUM', holdings: [tsmcSuppliesNvidia(1, 1)] }),
      sampleAlert({ id: 2, headline: 1, severity: 'HIGH', status: 'DISMISSED', holdings: [tsmcSuppliesNvidia(2, 3)] }),
    ];
    renderAt();
    await graph();

    await waitFor(() => expect(screen.getByTestId('risk-links')).toHaveTextContent(/^1$/));
    expect(screen.getByTestId('alert-dots')).toHaveTextContent('11:MEDIUM,1:MEDIUM');
  });

  it('listsTheCompaniesSeveralHoldingsDependOnAndChoosingOneShowsIt', async () => {
    renderAt();
    await graph();

    const shared = within(drawer()).getByText('Shared Suppliers and Customers');
    expect(shared).toBeInTheDocument();
    await userEvent.click(within(drawer()).getByRole('button', { name: /Taiwan Semiconductor Manufacturing/ }));
    expect(screen.getByTestId('selected-node')).toHaveTextContent(String(TSMC.id));
  });

  it('givesTheGraphTheLogoDevTokenAndTheDomainsOfCompaniesWithoutATicker', async () => {
    backend.logoToken = 'pk_test';
    renderAt();
    await graph();

    await waitFor(() => expect(screen.getByTestId('logo-domains')).toHaveTextContent('samsung.example'));
    expect(screen.getByTestId('logo-token')).toHaveTextContent('pk_test');
    const asked = vi.mocked(fetch).mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith('/api/logos/domains'));
    expect(asked).toEqual([`/api/logos/domains?names=${encodeURIComponent('Samsung Electronics Co., Ltd.')}`]);
  });
});

// ---- 5. Folding the drawer, closing and reopening the Inspector ------------------------------------------------------

describe('the panels', () => {
  it('foldsTheDrawerToItsRailOfHoldingsAndOpensItAgain', async () => {
    renderAt();
    await graph();

    await userEvent.click(screen.getByRole('button', { name: 'Fold the Drawer' }));
    expect(insets().left).toBe(84);
    await userEvent.click(screen.getByRole('button', { name: 'NVIDIA' }));
    expect(within(inspector()).getByRole('heading', { name: 'NVIDIA' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Open the Drawer' }));
    expect(insets().left).toBe(404);
  });

  it('closesTheInspectorLeavingATabThatOpensItAgainOnTheSameThing', async () => {
    renderAt();
    await graph();
    await userEvent.click(screen.getByRole('button', { name: 'Link 1' }));

    await userEvent.click(within(inspector()).getByRole('button', { name: 'Close the Inspector' }));
    await waitFor(() => expect(screen.queryByRole('complementary', { name: 'Inspector' })).not.toBeInTheDocument());

    await userEvent.click(await screen.findByRole('button', { name: 'Open the Inspector Again' }));
    expect(within(inspector()).getByRole('heading', { name: 'Taiwan Semiconductor Manufacturing Supplies NVIDIA' })).toBeInTheDocument();
  });

  it('closesTheInspectorWithEscape', async () => {
    renderAt();
    await graph();
    await userEvent.click(screen.getByRole('button', { name: 'Link 1' }));

    await userEvent.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('complementary', { name: 'Inspector' })).not.toBeInTheDocument());
  });
});

// ---- 6. Old addresses ----------------------------------------------------------------------------------------------

describe('the old addresses', () => {
  it('opensTheWorkspaceFromTheOldSupplyChainAddress', async () => {
    renderAt('/portfolios/7/supply-chain');
    await graph();

    expect(screen.getByLabelText('Address')).toHaveTextContent(/^\/portfolios\/7$/);
    expect(within(drawer()).getByRole('tab', { name: 'Holdings' })).toHaveAttribute('aria-selected', 'true');
  });

  it('opensTheWorkspaceOnTheAlertsTabFromTheOldAlertsAddress', async () => {
    renderAt('/portfolios/7/alerts');
    await graph();

    expect(screen.getByLabelText('Address')).toHaveTextContent('/portfolios/7?tab=alerts');
    expect(within(drawer()).getByRole('tab', { name: 'Alerts' })).toHaveAttribute('aria-selected', 'true');
  });
});

// ---- 7. No sidebar, and the Analyst Desk for analysts only ----------------------------------------------------------

describe('the top bar', () => {
  it('offersNoSidebarOfPortfolioPages', async () => {
    renderAt();
    await graph();

    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Supply Chain' })).not.toBeInTheDocument();
  });

  it('showsTheAnalystDeskButtonToAnAnalystAndItOpensTheDesk', async () => {
    backend.me = { ...backend.me, role: 'ANALYST' };
    renderAt();
    await graph();

    await userEvent.click(await screen.findByRole('button', { name: 'Analyst Desk' }));

    expect(screen.getByLabelText('Address')).toHaveTextContent('/review');
    expect(await screen.findByRole('button', { name: 'Semiconductors' })).toBeInTheDocument();
  });

  it('showsNoAnalystDeskButtonToAPortfolioManager', async () => {
    renderAt();
    await screen.findByRole('button', { name: 'Account (pm@example.com)' });
    await graph();

    expect(screen.queryByRole('button', { name: 'Analyst Desk' })).not.toBeInTheDocument();
  });
});

// ---- 8. Every state ------------------------------------------------------------------------------------------------

describe('every state', () => {
  it('showsLoadingRowsWhileThePortfolioLoads', () => {
    renderAt();

    expect(within(drawer()).getByLabelText('Loading')).toHaveAttribute('aria-busy', 'true');
  });

  it('showsTheBackendsMessageAndRetryWhenThePortfolioCannotLoad', async () => {
    backend.portfoliosFail = 503;
    renderAt();

    expect(await screen.findByRole('heading', { name: 'Portfolio Could Not Load' })).toBeInTheDocument();
    expect(screen.getByText('The server is restarting.')).toBeInTheDocument();
    backend.portfoliosFail = undefined;
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await graph()).toBeInTheDocument();
  });

  it('saysPortfolioNotFoundForAPortfolioThatIsNotTheUsers', async () => {
    renderAt('/portfolios/99');

    expect(await screen.findByRole('heading', { name: 'Portfolio Not Found' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Open My Portfolios' }));
    await waitFor(() => expect(screen.getByLabelText('Address')).toHaveTextContent('/portfolios/7'));
  });

  it('offersUploadCsvAndAddHoldingForAPortfolioWithNoHoldings', async () => {
    backend.holdings = [];
    backend.relationships = [];
    renderAt();

    expect(await screen.findByRole('heading', { name: 'No Supply Chain Yet' })).toBeInTheDocument();
    // The drawer's loading rows leave before its holdings arrive, a moment after the card.
    expect(await within(drawer()).findByText('No Holdings Yet')).toBeInTheDocument();
    const card = screen.getByRole('heading', { name: 'No Supply Chain Yet' }).parentElement!;
    expect(within(card).getByRole('button', { name: 'Upload CSV' })).toBeInTheDocument();
    await userEvent.click(within(card).getByRole('button', { name: 'Add Holding' }));
    await waitFor(() => expect(screen.getByLabelText('Ticker')).toHaveFocus());
    expect(screen.getByRole('button', { name: 'Portfolio: Semiconductors' })).toHaveTextContent('No Holdings Yet');
  });
});
