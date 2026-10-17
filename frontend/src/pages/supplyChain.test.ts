// Tests for buildGraphElements. The graph-building test uses docs/examples/relationships.json,
// the real sample data every AI story test uses.
import { describe, expect, it } from 'vitest';
import sampleRelationshipsJson from '../../../docs/examples/relationships.json';
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import {
  buildGraphElements,
  dashOffsetAt,
  focusOn,
  formatWeight,
  type GraphNode,
  nodeLabel,
  plainName,
  shortName,
} from './supplyChain';

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
    lastAnalysed: null,
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
    lastAnalysed: null,
  };
}

describe('focusOn', () => {
  const { nodes, edges } = buildGraphElements(sampleRelationships, sampleHoldings());
  const idOf = (name: string) => nodes.find((n) => n.name === name)!.id;
  const nameOf = (id: string) => nodes.find((n) => n.id === id)!.name;

  it('keeps only NVIDIA, TSMC and Samsung, and their two arrows, when pointing at NVIDIA', () => {
    const focus = focusOn(idOf('NVIDIA Corporation'), edges);

    expect([...focus.nodeIds].map(nameOf).sort()).toEqual([
      'NVIDIA Corporation',
      'Samsung Electronics Co., Ltd.',
      'Taiwan Semiconductor Manufacturing Company Limited',
    ]);
    expect([...focus.edgeIds].sort()).toEqual(['relationship-1', 'relationship-2']);
  });

  it('keeps every holding TSMC supplies and the one it buys from when pointing at TSMC', () => {
    const focus = focusOn(idOf('Taiwan Semiconductor Manufacturing Company Limited'), edges);

    expect([...focus.nodeIds].map(nameOf).sort()).toEqual([
      'Advanced Micro Devices, Inc.',
      'Broadcom Inc.',
      'Lam Research Corporation',
      'NVIDIA Corporation',
      'QUALCOMM Incorporated',
      'Taiwan Semiconductor Manufacturing Company Limited',
    ]);
    expect(focus.edgeIds.size).toBe(5);
  });

  it('does not light an arrow between two neighbours that does not touch the company', () => {
    const a = { id: 'a', source: 'supplier', target: 'focus', relationshipId: 1 };
    const b = { id: 'b', source: 'focus', target: 'customer', relationshipId: 2 };
    const between = { id: 'between', source: 'supplier', target: 'customer', relationshipId: 3 };

    const focus = focusOn('focus', [a, b, between]);

    expect([...focus.edgeIds].sort()).toEqual(['a', 'b']);
    expect([...focus.nodeIds].sort()).toEqual(['customer', 'focus', 'supplier']);
  });

  it('lights a company with no arrows on its own', () => {
    const focus = focusOn('holding-10', edges);

    expect([...focus.nodeIds]).toEqual(['holding-10']);
    expect(focus.edgeIds.size).toBe(0);
  });
});

describe('dashOffsetAt', () => {
  // An edge is drawn from supplier to customer, and a smaller offset slides the dashes towards the customer.
  it('gets smaller as time passes, so the dashes travel from supplier to customer', () => {
    expect(dashOffsetAt(0)).toBe(0);
    expect(dashOffsetAt(250)).toBeLessThan(dashOffsetAt(0));
    expect(dashOffsetAt(500)).toBeLessThan(dashOffsetAt(250));
  });

  it('wraps around after one dash and gap, so it never grows without end', () => {
    for (const elapsed of [0, 1_000, 123_456, 86_400_000]) {
      expect(dashOffsetAt(elapsed)).toBeLessThanOrEqual(0);
      expect(dashOffsetAt(elapsed)).toBeGreaterThan(-13);
    }
  });
});

