// Tests for buildGraphElements. The graph-building test uses docs/examples/relationships.json,
// the real sample data every AI story test uses.
import { describe, expect, it } from 'vitest';
import sampleRelationshipsJson from '../../../docs/examples/relationships.json';
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import {
  buildGraphElements,
  connectionsOf,
  flowProgressAt,
  displayName,
  focusOn,
  formatWeight,
  holdingNodeIds,
  holdingSize,
  type GraphNode,
  logoUrl,
  nodeLabel,
  plainName,
  relationshipSummary,
  shortName,
  sharedCompanies,
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
    const a = { id: 'a', source: 'supplier', target: 'focus', relationshipId: 1, reviewStatus: 'UNREVIEWED' as const };
    const b = { id: 'b', source: 'focus', target: 'customer', relationshipId: 2, reviewStatus: 'UNREVIEWED' as const };
    const between = { id: 'between', source: 'supplier', target: 'customer', relationshipId: 3, reviewStatus: 'UNREVIEWED' as const };

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

describe('flowProgressAt', () => {
  it('moves from supplier to customer and wraps after one cycle', () => {
    expect(flowProgressAt(0)).toBe(0);
    expect(flowProgressAt(550)).toBe(0.25);
    expect(flowProgressAt(2200)).toBe(0);
    expect(flowProgressAt(2250)).toBeCloseTo(50 / 2200);
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

  it('looks a company with no ticker up by its name without the legal ending', () => {
    const url = new URL(logoUrl({ name: 'Samsung Electronics Co., Ltd.', ticker: null }, token)!);
    expect(url.pathname).toBe('/name/Samsung%20Electronics');
  });

  it('asks Logo.dev for "not found" rather than its own letter, so the graph can show its own', () => {
    const url = new URL(logoUrl({ name: 'Carl Zeiss SMT', ticker: null }, token)!);
    expect(url.searchParams.get('fallback')).toBe('404');
  });

  it('keeps a name\'s other characters from breaking the address', () => {
    const url = logoUrl({ name: 'AT&T Inc.', ticker: null }, token)!;
    expect(new URL(url).pathname).toBe('/name/AT%26T');
    expect(new URL(url).searchParams.get('token')).toBe(token);
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
