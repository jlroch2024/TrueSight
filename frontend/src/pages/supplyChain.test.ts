// Tests for buildGraphElements. The graph-building test uses docs/examples/relationships.json,
// the real sample data every AI story test uses.
import { afterEach, describe, expect, it, vi } from 'vitest';
import sampleRelationshipsJson from '../../../docs/examples/relationships.json';
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import {
  buildGraphElements,
  CHAIN_COLOURS,
  chainColours,
  dashOffsetAt,
  connectionsOf,
  forgetLayout,
  displayName,
  easeTowards,
  FLOAT_REACH,
  floatOffset,
  flowLayout,
  focusOn,
  followTarget,
  formatWeight,
  holdingNodeIds,
  holdingSize,
  type GraphNode,
  lineCrosses,
  logoUrl,
  nodeLabel,
  plainName,
  readSavedLayout,
  relationshipSummary,
  saveLayout,
  shortName,
  sharedCompanies,
  spreadLayout,
  sharedSummary,
  withSharedSummaries,
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
    source: 'AI',
    criticality: null,
    criticalityEvidence: null,
    report: { form: '10-K', filingDate: '2026-01-01', url: 'https://www.sec.gov/example' },
    reviewStatus: 'UNREVIEWED',
    reviewedAt: null,
    reviewNote: null,
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
    const a = {
      id: 'a', source: 'supplier', target: 'focus', relationshipId: 1,
      reviewStatus: 'UNREVIEWED' as const, criticality: null, summary: 'Supplier supplies Focus',
    };
    const b = {
      id: 'b', source: 'focus', target: 'customer', relationshipId: 2,
      reviewStatus: 'UNREVIEWED' as const, criticality: null, summary: 'Focus supplies Customer',
    };
    const between = {
      id: 'between', source: 'supplier', target: 'customer', relationshipId: 3,
      reviewStatus: 'UNREVIEWED' as const, criticality: null, summary: 'Supplier supplies Customer',
    };

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

describe('lineCrosses', () => {
  const box = { x1: 40, x2: 60, y1: -10, y2: 10 };

  it('finds a line through a box, and none past it or stopping short of it', () => {
    expect(lineCrosses({ x: 0, y: 0 }, { x: 100, y: 0 }, box)).toBe(true);
    expect(lineCrosses({ x: 0, y: 30 }, { x: 100, y: 30 }, box)).toBe(false);
    expect(lineCrosses({ x: 0, y: 0 }, { x: 30, y: 0 }, box)).toBe(false);
    expect(lineCrosses({ x: 0, y: -50 }, { x: 100, y: 50 }, box)).toBe(true);
  });
});

describe('chainColours', () => {
  it("colours every arrow into or out of a holding in that holding's own colour, with the real sample relationships", () => {
    const { nodes, edges } = buildGraphElements(sampleRelationships, sampleHoldings());
    const { edges: colours, key } = chainColours(nodes, edges);
    const colourOf = new Map(key.map((entry) => [entry.id, entry.colour]));
    const holdings = new Set(nodes.filter((node) => node.isHolding).map((node) => node.id));

    expect(key.length).toBeGreaterThan(0);
    for (const edge of edges) {
      const holding = holdings.has(edge.target) ? edge.target : holdings.has(edge.source) ? edge.source : null;
      if (holding && colourOf.has(holding)) expect(colours.get(edge.id), edge.summary).toBe(colourOf.get(holding));
    }
    expect(new Set(key.map((entry) => entry.colour)).size).toBe(key.length);
  });

  it('hands the colours to the largest holdings first, equal weights by ticker, and leaves out a holding without arrows', () => {
    const nodes = [
      { id: 'b', name: 'Bee', ticker: 'BEE', isHolding: true, weight: 20 },
      { id: 'a', name: 'Ay', ticker: 'AY', isHolding: true, weight: 20 },
      { id: 'c', name: 'Cee', ticker: 'CEE', isHolding: true, weight: 35 },
      { id: 'd', name: 'Dee', ticker: 'DEE', isHolding: true, weight: null },
      { id: 'alone', name: 'Alone', ticker: 'AAA', isHolding: true, weight: 90 },
      { id: 's', name: 'Supplier', ticker: null, isHolding: false, weight: null },
    ];
    const edges = ['a', 'b', 'c', 'd'].map((id) => ({ id: `to-${id}`, source: 's', target: id }));

    expect(chainColours(nodes, edges).key).toEqual([
      { id: 'c', label: 'CEE', colour: CHAIN_COLOURS[0] },
      { id: 'a', label: 'AY', colour: CHAIN_COLOURS[1] },
      { id: 'b', label: 'BEE', colour: CHAIN_COLOURS[2] },
      { id: 'd', label: 'DEE', colour: CHAIN_COLOURS[3] },
    ]);
  });

  it("gives a supplier's supplier the colour of the holding its goods go on to, and a holding's supply to another holding the customer's colour", () => {
    const nodes = [
      { id: 'far', name: 'Far', ticker: null, isHolding: false, weight: null },
      { id: 'near', name: 'Near', ticker: null, isHolding: false, weight: null },
      { id: 'maker', name: 'Maker', ticker: 'MKR', isHolding: true, weight: null },
      { id: 'buyer', name: 'Buyer', ticker: 'BUY', isHolding: true, weight: null },
    ];
    const edges = [
      { id: 'far-near', source: 'far', target: 'near' },
      { id: 'near-buyer', source: 'near', target: 'buyer' },
      { id: 'maker-buyer', source: 'maker', target: 'buyer' },
      { id: 'maker-near', source: 'maker', target: 'near' },
    ];
    const { edges: colours, key } = chainColours(nodes, edges);
    const buyer = key.find((entry) => entry.id === 'buyer')!.colour;
    const maker = key.find((entry) => entry.id === 'maker')!.colour;

    expect(colours.get('far-near')).toBe(buyer);
    expect(colours.get('near-buyer')).toBe(buyer);
    expect(colours.get('maker-buyer')).toBe(buyer);
    expect(colours.get('maker-near')).toBe(maker);
  });

  it('never uses a colour twice: holdings smaller than the seven largest keep grey arrows', () => {
    // Holding h0 is the smallest, h8 the largest.
    const holdings = Array.from({ length: CHAIN_COLOURS.length + 2 }, (_, index) => ({
      id: `h${index}`, name: `Holding ${index}`, ticker: `H${index}`, isHolding: true, weight: index + 1,
    }));
    const nodes = [...holdings, { id: 's', name: 'Supplier', ticker: null, isHolding: false, weight: null }];
    const edges = holdings.map((holding) => ({ id: `to-${holding.id}`, source: 's', target: holding.id }));
    const { edges: colours, key } = chainColours(nodes, edges);

    expect(key).toHaveLength(CHAIN_COLOURS.length);
    expect(key[0]!.id).toBe(`h${CHAIN_COLOURS.length + 1}`);
    expect(new Set(colours.values()).size).toBe(CHAIN_COLOURS.length);
    expect(colours.has('to-h0')).toBe(false);
    expect(colours.has('to-h1')).toBe(false);
  });
});

describe('dashOffsetAt', () => {
  it('slides the dashes from supplier to customer, 20 px a second, and wraps after each dash and gap', () => {
    expect(dashOffsetAt(0)).toBe(0);
    expect(dashOffsetAt(500)).toBe(-10);
    expect(dashOffsetAt(1000)).toBeCloseTo(-7);
    expect(dashOffsetAt(650)).toBeCloseTo(0);
  });
});

describe('review status on graph edges', () => {
  it('carries approved and unreviewed statuses onto their edges', () => {
    const approved = relationship(31, { id: 1, name: 'Example Corp', ticker: 'EX' }, { id: 2, name: 'Supplier Ltd', ticker: null });
    const unreviewed = { ...approved, id: 32, reviewStatus: 'UNREVIEWED' as const };
    const { edges } = buildGraphElements([
      { ...approved, reviewStatus: 'APPROVED', reviewedAt: '2026-10-14T12:00:00Z', reviewNote: 'Checked.' },
      unreviewed,
    ], []);

    expect(edges).toContainEqual(expect.objectContaining({ relationshipId: 31, reviewStatus: 'APPROVED' }));
    expect(edges).toContainEqual(expect.objectContaining({ relationshipId: 32, reviewStatus: 'UNREVIEWED' }));
  });

  it('carries source criticality onto the matching graph edge', () => {
    const source = sampleRelationships[15]!;
    const { edges } = buildGraphElements([
      { ...source, criticality: 'SOLE_SOURCE', criticalityEvidence: source.evidence },
      { ...source, id: source.id + 100, criticality: null, criticalityEvidence: null },
    ], []);

    expect(edges).toContainEqual(expect.objectContaining({ relationshipId: source.id, criticality: 'SOLE_SOURCE' }));
    expect(edges).toContainEqual(expect.objectContaining({ relationshipId: source.id + 100, criticality: null }));
  });

  it('carries the relationship sentence onto its arrow', () => {
    const { edges } = buildGraphElements(sampleRelationships, sampleHoldings());

    expect(edges.find((edge) => edge.relationshipId === sampleRelationships[0]!.id)?.summary).toBe(
      'Taiwan Semiconductor Manufacturing supplies NVIDIA: semiconductor wafers',
    );
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

describe('logoUrl', () => {
  const token = 'pk_test_token';

  it('looks a company up by its ticker when it has one', () => {
    const url = new URL(logoUrl({ name: 'NVIDIA Corporation', ticker: 'NVDA' }, token)!);
    expect(url.origin).toBe('https://img.logo.dev');
    expect(url.pathname).toBe('/ticker/NVDA');
    expect(url.searchParams.get('token')).toBe(token);
  });

  it('looks a company with no ticker up by the website domain the backend checked is its own', () => {
    const url = new URL(logoUrl({ name: 'Amkor Technology, Inc.', ticker: null }, token, 'amkor.com')!);
    expect(url.pathname).toBe('/amkor.com');
    expect(url.searchParams.get('token')).toBe(token);
  });

  it('never asks Logo.dev by name: without a ticker or a checked domain, the graph shows the letter', () => {
    expect(logoUrl({ name: 'King Yuan Electronics Company', ticker: null }, token)).toBeNull();
  });

  it('asks Logo.dev for "not found" rather than its own letter, so the graph can show its own', () => {
    const url = new URL(logoUrl({ name: 'Amkor Technology, Inc.', ticker: null }, token, 'amkor.com')!);
    expect(url.searchParams.get('fallback')).toBe('404');
  });

  it('is empty without a token, so the graph shows letters instead', () => {
    expect(logoUrl({ name: 'NVIDIA Corporation', ticker: 'NVDA' }, '')).toBeNull();
  });
});

describe('holdingSize', () => {
  it('is the usual tile size for a holding with no weight, or a weight of 0', () => {
    expect(holdingSize(null)).toBe(44);
    expect(holdingSize(0)).toBe(44);
  });

  it('grows with the square root of the weight', () => {
    expect(holdingSize(4)).toBe(62);
    expect(holdingSize(25)).toBe(89);
    expect(holdingSize(18)).toBeCloseTo(82.18, 2);
  });

  it('never grows past 110 pixels', () => {
    expect(holdingSize(60)).toBe(110);
    expect(holdingSize(100)).toBe(110);
  });

  it('gives a bigger position a bigger tile', () => {
    expect(holdingSize(40)).toBeGreaterThan(holdingSize(10));
  });
});

describe('sharedCompanies', () => {
  const { nodes } = buildGraphElements(sampleRelationships, sampleHoldings());
  const find = (name: string) => sharedCompanies(sampleRelationships, nodes).find((c) => c.name === name);

  it('shows TSMC as supplying 4 holdings, and Apple as buying from 2 holdings, with the sample data', () => {
    const tsmc = find('Taiwan Semiconductor Manufacturing Company Limited')!;
    const apple = find('Apple Inc.')!;

    expect(sharedSummary(tsmc)).toBe('Supplies 4 Holdings');
    expect(sharedSummary(apple)).toBe('Buys From 2 Holdings');
  });

  it('lists the most shared first, and companies of equal rank in alphabetical order', () => {
    const names = sharedCompanies(sampleRelationships, nodes).map((c) => c.name);

    expect(names).toEqual([
      'Taiwan Semiconductor Manufacturing Company Limited',
      'Apple Inc.',
      'GLOBALFOUNDRIES Inc.',
      'Samsung Electronics Co., Ltd.',
    ]);
  });

  it('does not list a company that serves only one holding', () => {
    const names = sharedCompanies(sampleRelationships, nodes).map((c) => c.name);

    // Micron, SK hynix, Panasonic, CATL and Carl Zeiss each appear for one holding only.
    for (const single of ['Micron Technology, Inc.', 'SK hynix Inc.', 'Panasonic', 'Carl Zeiss SMT']) {
      expect(names).not.toContain(single);
    }
    expect(names).not.toContain('Contemporary Amperex Technology Co. Limited');
  });

  it('never lists a holding, even when several other holdings name it', () => {
    const [nvidiaRelationship] = sampleRelationships;
    const nvidia = nvidiaRelationship!.company;
    const namedByTwoHoldings: Relationship[] = [
      { ...sampleRelationships[2]!, id: 101, counterparty: nvidia }, // AMD names NVIDIA as a supplier
      { ...sampleRelationships[4]!, id: 102, counterparty: nvidia }, // QUALCOMM does too
    ];

    const names = sharedCompanies([...sampleRelationships, ...namedByTwoHoldings], nodes).map((c) => c.name);

    expect(names).not.toContain('NVIDIA Corporation');
  });

  it('counts holdings, not arrows', () => {
    // NVIDIA names TSMC twice (two arrows, one holding), and Tesla names Panasonic twice.
    const doubled: Relationship[] = [
      { ...sampleRelationships[0]!, id: 103 },
      { ...sampleRelationships[7]!, id: 104 },
    ];

    const shared = sharedCompanies([...sampleRelationships, ...doubled], nodes);

    expect(shared.find((c) => c.name === 'Taiwan Semiconductor Manufacturing Company Limited')!.suppliesHoldings).toBe(4);
    expect(shared.map((c) => c.name)).not.toContain('Panasonic');
  });

  it('gives a company shared on both sides both labels, and counts each side on its own', () => {
    const counterparty = { id: 900, name: 'Both Ways Ltd', ticker: null };
    const holdingCompany = (i: number) => sampleRelationships[i]!.company;
    const both: Relationship[] = [
      { ...sampleRelationships[0]!, id: 201, counterparty, type: 'SUPPLIER', company: holdingCompany(0) },
      { ...sampleRelationships[0]!, id: 202, counterparty, type: 'SUPPLIER', company: holdingCompany(2) },
      { ...sampleRelationships[0]!, id: 203, counterparty, type: 'CUSTOMER', company: holdingCompany(7) },
      { ...sampleRelationships[0]!, id: 204, counterparty, type: 'CUSTOMER', company: holdingCompany(9) },
      { ...sampleRelationships[0]!, id: 205, counterparty, type: 'CUSTOMER', company: holdingCompany(12) },
    ];

    const bothWays = sharedCompanies(both, nodes).find((c) => c.name === 'Both Ways Ltd')!;

    expect(bothWays).toMatchObject({ suppliesHoldings: 2, buysFromHoldings: 3 });
    expect(sharedSummary(bothWays)).toBe('Supplies 2 Holdings · Buys From 3 Holdings');
  });

  it('leaves out the side held by only one holding', () => {
    const tsmc = find('Taiwan Semiconductor Manufacturing Company Limited')!;

    // TSMC also buys from Lam Research, but that is a single holding.
    expect(tsmc.buysFromHoldings).toBe(1);
    expect(sharedSummary(tsmc)).not.toContain('Buys From');
  });

  it('lists nothing when no company is shared', () => {
    expect(sharedCompanies([sampleRelationships[0]!], buildGraphElements([sampleRelationships[0]!], []).nodes)).toEqual([]);
  });
});

describe('withSharedSummaries', () => {
  it('gives the summary to each shared company and leaves every other node as it was', () => {
    const { nodes } = buildGraphElements(sampleRelationships, sampleHoldings());

    const marked = withSharedSummaries(nodes, sharedCompanies(sampleRelationships, nodes));

    const withSummary = marked.filter((n) => n.sharedSummary !== undefined).map((n) => n.name).sort();
    expect(withSummary).toEqual([
      'Apple Inc.',
      'GLOBALFOUNDRIES Inc.',
      'Samsung Electronics Co., Ltd.',
      'Taiwan Semiconductor Manufacturing Company Limited',
    ]);
    expect(marked.find((n) => n.name === 'NVIDIA Corporation')).toBe(nodes.find((n) => n.name === 'NVIDIA Corporation'));
  });
});

describe('connectionsOf', () => {
  const names = (list: { company: { name: string } }[]) => list.map((c) => c.company.name);

  it("lists a holding's suppliers from its own report", () => {
    const { suppliers, customers } = connectionsOf('1', sampleRelationships); // NVIDIA

    expect(names(suppliers)).toEqual(['Taiwan Semiconductor Manufacturing Company Limited', 'Samsung Electronics Co., Ltd.']);
    expect(customers).toEqual([]);
  });

  it("lists a holding's customers from its own report", () => {
    const { suppliers, customers } = connectionsOf('7', sampleRelationships); // Cirrus Logic

    expect(suppliers).toEqual([]);
    expect(names(customers)).toEqual(['Apple Inc.']);
  });

  it('turns the relationship round for a company named in other reports', () => {
    // TSMC supplies NVIDIA, AMD, QUALCOMM and Broadcom, and buys from Lam Research.
    const { suppliers, customers } = connectionsOf('11', sampleRelationships);

    expect(names(customers)).toEqual([
      'NVIDIA Corporation',
      'Advanced Micro Devices, Inc.',
      'QUALCOMM Incorporated',
      'Broadcom Inc.',
    ]);
    expect(names(suppliers)).toEqual(['Lam Research Corporation']);
  });

  it('lists a company once, even when two relationships link the same pair', () => {
    const doubled = [...sampleRelationships, { ...sampleRelationships[0]!, id: 999 }];

    expect(names(connectionsOf('1', doubled).suppliers)).toHaveLength(2);
  });
});

describe('holdingNodeIds', () => {
  it("finds each analysed holding's node, and leaves out one not on the graph", () => {
    const holdings = [
      ...sampleHoldings(),
      { ...sampleHoldings()[0]!, id: 50, ticker: 'NEW', companyName: null, status: 'WAITING' as const },
    ];
    const { nodes } = buildGraphElements(sampleRelationships, holdings);

    const ids = holdingNodeIds(holdings, nodes);

    expect(ids.get(1)).toBe('1'); // NVIDIA, a company in the relationships
    expect(ids.get(10)).toBe('holding-10'); // Sony, a holding with no relationships
    expect(ids.has(50)).toBe(false);
  });
});

describe('relationshipSummary', () => {
  it('puts the supplier first, with plain names and what is supplied', () => {
    expect(relationshipSummary(sampleRelationships[0]!)).toBe(
      'Taiwan Semiconductor Manufacturing supplies NVIDIA: semiconductor wafers',
    );
  });

  it('turns a customer relationship round, and leaves out what is supplied when the report does not say', () => {
    expect(relationshipSummary(sampleRelationships[14]!)).toBe('Cirrus Logic supplies Apple');
  });
});

describe('displayName', () => {
  // The SEC's own names for the holdings in the live Semiconductors portfolio, and names the reports give.
  it("turns the SEC's all-capital names into Title Case, without the legal ending", () => {
    expect(displayName('NVIDIA CORP')).toBe('Nvidia');
    expect(displayName('MICROSOFT CORP')).toBe('Microsoft');
  });

  it('keeps short words in capitals, as they are usually acronyms', () => {
    expect(displayName('ASML HOLDING NV')).toBe('ASML Holding');
    expect(displayName('ADVANCED MICRO DEVICES INC')).toBe('Advanced Micro Devices');
  });

  it('writes a trailing COM as the web address it is', () => {
    expect(displayName('AMAZON COM INC')).toBe('Amazon.com');
  });

  it('keeps a name already written in mixed case as it is, only without the legal ending', () => {
    expect(displayName('Apple Inc.')).toBe('Apple');
    expect(displayName('NVIDIA Corporation')).toBe('NVIDIA');
    expect(displayName('Taiwan Semiconductor Manufacturing Company Limited')).toBe('Taiwan Semiconductor Manufacturing');
  });
});

describe('floatOffset', () => {
  const ids = ['nvidia', 'amd', 'supplier-1', 'supplier-2', 'Taiwan Semiconductor'];
  const moments = Array.from({ length: 200 }, (_, index) => index * 157);

  it('keeps every company within a few pixels of where it rests, and a holding a little closer', () => {
    for (const id of ids) {
      for (const at of moments) {
        expect(Math.hypot(floatOffset(id, at, false).x, floatOffset(id, at, false).y)).toBeLessThanOrEqual(FLOAT_REACH);
        const holding = floatOffset(id, at, true);
        expect(Math.hypot(holding.x, holding.y)).toBeLessThan(FLOAT_REACH);
      }
    }
  });

  it('moves smoothly: less than a pixel from one frame to the next', () => {
    for (const id of ids) {
      for (const at of moments) {
        const now = floatOffset(id, at, false);
        const next = floatOffset(id, at + 16, false);
        expect(Math.hypot(next.x - now.x, next.y - now.y)).toBeLessThan(1);
      }
    }
  });

  it('gives each company its own path, the same every time', () => {
    const paths = ids.map((id) => JSON.stringify(moments.slice(0, 10).map((at) => floatOffset(id, at, false))));

    expect(new Set(paths).size).toBe(ids.length);
    expect(floatOffset('amd', 4321, false)).toEqual(floatOffset('amd', 4321, false));
  });
});

describe('elastic dragging', () => {
  it("moves a dragged company's neighbour by a share of the drag, from where the neighbour rests", () => {
    expect(followTarget({ x: 100, y: 50 }, { x: 0, y: 0 }, { x: 200, y: -100 }, 0.35)).toEqual({ x: 170, y: 15 });
  });

  it('eases a fraction of the way each frame, and arrives once within half a pixel', () => {
    const first = easeTowards({ x: 0, y: 0 }, { x: 100, y: 0 }, 0.18);
    expect(first).toEqual({ position: { x: 18, y: 0 }, arrived: false });
    expect(easeTowards({ x: 99.7, y: 0 }, { x: 100, y: 0 })).toEqual({ position: { x: 100, y: 0 }, arrived: true });
  });

  it('settles within about a second of frames', () => {
    let position = { x: 0, y: 0 };
    let frames = 0;
    for (let arrived = false; !arrived; frames++) ({ position, arrived } = easeTowards(position, { x: 300, y: 0 }));
    expect(frames).toBeLessThan(60);
  });
});

describe('flowLayout', () => {
  const { nodes, edges } = buildGraphElements(sampleRelationships, sampleHoldings());
  const ids = nodes.map((node) => node.id);
  const layout = () => flowLayout(ids, edges, () => 60);

  it('puts every supplier left of its customer, with the real sample relationships', () => {
    const positions = layout();

    for (const edge of edges) {
      expect(positions.get(edge.source)!.x).toBeLessThan(positions.get(edge.target)!.x);
    }
  });

  it('places every company, and never two of one group in the same spot', () => {
    const positions = layout();
    // NVIDIA's group: everyone linked to it, however indirectly. (Separate groups each start at the left edge; the
    // graph packs them apart afterwards.)
    const group = new Set([nodes.find((node) => node.name === 'NVIDIA Corporation')!.id]);
    for (let grew = true; grew; ) {
      grew = false;
      for (const { source, target } of edges) {
        if (group.has(source) !== group.has(target)) {
          group.add(source);
          group.add(target);
          grew = true;
        }
      }
    }
    const spots = new Set([...group].map((id) => `${positions.get(id)!.x},${positions.get(id)!.y}`));

    expect(positions.size).toBe(ids.length);
    expect(group.size).toBeGreaterThan(5);
    expect(spots.size).toBe(group.size);
  });

  it("puts a company that only supplies next to its customer, not far out on the left", () => {
    const positions = layout();
    const lam = nodes.find((node) => node.name === 'Lam Research Corporation')!.id; // supplies TSMC, Samsung, Micron, SK hynix
    const zeiss = nodes.find((node) => node.name === 'Carl Zeiss SMT')!.id; // supplies ASML only
    const asml = nodes.find((node) => node.name === 'ASML Holding N.V.')!.id;

    expect(positions.get(asml)!.x - positions.get(zeiss)!.x).toBe(200);
    expect(positions.get(lam)!.x).toBe(0);
  });

  it('lays out each separate group from the left on its own', () => {
    const positions = layout();
    const tesla = nodes.find((node) => node.name === 'Tesla, Inc.')!.id;

    // Tesla's group (Panasonic and CATL supply it) starts at the left edge too: two columns, Tesla in the second.
    expect(positions.get(tesla)!.x).toBe(200);
  });

  it('lets only the arrow that closes a loop point backwards', () => {
    const loop = [
      { source: 'a', target: 'b' },
      { source: 'b', target: 'c' },
      { source: 'c', target: 'a' },
    ];

    const positions = flowLayout(['a', 'b', 'c'], loop, () => 60);
    const backwards = loop.filter(({ source, target }) => positions.get(source)!.x >= positions.get(target)!.x);

    expect(backwards).toEqual([{ source: 'c', target: 'a' }]);
  });

  it('gives a taller company more room in its column', () => {
    const positions = flowLayout(['s', 'big', 'small'], [
      { source: 's', target: 'big' },
      { source: 's', target: 'small' },
    ], (id) => (id === 'big' ? 160 : 60));

    const gap = Math.abs(positions.get('big')!.y - positions.get('small')!.y);
    expect(gap).toBe(160 / 2 + 20 + 60 / 2);
  });
});

describe('spreadLayout', () => {
  const { nodes, edges } = buildGraphElements(sampleRelationships, sampleHoldings());
  const ids = nodes.map((node) => node.id);
  const box = { width: 120, height: 90, below: 20 };
  // How much room a group of companies takes, from the boxes around them.
  const extent = (positions: Map<string, { x: number; y: number }>, group: string[]) => {
    const xs = group.map((id) => positions.get(id)!.x);
    const ys = group.map((id) => positions.get(id)!.y + box.below);
    return {
      width: Math.max(...xs) - Math.min(...xs) + box.width,
      height: Math.max(...ys) - Math.min(...ys) + box.height,
    };
  };
  // One holding with 14 suppliers and 2 customers: the case that used to stand in one tall line.
  const suppliers = Array.from({ length: 14 }, (_, index) => `supplier-${index}`);
  const crowded = {
    ids: [...suppliers, 'holding', 'customer-a', 'customer-b'],
    edges: [
      ...suppliers.map((id) => ({ source: id, target: 'holding' })),
      { source: 'holding', target: 'customer-a' },
      { source: 'holding', target: 'customer-b' },
    ],
  };

  it('still puts every supplier left of its customer, with the real sample relationships', () => {
    const positions = spreadLayout(ids, edges, () => box, 1.8);

    for (const edge of edges) {
      expect(positions.get(edge.source)!.x).toBeLessThan(positions.get(edge.target)!.x);
    }
  });

  it('never lets two companies overlap', () => {
    for (const [graphIds, graphEdges] of [
      [ids, edges],
      [crowded.ids, crowded.edges],
    ] as const) {
      const positions = spreadLayout([...graphIds], [...graphEdges], () => box, 1.8);
      // Separate groups each start from the same corner; the graph packs them apart afterwards.
      for (const a of graphIds) {
        for (const b of spreadGroupOf(graphEdges, a)) {
          if (a >= b) continue;
          const pa = positions.get(a)!;
          const pb = positions.get(b)!;
          const apart = Math.abs(pa.x - pb.x) >= box.width || Math.abs(pa.y - pb.y) >= box.height;
          expect(apart, `${a} and ${b}`).toBe(true);
        }
      }
    }
  });

  it('spreads a crowded column over several lanes, so the graph takes the shape of the frame, not a tall line', () => {
    const positions = spreadLayout(crowded.ids, crowded.edges, () => box, 1.8);
    const lanes = new Set(suppliers.map((id) => positions.get(id)!.x));
    const { width, height } = extent(positions, crowded.ids);

    expect(lanes.size).toBeGreaterThan(1);
    expect(width / height).toBeGreaterThan(1.8 / 1.5);
    expect(width / height).toBeLessThan(1.8 * 1.5);
  });

  it('comes out wider for a wider frame', () => {
    const wide = extent(spreadLayout(crowded.ids, crowded.edges, () => box, 3), crowded.ids);
    const tall = extent(spreadLayout(crowded.ids, crowded.edges, () => box, 0.8), crowded.ids);

    expect(wide.width / wide.height).toBeGreaterThan(tall.width / tall.height);
  });

  // Whether any straight arrow passes through a company that is not one of its two ends.
  const crossings = (
    positions: Map<string, { x: number; y: number }>,
    graphEdges: readonly { source: string; target: string }[],
  ) =>
    graphEdges.flatMap(({ source, target }) =>
      [...positions.keys()]
        .filter((id) => id !== source && id !== target && spreadGroupOf(graphEdges, source).has(id))
        .filter((id) => {
          const point = positions.get(id)!;
          const middle = point.y + box.below;
          return lineCrosses(positions.get(source)!, positions.get(target)!, {
            x1: point.x - box.width / 2, x2: point.x + box.width / 2,
            y1: middle - box.height / 2, y2: middle + box.height / 2,
          });
        })
        .map((id) => `${source} > ${target} through ${id}`),
    );

  it("keeps two suppliers of one company off each other's arrow (item 55)", () => {
    // Contemporary Amperex and Panasonic both supply Tesla: placed side by side, one arrow ran through the other.
    const twoSuppliers = [
      { source: 'catl', target: 'tesla' },
      { source: 'panasonic', target: 'tesla' },
    ];
    const positions = spreadLayout(['catl', 'panasonic', 'tesla'], twoSuppliers, () => box, 3);

    expect(crossings(positions, twoSuppliers)).toEqual([]);
  });

  it('draws no straight arrow through another company, with the real sample relationships', () => {
    expect(crossings(spreadLayout(ids, edges, () => box, 1.8), edges)).toEqual([]);
  });

  it('lays the same graph out the same way every time', () => {
    const first = spreadLayout(ids, edges, () => box, 1.8);
    const second = spreadLayout(ids, edges, () => box, 1.8);

    expect([...second.entries()]).toEqual([...first.entries()]);
  });
});

/** Every company linked to the given one, however indirectly, and the company itself. */
function spreadGroupOf(edges: readonly { source: string; target: string }[], start: string): Set<string> {
  const group = new Set([start]);
  for (let grew = true; grew; ) {
    grew = false;
    for (const { source, target } of edges) {
      if (group.has(source) !== group.has(target)) {
        group.add(source);
        group.add(target);
        grew = true;
      }
    }
  }
  return group;
}

describe('saved layouts', () => {
  afterEach(() => {
    window.sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('keeps what the user did to a portfolio\'s graph for the browser tab, and forgets it on request', () => {
    saveLayout('truesight.graph.7', { positions: { nvidia: { x: 10, y: 20 } }, zoom: 1.5, pan: { x: 3, y: 4 } });

    expect(readSavedLayout('truesight.graph.7')).toEqual({
      positions: { nvidia: { x: 10, y: 20 } },
      zoom: 1.5,
      pan: { x: 3, y: 4 },
    });
    expect(readSavedLayout('truesight.graph.8')).toBeNull();

    forgetLayout('truesight.graph.7');
    expect(readSavedLayout('truesight.graph.7')).toBeNull();
  });

  it('reads nothing from a damaged entry, or when the browser refuses storage', () => {
    window.sessionStorage.setItem('truesight.graph.7', 'not json');
    expect(readSavedLayout('truesight.graph.7')).toBeNull();

    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage is switched off');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage is switched off');
    });
    expect(() => saveLayout('truesight.graph.7', { positions: {} })).not.toThrow();
    expect(readSavedLayout('truesight.graph.7')).toBeNull();
  });
});
