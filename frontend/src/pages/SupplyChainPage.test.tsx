// The Supply Chain page: loading, error, empty and loaded states, and clicking an edge.
//
// SupplyChainGraph draws with Cytoscape, which needs a real <canvas> that jsdom does not provide (see
// SupplyChainGraph.tsx). It is replaced here with a fake that renders one button per edge, so a click can be
// simulated without a browser. What the real graph draws (nodes, shapes, colours) is Cytoscape's own job; what goes
// into it is buildGraphElements, already tested on its own in supplyChain.test.ts.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import { tokenStore } from '../api/client';
import type { GraphEdge, GraphNode } from './supplyChain';
import { SupplyChainPage } from './SupplyChainPage';

vi.mock('../components/SupplyChainGraph', () => ({
  SupplyChainGraph: ({
    nodes,
    edges,
    onEdgeSelect,
  }: {
    nodes: GraphNode[];
    edges: GraphEdge[];
    onEdgeSelect: (relationshipId: number) => void;
  }) => (
    <div data-testid="fake-graph">
      <span data-testid="node-count">{nodes.length}</span>
      <span data-testid="edge-count">{edges.length}</span>
      {edges.map((edge) => (
        <button key={edge.id} onClick={() => onEdgeSelect(edge.relationshipId)}>
          {`Select edge ${edge.relationshipId}`}
        </button>
      ))}
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
    ...overrides,
  };
}

function fakeFetch(relationships: Relationship[], holdings: Holding[]) {
  return vi.fn(async (url: string) =>
    new Response(JSON.stringify(url.endsWith('/relationships') ? relationships : holdings)),
  );
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
    expect(screen.getByRole('link', { name: 'Portfolio page' })).toHaveAttribute('href', '/portfolios/7');
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

  it('shows the evidence, the report and its date, and a Show in Report link, when an edge is clicked', async () => {
    vi.stubGlobal('fetch', fakeFetch([relationship()], [holding()]));
    renderAt('/portfolios/7/supply-chain');
    await screen.findByTestId('fake-graph');

    await userEvent.click(screen.getByRole('button', { name: 'Select edge 1' }));

    expect(screen.getByText(tsmcSentence)).toBeInTheDocument();
    expect(screen.getByText('10-K, filed 2026-02-25')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: 'Show in Report' });
    expect(link).toHaveAttribute(
      'href',
      'https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm' +
        '#:~:text=We%20utilize%20foundries%2C%20such%20as,to%20produce%20our%20semiconductor%20wafers.',
    );
    expect(link).toHaveAttribute('target', '_blank');
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
});
