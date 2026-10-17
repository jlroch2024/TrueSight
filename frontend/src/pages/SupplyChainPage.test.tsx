// The Supply Chain page: loading, error, empty and loaded states, and clicking an edge.
//
// SupplyChainGraph draws with Cytoscape, which needs a real <canvas> that jsdom does not provide (see
// SupplyChainGraph.tsx). It is replaced here with a fake that renders one button per edge, so a click can be
// simulated without a browser. What the real graph draws (nodes, shapes, colours) is Cytoscape's own job; what goes
// into it is buildGraphElements, already tested on its own in supplyChain.test.ts.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import sampleRelationshipsJson from '../../../docs/examples/relationships.json';
import type { NewsArticle } from '../api/news';
import type { Holding, Portfolio } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import { tokenStore } from '../api/client';
import { TSMC, sampleNews } from '../test/sampleNews';
import type { GraphEdge, GraphNode } from './supplyChain';
import { SupplyChainPage } from './SupplyChainPage';

vi.mock('../components/SupplyChainGraph', () => ({
  SupplyChainGraph: ({
    nodes,
    edges,
    onEdgeSelect,
    onNodeSelect,
    selectedNodeId,
    selectedRelationshipId,
    logoToken,
    logoDomains,
  }: {
    nodes: GraphNode[];
    edges: GraphEdge[];
    onEdgeSelect: (relationshipId: number) => void;
    onNodeSelect?: (nodeId: string | null) => void;
    selectedNodeId?: string | null;
    selectedRelationshipId?: number | null;
    logoToken?: string;
    logoDomains?: Record<string, string>;
  }) => (
    <div data-testid="fake-graph">
      <span data-testid="logo-domains">{JSON.stringify(logoDomains ?? {})}</span>
      <span data-testid="logo-token">{logoToken}</span>
      <span data-testid="node-count">{nodes.length}</span>
      <span data-testid="edge-count">{edges.length}</span>
      <span data-testid="selected-node">{selectedNodeId ?? ''}</span>
      <span data-testid="selected-relationship">{selectedRelationshipId ?? ''}</span>
      {edges.map((edge) => (
        <button key={edge.id} onClick={() => onEdgeSelect(edge.relationshipId)}>
          {`Select edge ${edge.relationshipId}`}
        </button>
      ))}
      {nodes.map((node) => (
        <button key={node.id} onClick={() => onNodeSelect?.(node.id)}>
          {`Select company ${node.id}`}
        </button>
      ))}
      <button onClick={() => onNodeSelect?.(null)}>Click the background</button>
    </div>
  ),
}));

const tsmcSentence =
  'We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC, and Samsung ' +
  'Electronics Co., Ltd., or Samsung, to produce our semiconductor wafers.';

function relationship(overrides: Partial<Relationship> = {}): Relationship {
  return {
    id: 1,
    company: { id: 1, name: 'NVIDIA Corporation', ticker: 'NVDA' },
    counterparty: { id: 11, name: 'Taiwan Semiconductor Manufacturing Company Limited', ticker: 'TSM' },
    type: 'SUPPLIER',
    provides: 'semiconductor wafers',
    evidence: tsmcSentence,
    report: {
      form: '10-K',
      filingDate: '2026-02-25',
      url: 'https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm',
    },
    reviewStatus: 'UNREVIEWED',
    reviewedAt: null,
    reviewNote: null,
    ...overrides,
  };
}

function holding(overrides: Partial<Holding> = {}): Holding {
  return {
    id: 1,
    ticker: 'NVDA',
    weight: null,
    companyName: 'NVIDIA Corporation',
    status: 'DONE',
    statusReason: null,
    reportType: '10-K',
    reportDate: '2026-02-25',
    reportLink: 'https://www.sec.gov/example',
    lastAnalysed: null,
    ...overrides,
  };
}

const PORTFOLIO: Portfolio = {
  id: 7,
  name: 'Semiconductors',
  createdAt: '2026-10-01T00:00:00Z',
  newsStatus: 'IDLE',
  newsStatusReason: null,
  newsCheckedAt: null,
};

