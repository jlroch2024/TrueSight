// Tests for buildGraphElements and showInReportUrl. The graph-building test uses docs/examples/relationships.json,
// the real sample data every AI story test uses.
import { describe, expect, it } from 'vitest';
import sampleRelationshipsJson from '../../../docs/examples/relationships.json';
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import { buildGraphElements, showInReportUrl } from './supplyChain';

const sampleRelationships = sampleRelationshipsJson as Relationship[];

// The 9 holdings docs/examples/relationships.json's companies belong to, plus Sony: its report names TSMC only as a
// planned partner, so (on purpose, per docs/examples/README.md) it has no relationships at all.
function sampleHoldings(): Holding[] {
  const holdingOf: [string, string][] = [
    ['NVDA', 'NVIDIA Corporation'],
    ['AMD', 'Advanced Micro Devices, Inc.'],
    ['QCOM', 'QUALCOMM Incorporated'],
    ['TSLA', 'Tesla, Inc.'],
    ['AVGO', 'Broadcom Inc.'],
    ['LRCX', 'Lam Research Corporation'],
    ['CRUS', 'Cirrus Logic, Inc.'],
    ['ASML', 'ASML Holding N.V.'],
    ['STM', 'STMicroelectronics N.V.'],
    ['SONY', 'Sony Group Corporation'],
  ];
  return holdingOf.map(([ticker, companyName], i) => ({
    id: i + 1,
    ticker,
    weight: null,
    companyName,
    status: 'DONE',
    statusReason: null,
    reportType: '10-K',
    reportDate: '2026-01-01',
    reportLink: 'https://www.sec.gov/example',
  }));
}

describe('buildGraphElements', () => {
  it('gives a supplier shared by several holdings, such as TSMC, one node with an edge to each', () => {
    const { nodes, edges } = buildGraphElements(sampleRelationships, sampleHoldings());

    const tsmc = nodes.filter((n) => n.name === 'Taiwan Semiconductor Manufacturing Company Limited');
    expect(tsmc).toHaveLength(1);
    expect(tsmc[0]!.isHolding).toBe(false);

    // TSMC supplies NVIDIA, AMD, QUALCOMM and Broadcom, and is a customer of Lam Research: 5 edges, one node.
    const tsmcEdges = edges.filter((e) => e.source === tsmc[0]!.id || e.target === tsmc[0]!.id);
    expect(tsmcEdges).toHaveLength(5);
  });

  it('tells holdings apart from other companies', () => {
    const { nodes } = buildGraphElements(sampleRelationships, sampleHoldings());

    const nvidia = nodes.find((n) => n.name === 'NVIDIA Corporation');
    const tsmc = nodes.find((n) => n.name === 'Taiwan Semiconductor Manufacturing Company Limited');

    expect(nvidia?.isHolding).toBe(true);
    expect(tsmc?.isHolding).toBe(false);
  });

  it('points the arrow from supplier to customer, whichever side of the relationship the holding is on', () => {
    const { nodes, edges } = buildGraphElements(sampleRelationships, sampleHoldings());
    const nvidia = nodes.find((n) => n.name === 'NVIDIA Corporation')!;
    const tsmcAsSupplier = nodes.find((n) => n.name === 'Taiwan Semiconductor Manufacturing Company Limited')!;
    const lamResearch = nodes.find((n) => n.name === 'Lam Research Corporation')!;

    // NVIDIA's relationship: TSMC supplies NVIDIA.
    expect(edges).toContainEqual(
      expect.objectContaining({ source: tsmcAsSupplier.id, target: nvidia.id }),
    );
    // Lam Research's relationship: TSMC is Lam Research's customer, so the arrow runs the other way.
    expect(edges).toContainEqual(
      expect.objectContaining({ source: lamResearch.id, target: tsmcAsSupplier.id }),
    );
  });

  it('gives a customer shared by several holdings, such as Apple, one node too', () => {
    const { nodes, edges } = buildGraphElements(sampleRelationships, sampleHoldings());

    const apple = nodes.filter((n) => n.name === 'Apple Inc.');
    expect(apple).toHaveLength(1);
    // Apple buys from both Cirrus Logic and STMicroelectronics.
    expect(edges.filter((e) => e.target === apple[0]!.id)).toHaveLength(2);
  });

  it('keeps Apple and Applied Materials apart', () => {
    const relationships: Relationship[] = [
      relationship(1, { id: 1, name: 'Apple Inc.', ticker: 'AAPL' }, { id: 2, name: 'Applied Materials, Inc.', ticker: 'AMAT' }),
    ];
    const { nodes } = buildGraphElements(relationships, []);

    expect(nodes.map((n) => n.name)).toEqual(['Apple Inc.', 'Applied Materials, Inc.']);
  });

  it('adds a holding with a company but no relationships yet as a node on its own', () => {
    const { nodes, edges } = buildGraphElements(sampleRelationships, sampleHoldings());

    const sony = nodes.find((n) => n.name === 'Sony Group Corporation');
    expect(sony).toBeDefined();
    expect(sony?.isHolding).toBe(true);
    expect(edges.some((e) => e.source === sony?.id || e.target === sony?.id)).toBe(false);
  });

  it('leaves out a holding that has not been analysed, or whose report was not found', () => {
    const holdings: Holding[] = [holding(1, 'IBM', null)];

    const { nodes } = buildGraphElements([], holdings);

    expect(nodes).toEqual([]);
  });
});