describe('a holding\'s weight on the graph', () => {
  it('is carried onto the holding\'s node, and left empty for other companies', () => {
    const holdings = sampleHoldings().map((h) => (h.ticker === 'NVDA' ? { ...h, weight: 18 } : h));
    const { nodes } = buildGraphElements(sampleRelationships, holdings);

    expect(nodes.find((n) => n.name === 'NVIDIA Corporation')?.weight).toBe(18);
    expect(nodes.find((n) => n.name === 'Taiwan Semiconductor Manufacturing Company Limited')?.weight).toBeNull();
  });

  it('is carried onto a holding with no relationships too', () => {
    const holdings = sampleHoldings().map((h) => (h.ticker === 'SONY' ? { ...h, weight: 7.5 } : h));
    const { nodes } = buildGraphElements(sampleRelationships, holdings);

    expect(nodes.find((n) => n.name === 'Sony Group Corporation')?.weight).toBe(7.5);
  });
});

// The names below are the real ones in docs/examples/relationships.json.
describe('plainName', () => {
  it('drops the legal ending', () => {
    expect(plainName('NVIDIA Corporation')).toBe('NVIDIA');
    expect(plainName('Apple Inc.')).toBe('Apple');
    expect(plainName('QUALCOMM Incorporated')).toBe('QUALCOMM');
    expect(plainName('STMicroelectronics N.V.')).toBe('STMicroelectronics');
  });

  it('drops the comma before the ending', () => {
    expect(plainName('Advanced Micro Devices, Inc.')).toBe('Advanced Micro Devices');
  });

  it('drops endings one word at a time, so "Co., Ltd." and "Company Limited" both go', () => {
    expect(plainName('Samsung Electronics Co., Ltd.')).toBe('Samsung Electronics');
    expect(plainName('Taiwan Semiconductor Manufacturing Company Limited')).toBe('Taiwan Semiconductor Manufacturing');
    expect(plainName('Contemporary Amperex Technology Co. Limited')).toBe('Contemporary Amperex Technology');
  });

  it('drops the SEC\'s registration mark after a slash', () => {
    expect(plainName('QUALCOMM INC/DE')).toBe('QUALCOMM');
  });

  it('keeps words that can tell companies apart, such as "Holding"', () => {
    expect(plainName('ASML Holding N.V.')).toBe('ASML Holding');
  });

  it('leaves a name with no legal ending as it is', () => {
    expect(plainName('Carl Zeiss SMT')).toBe('Carl Zeiss SMT');
    expect(plainName('Panasonic')).toBe('Panasonic');
  });

  it('never empties a name', () => {
    expect(plainName('Company')).toBe('Company');
  });
});

describe('shortName', () => {
  it('is the plain name when that fits', () => {
    expect(shortName('Broadcom Inc.')).toBe('Broadcom');
    expect(shortName('SK hynix Inc.')).toBe('SK hynix');
  });

  it('cuts a long name with an ellipsis', () => {
    const short = shortName('Contemporary Amperex Technology Co. Limited');
    expect(short).toBe('Contemporary\u2026');
    expect(short.length).toBeLessThanOrEqual(14);
  });
});

describe('formatWeight', () => {
  it('shows a weight as a percentage, without needless decimals', () => {
    expect(formatWeight(18)).toBe('18%');
    expect(formatWeight(12.5)).toBe('12.5%');
    expect(formatWeight(3.333)).toBe('3.33%');
  });

  it('is empty when there is no weight', () => {
    expect(formatWeight(null)).toBeNull();
  });
});

describe('nodeLabel', () => {
  const node = (overrides: Partial<GraphNode>): GraphNode => ({
    id: 'n',
    name: 'NVIDIA Corporation',
    ticker: 'NVDA',
    isHolding: true,
    weight: 18,
    ...overrides,
  });

  it('gives a holding its ticker and weight', () => {
    expect(nodeLabel(node({}))).toBe('NVDA 18%');
  });

  it('gives a holding with no weight its ticker alone', () => {
    expect(nodeLabel(node({ weight: null }))).toBe('NVDA');
  });

  it('gives another company with a ticker its ticker', () => {
    expect(
      nodeLabel(node({ name: 'Taiwan Semiconductor Manufacturing Company Limited', ticker: 'TSM', isHolding: false, weight: null })),
    ).toBe('TSM');
  });

  it('gives a company with no ticker a short name', () => {
    expect(nodeLabel(node({ name: 'Samsung Electronics Co., Ltd.', ticker: null, isHolding: false, weight: null }))).toBe(
      'Samsung Elect\u2026',
    );
  });
});