// Answers the page's calls. settings is GET /api/settings's answer, or a status code for it to fail with. news is
// GET .../news's answer, and portfolio the open portfolio in GET /api/portfolios.
function fakeFetch(
  relationships: Relationship[],
  holdings: Holding[],
  settings: { logoToken: string } | number = { logoToken: '' },
  news: NewsArticle[] = [],
  portfolio: Portfolio = PORTFOLIO,
) {
  return vi.fn(async (url: string) => {
    if (url.endsWith('/settings')) {
      return typeof settings === 'number'
        ? new Response(JSON.stringify({ message: 'Something went wrong.' }), { status: settings })
        : new Response(JSON.stringify(settings));
    }
    if (url === '/api/portfolios') return new Response(JSON.stringify([portfolio]));
    if (url.endsWith('/news')) return new Response(JSON.stringify(news));
    if (url.startsWith('/api/logos/domains')) return new Response(JSON.stringify({}));
    return new Response(JSON.stringify(url.endsWith('/relationships') ? relationships : holdings));
  });
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/portfolios/:portfolioId/supply-chain" element={<SupplyChainPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  tokenStore.set('test-token');
});

describe('Supply Chain page', () => {
  it('shows a loading message before the data arrives', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));

    renderAt('/portfolios/7/supply-chain');

    expect(screen.getByText('Loading…')).toBeInTheDocument();
  });

  it("shows the backend's message when the portfolio is not found", async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ message: 'Portfolio not found.' }), { status: 404 })),
    );

    renderAt('/portfolios/7/supply-chain');

    expect(await screen.findByText('Portfolio not found.')).toBeInTheDocument();
  });

  it('says how to run an analysis when the portfolio has holdings but no relationships yet', async () => {
    vi.stubGlobal('fetch', fakeFetch([], [holding({ companyName: null, status: 'WAITING' })]));

    renderAt('/portfolios/7/supply-chain');

    expect(await screen.findByText(/Run Analyse on the/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Portfolio Page' })).toBeInTheDocument();
  });

  it('says to upload holdings first when the portfolio has none at all', async () => {
    vi.stubGlobal('fetch', fakeFetch([], []));

    renderAt('/portfolios/7/supply-chain');

    expect(await screen.findByText(/Upload holdings on the/)).toBeInTheDocument();
  });

  it('shows the graph with the holdings and relationships as nodes and edges', async () => {
    vi.stubGlobal('fetch', fakeFetch([relationship()], [holding()]));

    renderAt('/portfolios/7/supply-chain');

    expect(await screen.findByTestId('fake-graph')).toBeInTheDocument();
    expect(screen.getByTestId('node-count')).toHaveTextContent('2'); // NVIDIA and TSMC
    expect(screen.getByTestId('edge-count')).toHaveTextContent('1');
  });

  it('shows the evidence, report and date, and a Show in Report button that opens the report at the evidence', async () => {
    vi.stubGlobal('fetch', fakeFetch([relationship()], [holding()]));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 1' }));

    expect(screen.getByText(tsmcSentence)).toBeInTheDocument();
    expect(screen.getByText('10-K, Filed 2026-02-25')).toBeInTheDocument();
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    await userEvent.click(screen.getByRole('button', { name: 'Show in Report' }));
    expect(open).toHaveBeenCalledWith(
      'https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm' +
        '#:~:text=We%20utilize%20foundries%2C%20such%20as,to%20produce%20our%20semiconductor%20wafers.',
      '_blank',
      'noopener,noreferrer',
    );
  });

  it('switches the evidence shown when a different edge is clicked', async () => {
    const first = relationship({ id: 1 });
    const second = relationship({
      id: 2,
      counterparty: { id: 12, name: 'Samsung Electronics Co., Ltd.', ticker: null },
      evidence: 'Samsung also supplies us with memory.',
    });
    vi.stubGlobal('fetch', fakeFetch([first, second], [holding()]));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 1' }));
    expect(screen.getByText(tsmcSentence)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 2' }));
    expect(screen.getByText('Samsung also supplies us with memory.')).toBeInTheDocument();
    expect(screen.queryByText(tsmcSentence)).not.toBeInTheDocument();
  });

  it('gives the graph the Logo.dev token from the settings', async () => {
    const fetch = fakeFetch([relationship()], [holding()], { logoToken: 'pk_test_token' });
    vi.stubGlobal('fetch', fetch);

    renderAt('/portfolios/7/supply-chain');

    expect(await screen.findByTestId('logo-token')).toHaveTextContent('pk_test_token');
    expect(fetch).toHaveBeenCalledWith('/api/settings', expect.anything());
  });

  it('still shows the graph, without logos, when the settings cannot be fetched', async () => {
    vi.stubGlobal('fetch', fakeFetch([relationship()], [holding()], 500));

    renderAt('/portfolios/7/supply-chain');

    expect(await screen.findByTestId('fake-graph')).toBeInTheDocument();
    expect(screen.getByTestId('edge-count')).toHaveTextContent('1');
    expect(screen.getByTestId('logo-token')).toBeEmptyDOMElement();
    expect(screen.queryByText('Something went wrong.')).not.toBeInTheDocument();
  });

  describe('shared suppliers and customers', () => {
    const tsmc = { id: 11, name: 'Taiwan Semiconductor Manufacturing Company Limited', ticker: 'TSM' };
    const samsung = { id: 12, name: 'Samsung Electronics Co., Ltd.', ticker: null };
    const micron = { id: 13, name: 'Micron Technology, Inc.', ticker: 'MU' };
    const nvidia = { id: 1, name: 'NVIDIA Corporation', ticker: 'NVDA' };
    const amd = { id: 3, name: 'Advanced Micro Devices, Inc.', ticker: 'AMD' };
    const qualcomm = { id: 5, name: 'QUALCOMM Incorporated', ticker: 'QCOM' };
    const holdings = [
      holding({ id: 1, ticker: 'NVDA', companyName: nvidia.name }),
      holding({ id: 2, ticker: 'AMD', companyName: amd.name }),
      holding({ id: 3, ticker: 'QCOM', companyName: qualcomm.name }),
    ];

    it('lists the companies several holdings depend on in the left panel, most shared first', async () => {
      const relationships = [
        relationship({ id: 1, company: nvidia, counterparty: samsung }),
        relationship({ id: 2, company: amd, counterparty: samsung }),
        relationship({ id: 3, company: nvidia, counterparty: tsmc }),
        relationship({ id: 4, company: amd, counterparty: tsmc }),
        relationship({ id: 5, company: qualcomm, counterparty: tsmc }),
        relationship({ id: 6, company: nvidia, counterparty: micron }), // one holding only: not listed
      ];
      vi.stubGlobal('fetch', fakeFetch(relationships, holdings));

      renderAt('/portfolios/7/supply-chain');

      await screen.findByTestId('fake-graph');
      const panel = screen.getByRole('complementary', { name: 'Holdings and News' });
      expect(within(panel).getByRole('heading', { name: 'Shared Suppliers and Customers' })).toBeInTheDocument();
      const shared = within(panel).getAllByRole('button').filter((button) => button.classList.contains('shared'));
      expect(shared.map((button) => button.textContent)).toEqual([
        'TSMTaiwan Semiconductor ManufacturingSupplies 3 Holdings',
        'Samsung ElectronicsSupplies 2 Holdings',
      ]);
      expect(within(panel).queryByText(/Micron/)).not.toBeInTheDocument();
    });

    it('chooses a shared company on the graph when it is clicked in the panel', async () => {
      const relationships = [
        relationship({ id: 1, company: nvidia, counterparty: samsung }),
        relationship({ id: 2, company: amd, counterparty: samsung }),
      ];
      vi.stubGlobal('fetch', fakeFetch(relationships, holdings));
      renderAt('/portfolios/7/supply-chain');
      await screen.findByTestId('fake-graph');

      await userEvent.click(screen.getByRole('button', { name: /Samsung Electronics/ }));

      expect(screen.getByTestId('selected-node')).toHaveTextContent('12');
    });

    it('leaves the section out, with no empty card under the graph, when no company is shared', async () => {
      vi.stubGlobal('fetch', fakeFetch([relationship()], [holding()]));

      renderAt('/portfolios/7/supply-chain');

      await screen.findByTestId('fake-graph');
      expect(screen.queryByRole('heading', { name: 'Shared Suppliers and Customers' })).not.toBeInTheDocument();
      expect(screen.queryByText(/two or more of them/)).not.toBeInTheDocument();
    });
  });
});

// The real samples: NVIDIA names TSMC as a supplier; Cirrus Logic names Apple as its customer.
const sampleRelationships = sampleRelationshipsJson as Relationship[];

describe('Supply Chain workspace', () => {
  const relationships = [sampleRelationships[0]!, sampleRelationships[14]!];
  const holdings = [
    holding({ id: 1, ticker: 'NVDA', companyName: 'NVIDIA Corporation', weight: 18 }),
    holding({ id: 2, ticker: 'CRUS', companyName: 'Cirrus Logic, Inc.', weight: null }),
    holding({ id: 3, ticker: 'NEW', companyName: null, weight: 30, status: 'WAITING', reportType: null, reportLink: null }),
  ];

  async function openWorkspace() {
    vi.stubGlobal('fetch', fakeFetch(relationships, holdings));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');
  }

  const inspector = () => screen.getByRole('complementary', { name: 'Inspector' });
  const holdingsPanel = () => screen.getByRole('complementary', { name: 'Holdings and News' });

  it('shows the holdings, the graph and the Inspector side by side, with nothing chosen yet', async () => {
    await openWorkspace();

    expect(holdingsPanel()).toBeInTheDocument();
    expect(screen.getByTestId('fake-graph')).toBeInTheDocument();
    expect(within(inspector()).getByText('Click a company or an arrow to see its details.')).toBeInTheDocument();
    expect(screen.queryByText(/Who supplies your holdings/)).not.toBeInTheDocument();
  });

  it('lists holdings largest first, each with its weight and how many suppliers and customers it has', async () => {
    await openWorkspace();

    const rows = within(holdingsPanel()).getAllByRole('listitem').map((row) => row.textContent);
    expect(rows).toEqual([
      'NEW30%Waiting',
      'NVDANVIDIA18%1 Supplier · 0 Customers',
      'CRUSCirrus Logic0 Suppliers · 1 Customer',
    ]);
  });

  it('greys a holding without a report, with its status, and does not let it be chosen', async () => {
    await openWorkspace();

    const waiting = within(holdingsPanel()).getByText('NEW').closest('li')!;
    expect(waiting).toHaveClass('unavailable');
    expect(within(waiting).queryByRole('button')).not.toBeInTheDocument();
  });

  it('chooses a holding from the panel: the graph focuses it, and the Inspector shows it', async () => {
    await openWorkspace();

    await userEvent.click(within(holdingsPanel()).getByRole('button', { name: /NVDA/ }));

    expect(screen.getByTestId('selected-node')).toHaveTextContent('1');
    expect(within(inspector()).getByRole('heading', { name: 'NVIDIA' })).toBeInTheDocument();
    expect(within(inspector()).getByText('Holding · 18% of the Portfolio')).toBeInTheDocument();
    expect(within(inspector()).getByText('10-K, Filed 2026-02-25')).toBeInTheDocument();
  });

  it("shows a company clicked on the graph with its suppliers and customers, and opens one's relationship", async () => {
    await openWorkspace();

    await userEvent.click(screen.getByRole('button', { name: 'Select company 11' })); // TSMC

    expect(within(inspector()).getByText('Not Held')).toBeInTheDocument();
    const customers = within(inspector()).getByRole('region', { name: 'Customers' });
    await userEvent.click(within(customers).getByRole('button', { name: /NVDA/ }));

    expect(
      within(inspector()).getByRole('heading', { name: 'Taiwan Semiconductor Manufacturing supplies NVIDIA: semiconductor wafers' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('selected-relationship')).toHaveTextContent('1');
  });

  it('shows an arrow clicked on the graph in the Inspector, with its evidence and Show in Report', async () => {
    await openWorkspace();

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 15' }));

    expect(within(inspector()).getByRole('heading', { name: 'Cirrus Logic supplies Apple' })).toBeInTheDocument();
    expect(within(inspector()).getByText(sampleRelationships[14]!.evidence)).toBeInTheDocument();
    expect(within(inspector()).getByRole('button', { name: 'Show in Report' })).toBeInTheDocument();

    await userEvent.click(within(inspector()).getByRole('button', { name: /AAPL/ }));
    expect(screen.getByTestId('selected-node')).toHaveTextContent('18');
  });

  it('lets go of the choice when the background is clicked', async () => {
    await openWorkspace();
    await userEvent.click(screen.getByRole('button', { name: 'Select company 1' }));

    await userEvent.click(screen.getByRole('button', { name: 'Click the background' }));

    expect(screen.getByTestId('selected-node')).toBeEmptyDOMElement();
    expect(within(inspector()).getByText('Click a company or an arrow to see its details.')).toBeInTheDocument();
  });
});

describe('Supply Chain news', () => {
  const nvidia = { id: 1, name: 'NVIDIA Corporation', ticker: 'NVDA' };
  const relationships = [relationship({ id: 1, company: nvidia, counterparty: TSMC })];
  const holdings = [holding({ id: 1, ticker: 'NVDA', companyName: nvidia.name, weight: 18 })];
  const checked = { ...PORTFOLIO, newsCheckedAt: '2026-10-13T00:00:00Z' };

  it('opens on the News tab from ?panel=news, listing the collected headlines', async () => {
    const news = sampleNews(3);
    vi.stubGlobal('fetch', fakeFetch(relationships, holdings, undefined, news, checked));

    renderAt('/portfolios/7/supply-chain?panel=news');

    expect(await screen.findByRole('tab', { name: 'News (3)' })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByText(news[0]!.title)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Open Article' })).toHaveLength(3);
  });

  it('lists the headlines once a running news check finishes, without reloading the page', async () => {
    let finished = false;
    const news = sampleNews(2);
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      if (url === '/api/portfolios') {
        return new Response(JSON.stringify([finished ? checked : { ...PORTFOLIO, newsStatus: 'CHECKING' }]));
      }
      if (url.endsWith('/news')) return new Response(JSON.stringify(finished ? news : []));
      if (url.endsWith('/settings')) return new Response(JSON.stringify({ logoToken: '' }));
      return new Response(JSON.stringify(url.endsWith('/relationships') ? relationships : holdings));
    }));
    renderAt('/portfolios/7/supply-chain?panel=news');
    expect(await screen.findByRole('button', { name: 'Checking News…' })).toBeDisabled();

    finished = true;

    expect(await screen.findByText(news[0]!.title, undefined, { timeout: 4000 })).toBeInTheDocument();
  });

  it("chooses a headline's company on the graph, and the Inspector shows its newest headlines", async () => {
    const news = sampleNews(5);
    vi.stubGlobal('fetch', fakeFetch(relationships, holdings, undefined, news, checked));
    renderAt('/portfolios/7/supply-chain?panel=news');
    await screen.findByText(news[0]!.title);

    await userEvent.click(screen.getAllByRole('button', { name: 'TSM' })[0]!);

    expect(screen.getByTestId('selected-node')).toHaveTextContent('11');
    const inspector = screen.getByRole('complementary', { name: 'Inspector' });
    const recent = within(inspector).getByRole('region', { name: 'Recent News' });
    expect(within(recent).getAllByRole('listitem')).toHaveLength(3);
    expect(within(recent).getByText('(5)')).toBeInTheDocument();
  });

  it('narrows the News tab to one company with Show All News, and Clear lifts it', async () => {
    const [tsmcNews, other] = sampleNews(2);
    const nvidiaNews = { ...other!, id: 99, company: nvidia, isHolding: true };
    vi.stubGlobal('fetch', fakeFetch(relationships, holdings, undefined, [tsmcNews!, nvidiaNews], checked));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');
    await userEvent.click(screen.getByRole('button', { name: 'Select company 1' })); // NVIDIA

    await userEvent.click(await screen.findByRole('button', { name: 'Show All News' }));

    const panel = screen.getByRole('tabpanel');
    expect(screen.getByRole('tab', { name: 'News (2)' })).toHaveAttribute('aria-selected', 'true');
    expect(within(panel).getAllByRole('listitem')).toHaveLength(1);
    expect(within(panel).getByText('Only NVIDIA')).toBeInTheDocument();

    await userEvent.click(within(panel).getByRole('button', { name: 'Clear' }));
    expect(within(panel).getAllByRole('listitem')).toHaveLength(2);
  });
});

describe('Supply Chain logos', () => {
  it('asks for the website domains of companies without a ticker, and gives them to the graph', async () => {
    // Samsung has no ticker in this relationship; NVIDIA and TSMC have theirs. The domain is made up for the test.
    const samsung = { id: 12, name: 'Samsung Electronics Co., Ltd.', ticker: null };
    const fetchMock = vi.fn(async (url: string) => {
      if (url.endsWith('/settings')) return new Response(JSON.stringify({ logoToken: 'pk_test' }));
      if (url.startsWith('/api/logos/domains')) return new Response(JSON.stringify({ [samsung.name]: 'samsung.example' }));
      if (url === '/api/portfolios') return new Response(JSON.stringify([PORTFOLIO]));
      if (url.endsWith('/news')) return new Response('[]');
      return new Response(JSON.stringify(url.endsWith('/relationships') ? [relationship(), relationship({ id: 2, counterparty: samsung })] : [holding()]));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderAt('/portfolios/7/supply-chain');

    await screen.findByText(/samsung\.example/);
    const asked = fetchMock.mock.calls.map(([url]) => url).filter((url) => url.startsWith('/api/logos/domains'));
    expect(asked).toEqual([`/api/logos/domains?names=${encodeURIComponent(samsung.name)}`]);
  });
});
