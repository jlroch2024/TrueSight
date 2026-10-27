// The Supply Chain page: loading, error, empty and loaded states, and clicking an edge.
//
// The graph (LivingGraph) draws on a <canvas>, which jsdom cannot draw on. It is replaced here with a fake that renders
// one button per edge, so a click can be simulated without a browser. What the real graph does is tested on its own in
// src/graph/; what goes into it is buildGraphElements, tested in supplyChain.test.ts.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import type { AlertEvent } from '../api/alerts';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import sampleRelationshipsJson from '../../../docs/examples/relationships.json';
import type { Severity } from '../api/news';
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import { tokenStore } from '../api/client';
import { showInReportUrl } from '../format';
import { sampleAlert } from '../test/sampleAlerts';
import { TSMC } from '../test/sampleNews';
import type { GraphEdge, GraphNode } from './supplyChain';
import { SupplyChainPage } from './SupplyChainPage';
import { UNREVIEWED_CONFIDENCE } from '../test/sampleConfidence';

vi.mock('../graph/LivingGraph', () => ({
  LivingGraph: ({
    nodes,
    edges,
    onEdgeSelect,
    onNodeSelect,
    selectedNodeId,
    selectedRelationshipId,
    logoToken,
    logoDomains,
    risks,
    alertDots,
  }: {
    nodes: GraphNode[];
    edges: GraphEdge[];
    onEdgeSelect: (relationshipId: number) => void;
    onNodeSelect?: (nodeId: string | null) => void;
    selectedNodeId?: string | null;
    selectedRelationshipId?: number | null;
    logoToken?: string;
    logoDomains?: Record<string, string>;
    risks?: Map<number, { severity: string; eventType: string; title: string }>;
    alertDots?: Map<string, Severity>;
  }) => (
    <div data-testid="fake-graph">
      <span data-testid="logo-domains">{JSON.stringify(logoDomains ?? {})}</span>
      <span data-testid="logo-token">{logoToken}</span>
      <span data-testid="node-count">{nodes.length}</span>
      <span data-testid="edge-count">{edges.length}</span>
      <span data-testid="selected-node">{selectedNodeId ?? ''}</span>
      <span data-testid="selected-relationship">{selectedRelationshipId ?? ''}</span>
      <span data-testid="risk-relationships">{[...(risks?.keys() ?? [])].sort().join(',')}</span>
      <span data-testid="alert-dots">
        {[...(alertDots ?? [])].map(([nodeId, severity]) => `${nodeId}:${severity}`).sort().join(',')}
      </span>
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
    // The SEC's name for NVIDIA, and the display name the API sends for it: NVIDIA's own report writes "NVIDIA".
    company: { id: 1, name: 'NVIDIA CORP', displayName: 'NVIDIA', ticker: 'NVDA' },
    counterparty: {
      id: 11,
      name: 'Taiwan Semiconductor Manufacturing Company Limited',
      displayName: 'Taiwan Semiconductor Manufacturing',
      ticker: 'TSM',
    },
    type: 'SUPPLIER',
    provides: 'semiconductor wafers',
    evidence: tsmcSentence,
    source: 'AI',
    confidence: UNREVIEWED_CONFIDENCE,
    criticality: null,
    criticalityEvidence: null,
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
    companyName: 'NVIDIA CORP',
    companyDisplayName: 'NVIDIA',
    status: 'DONE',
    statusReason: null,
    reportType: '10-K',
    reportDate: '2026-02-25',
    reportLink: 'https://www.sec.gov/example',
    lastAnalysed: null,
    ...overrides,
  };
}

// Answers the page's calls. settings is GET /api/settings's answer, or a status code for it to fail with. alerts is
// GET .../alerts's answer, or a status code for it to fail with. GET .../news is never asked for, and answers an empty
// list if it is.
function fakeFetch(
  relationships: Relationship[],
  holdings: Holding[],
  settings: { logoToken: string } | number = { logoToken: '' },
  alerts: AlertEvent[] | number = [],
) {
  return vi.fn(async (url: string) => {
    if (url.endsWith('/settings')) {
      return typeof settings === 'number'
        ? new Response(JSON.stringify({ message: 'Something went wrong.' }), { status: settings })
        : new Response(JSON.stringify(settings));
    }
    if (url.includes('/alerts')) {
      return typeof alerts === 'number'
        ? new Response(JSON.stringify({ message: 'Something went wrong.' }), { status: alerts })
        : new Response(JSON.stringify({ weighted: false, holdingCount: holdings.length, alerts }));
    }
    if (url.endsWith('/news')) return new Response('[]');
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
  it('marks every relationship an open event runs through', async () => {
    const first = relationship();
    const second = relationship({
      id: 2,
      counterparty: { id: 12, name: 'Samsung Electronics', displayName: 'Samsung Electronics', ticker: '005930' },
    });
    const event: AlertEvent = {
      alertIds: [11],
      status: 'NEW',
      exposure: 40,
      headline: {
        article: {
          title: 'Temporary supply disruption',
          source: 'Local test fixture',
          publishedAt: '2026-10-20T00:00:00Z',
          url: 'https://example.com/event',
          companyName: 'Taiwan Semiconductor Manufacturing Company Limited',
        },
        assessment: {
          eventType: 'SUPPLY_SHORTAGE',
          severity: 'HIGH',
          confidence: 'HIGH',
          summary: null,
          reason: 'Test event reaches both supplier links.',
        },
      },
      sources: [{
        title: 'Temporary supply disruption',
        source: 'Local test fixture',
        publishedAt: '2026-10-20T00:00:00Z',
        url: 'https://example.com/event',
      }],
      holdings: [
        { holdingId: 1, ticker: 'NVDA', weight: 40, indirect: false, supplierName: 'TSMC', middleName: null, customerName: 'NVIDIA', reviewStatus: 'UNREVIEWED', relationshipId: 1, confidence: UNREVIEWED_CONFIDENCE },
        { holdingId: 1, ticker: 'NVDA', weight: 40, indirect: false, supplierName: 'Samsung Electronics', middleName: null, customerName: 'NVIDIA', reviewStatus: 'UNREVIEWED', relationshipId: 2, confidence: UNREVIEWED_CONFIDENCE },
      ],
      recommendation: null,
    };
    vi.stubGlobal('fetch', fakeFetch([first, second], [holding()], undefined, [event]));

    renderAt('/portfolios/7/supply-chain');

    expect(await screen.findByTestId('risk-relationships')).toHaveTextContent('1,2');
  });

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
    vi.stubGlobal('fetch', fakeFetch([], [holding({ companyName: null, companyDisplayName: null, status: 'WAITING' })]));

    renderAt('/portfolios/7/supply-chain');

    expect(await screen.findByText(/Run Analyse on the/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Portfolio Page' })).toBeInTheDocument();
  });

  // As the backend's RelationshipConfidence works them out; the website only shows them.
  it("shows a relationship's confidence in the Inspector, with each reason and its effect", async () => {
    const approved = relationship({
      reviewStatus: 'APPROVED',
      confidence: {
        level: 'HIGH',
        reasons: [
          { text: 'An Analyst Approved It', effect: 'RAISES' },
          { text: 'Quote Match Not Recorded', effect: 'NONE' },
          { text: 'Second Check Agrees', effect: 'NONE' },
        ],
      },
    });
    vi.stubGlobal('fetch', fakeFetch([approved], [holding()]));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 1' }));

    const confidence = within(screen.getByRole('complementary', { name: 'Inspector' })).getByRole('region', {
      name: 'Confidence',
    });
    expect(within(confidence).getByText('High Confidence')).toHaveClass('confidence-high');
    expect(within(confidence).getAllByRole('listitem').map((reason) => reason.textContent)).toEqual([
      'An Analyst Approved ItRaises Confidence',
      'Quote Match Not RecordedNo Effect',
      'Second Check AgreesNo Effect',
    ]);
  });

  it('shows a doubted relationship as Low Confidence, with the doubt lowering it', async () => {
    const doubted = relationship({
      confidence: {
        level: 'LOW',
        reasons: [
          { text: 'Not Yet Reviewed', effect: 'NONE' },
          { text: 'Strong Quote Match (100%)', effect: 'NONE' },
          { text: 'Second Check Doubts', effect: 'LOWERS' },
        ],
      },
    });
    vi.stubGlobal('fetch', fakeFetch([doubted], [holding()]));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 1' }));

    const inspector = screen.getByRole('complementary', { name: 'Inspector' });
    expect(within(inspector).getByText('Low Confidence')).toHaveClass('confidence-low');
    expect(within(inspector).getByText('Second Check Doubts').closest('li')).toHaveTextContent('Lowers Confidence');
  });

  // The SEC calls it "NVIDIA CORP"; the website once guessed "Nvidia" from that. NVIDIA's report writes "NVIDIA".
  it('shows NVIDIA by the display name the API sends in the Holdings panel and the Inspector', async () => {
    vi.stubGlobal('fetch', fakeFetch([relationship()], [holding({ weight: 18 })]));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');
    const panel = screen.getByRole('complementary', { name: 'Holdings' });

    expect(within(panel).getAllByRole('listitem').map((row) => row.textContent)).toEqual([
      'NVDANVIDIA18%1 Supplier · 0 Customers',
    ]);
    await userEvent.click(screen.getByRole('button', { name: 'Select company 1' }));
    const inspector = screen.getByRole('complementary', { name: 'Inspector' });
    expect(within(inspector).getByRole('heading', { name: 'NVIDIA' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Select edge 1' }));
    expect(
      within(inspector).getByRole('heading', { name: 'Taiwan Semiconductor Manufacturing supplies NVIDIA: semiconductor wafers' }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Nvidia/)).not.toBeInTheDocument();
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
    expect(screen.getByText('10-K, Filed Feb 25, 2026')).toBeInTheDocument();
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
      counterparty: { id: 12, name: 'Samsung Electronics Co., Ltd.', displayName: 'Samsung Electronics', ticker: null },
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
    const tsmc = TSMC;
    const samsung = { id: 12, name: 'Samsung Electronics Co., Ltd.', displayName: 'Samsung Electronics', ticker: null };
    const micron = { id: 13, name: 'Micron Technology, Inc.', displayName: 'Micron Technology', ticker: 'MU' };
    const nvidia = { id: 1, name: 'NVIDIA CORP', displayName: 'NVIDIA', ticker: 'NVDA' };
    const amd = { id: 3, name: 'ADVANCED MICRO DEVICES INC', displayName: 'AMD', ticker: 'AMD' };
    const qualcomm = { id: 5, name: 'QUALCOMM INC/DE', displayName: 'Qualcomm', ticker: 'QCOM' };
    const holdings = [nvidia, amd, qualcomm].map((company, i) =>
      holding({ id: i + 1, ticker: company.ticker, companyName: company.name, companyDisplayName: company.displayName }),
    );

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
      const panel = screen.getByRole('complementary', { name: 'Holdings' });
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
    holding({ id: 1, ticker: 'NVDA', companyName: 'NVIDIA Corporation', companyDisplayName: 'NVIDIA', weight: 18 }),
    holding({ id: 2, ticker: 'CRUS', companyName: 'Cirrus Logic, Inc.', companyDisplayName: 'Cirrus Logic', weight: null }),
    holding({
      id: 3,
      ticker: 'NEW',
      companyName: null,
      companyDisplayName: null,
      weight: 30,
      status: 'WAITING',
      reportType: null,
      reportLink: null,
    }),
  ];

  async function openWorkspace() {
    vi.stubGlobal('fetch', fakeFetch(relationships, holdings));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');
  }

  const inspector = () => screen.getByRole('complementary', { name: 'Inspector' });
  const holdingsPanel = () => screen.getByRole('complementary', { name: 'Holdings' });

  it('shows the holdings and the graph, with the Inspector closed until something is chosen', async () => {
    await openWorkspace();

    expect(holdingsPanel()).toBeInTheDocument();
    expect(screen.getByTestId('fake-graph')).toBeInTheDocument();
    expect(screen.queryByRole('complementary', { name: 'Inspector' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Who supplies your holdings/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Select company 1' }));
    expect(inspector()).toBeInTheDocument();
  });

  it('closes the Inspector with its close button, letting go of the choice', async () => {
    await openWorkspace();
    await userEvent.click(screen.getByRole('button', { name: 'Select company 1' }));

    await userEvent.click(within(inspector()).getByRole('button', { name: 'Close Inspector' }));

    expect(screen.queryByRole('complementary', { name: 'Inspector' })).not.toBeInTheDocument();
    expect(screen.getByTestId('selected-node')).toBeEmptyDOMElement();
  });

  it('folds the Holdings drawer to a strip, and opens it again', async () => {
    await openWorkspace();

    await userEvent.click(screen.getByRole('button', { name: 'Hide Holdings' }));
    expect(screen.queryByRole('heading', { name: 'Holdings' })).not.toBeInTheDocument();
    expect(document.querySelector('.workspace')).toHaveClass('left-closed');

    await userEvent.click(screen.getByRole('button', { name: 'Show Holdings' }));
    expect(screen.getByRole('heading', { name: 'Holdings' })).toBeInTheDocument();
    expect(document.querySelector('.workspace')).not.toHaveClass('left-closed');
  });

  it('has no News tab: the Holdings panel stands alone, with no tabs and no Check News', async () => {
    await openWorkspace();

    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /News/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /News/ })).not.toBeInTheDocument();
    expect(within(holdingsPanel()).getByRole('heading', { name: 'Holdings' })).toBeInTheDocument();
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
    expect(within(inspector()).getByText('10-K, Filed Feb 25, 2026')).toBeInTheDocument();
  });

  it("shows a company clicked on the graph with its suppliers and customers, and opens one's relationship", async () => {
    await openWorkspace();

    await userEvent.click(screen.getByRole('button', { name: 'Select company 11' })); // TSMC

    expect(within(inspector()).getByText('Not Held')).toBeInTheDocument();
    const customers = within(inspector()).getByRole('region', { name: 'Customers' });
    expect(within(customers).getByText('Semiconductor Wafers')).toBeInTheDocument();
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

  it('shows criticality evidence and opens the report at that sentence', async () => {
    const source = sampleRelationships[15]!;
    const critical = {
      ...source,
      criticality: 'SOLE_SOURCE' as const,
      criticalityEvidence: source.evidence,
    };
    const asml = holding({
      id: 16,
      ticker: 'ASML',
      companyName: source.company.name,
      companyDisplayName: source.company.displayName,
    });
    vi.stubGlobal('fetch', fakeFetch([critical], [asml]));
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: `Select edge ${source.id}` }));

    const criticality = inspector().querySelector<HTMLElement>('.criticality-evidence')!;
    expect(within(inspector()).getByText('Sole Source')).toBeInTheDocument();
    expect(within(criticality).getByText(source.evidence)).toBeInTheDocument();
    await userEvent.click(within(criticality).getByRole('button', { name: 'Show Criticality in Report' }));
    expect(open).toHaveBeenCalledWith(showInReportUrl(source.report.url, source.evidence), '_blank', 'noopener,noreferrer');
  });

  it('shows when an analyst approved a relationship and the note', async () => {
    const approved = {
      ...relationships[0]!,
      reviewStatus: 'APPROVED' as const,
      reviewedAt: '2026-10-14T12:00:00Z',
      reviewNote: 'Confirmed against the annual report.',
    };
    vi.stubGlobal('fetch', fakeFetch([approved], holdings));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 1' }));

    expect(within(inspector()).getByText('Approved by an Analyst on Oct 14, 2026')).toBeInTheDocument();
    expect(within(inspector()).getByText('Confirmed against the annual report.')).toBeInTheDocument();
  });

  it('says when a relationship was added by an analyst', async () => {
    const added = {
      ...relationships[0]!,
      source: 'ANALYST' as const,
      reviewStatus: 'APPROVED' as const,
    };
    vi.stubGlobal('fetch', fakeFetch([added], holdings));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 1' }));

    expect(within(inspector()).getByText('Added by an Analyst')).toBeInTheDocument();
  });

  it('shows an approval without a note and says when a relationship is unreviewed', async () => {
    const approved = {
      ...relationships[0]!,
      reviewStatus: 'APPROVED' as const,
      reviewedAt: '2026-10-14T12:00:00Z',
      reviewNote: null,
    };
    vi.stubGlobal('fetch', fakeFetch([approved, relationships[1]!], holdings));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 1' }));
    expect(within(inspector()).getByText('Approved by an Analyst on Oct 14, 2026')).toBeInTheDocument();
    expect(inspector().querySelector('.review-note')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 15' }));
    expect(within(inspector()).getByText('Not Yet Reviewed by an Analyst')).toBeInTheDocument();
  });

  it('lets go of the choice when the background is clicked', async () => {
    await openWorkspace();
    await userEvent.click(screen.getByRole('button', { name: 'Select company 1' }));

    await userEvent.click(screen.getByRole('button', { name: 'Click the background' }));

    expect(screen.getByTestId('selected-node')).toBeEmptyDOMElement();
    expect(screen.queryByRole('complementary', { name: 'Inspector' })).not.toBeInTheDocument();
  });
});

describe('Supply Chain alerts', () => {
  const nvidia = { id: 1, name: 'NVIDIA CORP', displayName: 'NVIDIA', ticker: 'NVDA' };
  const relationships = [relationship({ id: 1, company: nvidia, counterparty: TSMC })];
  const holdings = [
    holding({ id: 1, ticker: 'NVDA', companyName: nvidia.name, companyDisplayName: nvidia.displayName, weight: 18 }),
  ];

  /** The requests the page made. */
  const asked = (fetch: ReturnType<typeof fakeFetch>) => fetch.mock.calls.map(([url]) => url);
  /** The Inspector's Alerts section, once the alerts have arrived. */
  const alertsRegion = () =>
    within(screen.getByRole('complementary', { name: 'Inspector' })).findByRole('region', { name: 'Alerts' });

  it('asks for the alerts once, and never for the news list', async () => {
    const fetch = fakeFetch(relationships, holdings, undefined, [sampleAlert()]);
    vi.stubGlobal('fetch', fetch);

    renderAt('/portfolios/7/supply-chain?panel=news');
    await screen.findByTestId('fake-graph');
    await userEvent.click(screen.getByRole('button', { name: 'Select company 11' }));

    await alertsRegion();
    expect(asked(fetch).filter((url) => url.includes('/alerts'))).toEqual(['/api/portfolios/7/alerts']);
    expect(asked(fetch).filter((url) => url.endsWith('/news'))).toEqual([]);
    expect(asked(fetch).filter((url) => url === '/api/portfolios')).toEqual([]);
  });

  it('ignores ?panel=news: there is no News tab to open', async () => {
    vi.stubGlobal('fetch', fakeFetch(relationships, holdings));

    renderAt('/portfolios/7/supply-chain?panel=news');

    expect(await screen.findByTestId('fake-graph')).toBeInTheDocument();
    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'Holdings' })).toBeInTheDocument();
  });

  it("lists a company's open alerts in the Inspector, however old, with the path and Open Article", async () => {
    const fresh = sampleAlert({ id: 1, headline: 0 });
    const old = sampleAlert({ id: 2, headline: 1, daysOld: 40, severity: 'MEDIUM' });
    vi.stubGlobal('fetch', fakeFetch(relationships, holdings, undefined, [fresh, old]));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select company 11' })); // TSMC

    const alerts = await alertsRegion();
    expect(within(alerts).getByText('(2)')).toBeInTheDocument();
    expect(within(alerts).getAllByRole('listitem')).toHaveLength(2);
    expect(within(alerts).getByText(fresh.headline.article.title)).toBeInTheDocument();
    expect(within(alerts).getByText(old.headline.article.title)).toBeInTheDocument();
    expect(within(alerts).getAllByText('Taiwan Semiconductor Manufacturing supplies NVIDIA')).toHaveLength(2);
    expect(within(alerts).getAllByRole('button', { name: 'Open Article' })).toHaveLength(2);
  });

  it('also lists, for a holding, the open alerts that reach it through a supplier, with the path', async () => {
    vi.stubGlobal('fetch', fakeFetch(relationships, holdings, undefined, [sampleAlert()]));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select company 1' })); // NVIDIA, the holding

    const alerts = await alertsRegion();
    expect(within(alerts).getAllByRole('listitem')).toHaveLength(1);
    expect(within(alerts).getByText('Taiwan Semiconductor Manufacturing supplies NVIDIA')).toBeInTheDocument();
  });

  it('says there are no open alerts for a company with none', async () => {
    vi.stubGlobal('fetch', fakeFetch(relationships, holdings, undefined, []));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select company 11' }));

    const alerts = await alertsRegion();
    expect(within(alerts).getByText('(0)')).toBeInTheDocument();
    expect(within(alerts).getByText('No open alerts.')).toBeInTheDocument();
  });

  it('does not list a dismissed alert', async () => {
    vi.stubGlobal('fetch', fakeFetch(relationships, holdings, undefined, [sampleAlert({ status: 'DISMISSED' })]));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select company 11' }));

    expect(await within(await alertsRegion()).findByText('No open alerts.')).toBeInTheDocument();
    expect(screen.getByTestId('alert-dots')).toBeEmptyDOMElement();
  });

  it("gives the graph each company's most severe open alert, and none to a company with only dismissed ones", async () => {
    const samsung = { id: 12, name: 'Samsung Electronics Co., Ltd.', displayName: 'Samsung Electronics', ticker: null };
    const both = [
      relationship({ id: 1, company: nvidia, counterparty: TSMC }),
      relationship({ id: 2, company: nvidia, counterparty: samsung }),
    ];
    const alerts = [
      sampleAlert({ id: 1, headline: 0, severity: 'LOW' }),
      sampleAlert({ id: 2, headline: 1, severity: 'HIGH' }),
      sampleAlert({ id: 3, headline: 2, severity: 'HIGH', status: 'DISMISSED', companyName: samsung.name }),
    ];
    vi.stubGlobal('fetch', fakeFetch(both, holdings, undefined, alerts));

    renderAt('/portfolios/7/supply-chain');

    // TSMC (company 11) has two open alerts: the HIGH one wins. NVIDIA (company 1) is reached by both, so it too.
    // Samsung (company 12) has only a dismissed one, so it has no dot.
    await vi.waitFor(() => expect(screen.getByTestId('alert-dots')).toHaveTextContent('11:HIGH,1:HIGH'));
    expect(screen.getByTestId('alert-dots')).not.toHaveTextContent('12:');
  });

  it('still shows the graph, and no alerts section, when the alerts cannot be loaded', async () => {
    const fetch = fakeFetch(relationships, holdings, undefined, 500);
    vi.stubGlobal('fetch', fetch);
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');
    await vi.waitFor(() => expect(asked(fetch)).toContain('/api/portfolios/7/alerts'));
    await new Promise((resolve) => setTimeout(resolve, 20)); // the failure has been handled

    await userEvent.click(screen.getByRole('button', { name: 'Select company 11' }));

    expect(screen.getByRole('complementary', { name: 'Inspector' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Alerts' })).not.toBeInTheDocument();
    expect(screen.getByTestId('alert-dots')).toBeEmptyDOMElement();
  });
});

describe('Supply Chain logos', () => {
  it('asks for the website domains of companies without a ticker, and gives them to the graph', async () => {
    // Samsung has no ticker in this relationship; NVIDIA and TSMC have theirs. The domain is made up for the test.
    const samsung = { id: 12, name: 'Samsung Electronics Co., Ltd.', displayName: 'Samsung Electronics', ticker: null };
    const fetchMock = vi.fn(async (url: string) => {
      if (url.endsWith('/settings')) return new Response(JSON.stringify({ logoToken: 'pk_test' }));
      if (url.startsWith('/api/logos/domains')) return new Response(JSON.stringify({ [samsung.name]: 'samsung.example' }));
      if (url === '/api/portfolios') return new Response(JSON.stringify([{ id: 7, name: 'Semiconductors' }]));
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