describe('showInReportUrl', () => {
  const url = 'https://www.sec.gov/Archives/edgar/data/937966/000162828026011378/asml-20251231.htm';

  it('uses the evidence whole when it is 10 words or fewer', () => {
    const evidence = 'Our top supplier is a ten-word example sentence here.';

    expect(showInReportUrl(url, evidence)).toBe(
      `${url}#:~:text=Our%20top%20supplier%20is%20a%20ten%2Dword%20example%20sentence%20here.`,
    );
  });

  it('uses the first 5 and last 5 words, comma-separated, for longer evidence', () => {
    const evidence =
      'The number of lithography systems we are able to produce is limited by the production capacity of one ' +
      'of our key suppliers, Carl Zeiss SMT, our sole supplier of lenses, mirrors, illuminators, collectors and ' +
      'other critical optical components (which we refer to as optics).';

    expect(showInReportUrl(url, evidence)).toBe(
      `${url}#:~:text=The%20number%20of%20lithography%20systems,we%20refer%20to%20as%20optics).`,
    );
  });

  it('writes a hyphen inside the evidence as %2D, not the "-" encodeURIComponent leaves it as', () => {
    const evidence =
      'Currently, we rely on suppliers such as Panasonic and Contemporary Amperex Technology Co. Limited (CATL) ' +
      'for these battery-grade cells.';

    expect(showInReportUrl(url, evidence)).toBe(
      `${url}#:~:text=Currently%2C%20we%20rely%20on%20suppliers,(CATL)%20for%20these%20battery%2Dgrade%20cells.`,
    );
  });
});

function relationship(
  id: number,
  company: Relationship['company'],
  counterparty: Relationship['counterparty'],
  type: Relationship['type'] = 'SUPPLIER',
): Relationship {
  return {
    id,
    company,
    counterparty,
    type,
    provides: null,
    evidence: 'Evidence sentence.',
    report: { form: '10-K', filingDate: '2026-01-01', url: 'https://www.sec.gov/example' },
  };
}

function holding(id: number, ticker: string, companyName: string | null): Holding {
  return {
    id,
    ticker,
    weight: null,
    companyName,
    status: companyName ? 'DONE' : 'WAITING',
    statusReason: null,
    reportType: null,
    reportDate: null,
    reportLink: null,
  };
}
