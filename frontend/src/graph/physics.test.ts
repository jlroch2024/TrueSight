// The living graph's model and physics, on the real sample relationships in docs/examples/relationships.json.
import { describe, expect, it } from 'vitest';
import sampleRelationshipsJson from '../../../docs/examples/relationships.json';
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import { buildGraphElements, withSharedSummaries, sharedCompanies } from '../pages/supplyChain';
import {
  boxOf,
  buildLiveGraph,
  eventLabel,
  labelScale,
  labelZoom,
  risksFrom,
  chainOf,
  clip,
  companyPresence,
  drawnShare,
  holdingGrowth,
  holdingTile,
  hueOf,
  initialsOf,
  neighbourhood,
} from './model';
import { BREATHING_ROOM, boundsOf, createPhysics, restoreSettled, settle, settleForView } from './physics';
import { fitCamera } from './camera';
import { lineCrosses } from '../pages/supplyChain';

// The holdings the sample relationships belong to, as the Supply Chain page would have them.
function sampleHoldings(): Holding[] {
  const holdingOf: [string, string, number][] = [
    ['NVDA', 'NVIDIA Corporation', 18],
    ['AMD', 'Advanced Micro Devices, Inc.', 12],
    ['QCOM', 'QUALCOMM Incorporated', 8],
    ['TSLA', 'Tesla, Inc.', 15],
    ['AVGO', 'Broadcom Inc.', 10],
    ['LRCX', 'Lam Research Corporation', 9],
    ['CRUS', 'Cirrus Logic, Inc.', 4],
    ['ASML', 'ASML Holding N.V.', 11],
    ['STM', 'STMicroelectronics N.V.', 6],
    ['SONY', 'Sony Group Corporation', 7],
  ];
  return holdingOf.map(([ticker, companyName, weight], i) => ({
    id: i + 1,
    ticker,
    weight,
    companyName,
    status: 'DONE',
    statusReason: null,
    reportType: '10-K',
    reportDate: '2026-01-01',
    reportLink: 'https://www.sec.gov/example',
    lastAnalysed: null,
  })) as Holding[];
}

function sampleGraph() {
  const relationships = sampleRelationshipsJson as Relationship[];
  const { nodes, edges } = buildGraphElements(relationships, sampleHoldings());
  return buildLiveGraph(withSharedSummaries(nodes, sharedCompanies(relationships, nodes)), edges);
}

function settled() {
  const graph = sampleGraph();
  const physics = createPhysics(graph.nodes, graph.edges);
  settle(physics);
  return { graph, physics };
}

describe('the living graph model', () => {
  it('makes a bigger holding a bigger tile, within limits', () => {
    expect(holdingTile(20)).toBeGreaterThan(holdingTile(5));
    expect(holdingTile(null)).toBe(holdingTile(0));
    expect(holdingTile(10_000)).toBe(62);
  });

  it('gives a company without a logo initials and a colour of its own, the same every time', () => {
    expect(initialsOf('Taiwan Semiconductor Manufacturing')).toBe('TS');
    expect(initialsOf('The Boeing Company')).toBe('BC');
    expect(initialsOf('Fabrinet')).toBe('FA');
    expect(hueOf('Fabrinet')).toBe(hueOf('Fabrinet'));
    expect(hueOf('Fabrinet')).not.toBe(hueOf('Foxconn'));
  });

  it('cuts a long name short with an ellipsis', () => {
    expect(clip('Taiwan Semiconductor Manufacturing Company', 24)).toBe('Taiwan Semiconductor Ma…');
    expect(clip('Tesla')).toBe('Tesla');
  });

  it('builds every company and relationship from the real sample, holdings in their chain colour', () => {
    const graph = sampleGraph();

    expect(graph.nodes.length).toBeGreaterThan(10);
    expect(graph.edges.length).toBeGreaterThan(10);
    for (const chain of graph.chains) {
      expect(graph.nodes.find((node) => node.id === chain.id)!.colour).toBe(chain.colour);
    }
    const holding = graph.nodes.find((node) => node.ticker === 'NVDA')!;
    expect(holding.subtitle).toBe('NVDA · 18%');
  });

  it('lights a company with its direct neighbours, and a chain by its colour', () => {
    const graph = sampleGraph();
    const nvidia = graph.nodes.find((node) => node.ticker === 'NVDA')!;
    const around = neighbourhood(nvidia.id, graph.edges);

    expect(around.nodeIds.has(nvidia.id)).toBe(true);
    for (const edgeId of around.edgeIds) {
      const edge = graph.edges.find((item) => item.id === edgeId)!;
      expect([edge.source.id, edge.target.id]).toContain(nvidia.id);
    }
    const chain = chainOf(nvidia.colour!, graph.edges);
    expect(chain.nodeIds.has(nvidia.id)).toBe(true);
    expect(chain.edgeIds.size).toBeGreaterThan(0);
  });
});

describe('disruptions and zoom', () => {
  // An event as the Alerts page receives it, reaching one holding through a relationship (or none).
  const event = (status: 'NEW' | 'DISMISSED', severity: 'LOW' | 'MEDIUM' | 'HIGH', relationshipId: number | null) => ({
    status,
    headline: {
      article: {
        title: `Headline ${severity}`,
        source: null,
        publishedAt: '2026-10-20T00:00:00Z',
        url: 'https://example.com',
      },
      assessment: {
        eventType: 'SUPPLY_SHORTAGE' as const,
        severity,
        confidence: 'HIGH' as const,
        summary: null,
        reason: '',
      },
    },
    holdings: [{
      holdingId: 1,
      ticker: 'NVDA',
      weight: null,
      supplierName: relationshipId === null ? null : 'TSMC',
      customerName: relationshipId === null ? null : 'NVIDIA',
      reviewStatus: null,
      relationshipId,
    }],
  });

  it('marks each link an open alert runs through with its most serious disruption', () => {
    const risks = risksFrom([event('NEW', 'LOW', 7), event('NEW', 'HIGH', 7), event('NEW', 'MEDIUM', 7)]);

    expect(risks.get(7)).toEqual({ severity: 'HIGH', eventType: 'SUPPLY_SHORTAGE', title: 'Headline HIGH' });
  });

  it('marks nothing for a dismissed alert, or one about a holding itself', () => {
    expect(risksFrom([event('DISMISSED', 'HIGH', 7), event('NEW', 'HIGH', null)]).size).toBe(0);
  });

  it('marks every relationship an event reaches', () => {
    const first = event('NEW', 'HIGH', 7);
    const secondHolding = { ...first.holdings[0]!, holdingId: 2, ticker: 'TSM', relationshipId: 8 };
    const risks = risksFrom([{ ...first, holdings: [first.holdings[0]!, secondHolding] }]);

    expect([...risks.keys()].sort()).toEqual([7, 8]);
    expect(risks.get(8)).toEqual({ severity: 'HIGH', eventType: 'SUPPLY_SHORTAGE', title: 'Headline HIGH' });
  });

  it('reads an event type as words', () => {
    expect(eventLabel('PRODUCTION_STOP')).toBe('Production Stop');
    expect(eventLabel('HIGH')).toBe('High');
  });

  it("fades suppliers' and customers' labels first when zooming out, then holdings', until none is left", () => {
    expect(labelZoom(1, false)).toBe(1);
    expect(labelZoom(0.3, false)).toBe(0);
    expect(labelZoom(0.3, true)).toBe(1);
    expect(labelZoom(0.15, true)).toBe(0);
    expect(labelZoom(0.36, false)).toBeGreaterThan(0);
    expect(labelZoom(0.36, false)).toBeLessThan(1);
  });
});

describe('the zoom drill-down', () => {
  it('shrinks a holding with the graph while every company shows, then at half the rate, and not past a limit', () => {
    // On screen a holding is its size × zoom × growth: from 0.45 down to a quarter of that zoom, it halves.
    expect(holdingGrowth(2)).toBe(1);
    expect(holdingGrowth(1)).toBe(1);
    expect(holdingGrowth(0.45)).toBe(1);
    expect((0.45 / 4) * holdingGrowth(0.45 / 4)).toBeCloseTo(0.45 / 2);
    expect(holdingGrowth(0.01)).toBe(2.6);
  });

  it('lets suppliers and customers recede as the view zooms out, and come back as it zooms in', () => {
    expect(companyPresence(0.6)).toBe(1);
    expect(companyPresence(0.3)).toBeGreaterThan(0);
    expect(companyPresence(0.3)).toBeLessThan(1);
    expect(companyPresence(0.15)).toBe(0);
    expect(drawnShare({ isHolding: false }, 1)).toBe(1);
    expect(drawnShare({ isHolding: false }, 0.1)).toBeCloseTo(0.55);
  });

  it('keeps holdings and the others in proportion while all show, then holdings larger the further it zooms out', () => {
    const ratio = (k: number) => drawnShare({ isHolding: true }, k) / drawnShare({ isHolding: false }, k);

    expect(ratio(1)).toBe(1);
    expect(ratio(0.5)).toBe(1);
    expect(ratio(0.3)).toBeGreaterThan(1);
    expect(ratio(0.15)).toBeGreaterThan(ratio(0.3));
  });
});

describe('the living graph physics', () => {
  {
    it("leaves no two companies' tiles or labels overlapping", () => {
      const { graph } = settled();
      const overlaps: string[] = [];
      for (let i = 0; i < graph.nodes.length; i++) {
        for (let j = i + 1; j < graph.nodes.length; j++) {
          const a = graph.nodes[i]!;
          const b = graph.nodes[j]!;
          const boxA = boxOf(a, a.x, a.y);
          const boxB = boxOf(b, b.x, b.y);
          // A little slack under the breathing room: the physics stops before every last pixel is settled.
          const slack = BREATHING_ROOM / 2;
          if (
            Math.min(boxA.x2, boxB.x2) - Math.max(boxA.x1, boxB.x1) > -slack &&
            Math.min(boxA.y2, boxB.y2) - Math.max(boxA.y1, boxB.y1) > -slack
          ) {
            overlaps.push(`${a.name} / ${b.name}`);
          }
        }
      }
      expect(overlaps).toEqual([]);
    });
  }

  it('keeps suppliers mostly to the left of their customers', () => {
    const { graph } = settled();
    const leftToRight = graph.edges.filter((edge) => edge.source.x < edge.target.x).length;
    expect(leftToRight / graph.edges.length).toBeGreaterThan(0.85);
  });

  {
    it('spreads out in two directions instead of a tall line', () => {
      const { graph } = settled();
      const box = boundsOf(graph.nodes);
      const ratio = (box.x2 - box.x1) / (box.y2 - box.y1);

      expect(ratio).toBeGreaterThan(0.8);
      expect(ratio).toBeLessThan(3);
    });
  }

  it('settles the same graph the same way every time', () => {
    const first = settled().graph.nodes.map((node) => [node.id, Math.round(node.x), Math.round(node.y)]);
    const second = settled().graph.nodes.map((node) => [node.id, Math.round(node.x), Math.round(node.y)]);

    expect(second).toEqual(first);
  });

  it('puts a graph back exactly where the user left it, with nothing moving by itself', () => {
    const { graph } = settled();
    const left = new Map(graph.nodes.map((node) => [node.id, { x: node.x + 13, y: node.y - 7 }]));

    const again = sampleGraph();
    for (const node of again.nodes) Object.assign(node, left.get(node.id));
    const physics = createPhysics(again.nodes, again.edges);
    restoreSettled(physics, 1.4);

    expect(physics.alpha()).toBe(0);
    for (const node of again.nodes) {
      expect({ x: node.x, y: node.y }).toEqual(left.get(node.id));
      expect(node.labelRoom).toBe(1.4);
    }
  });

  it('holds a company the user placed where they put it, once the web settles around it', () => {
    const { graph, physics } = settled();
    const placed = graph.nodes.find((node) => node.ticker === 'TSLA')!;
    const spot = { x: placed.x + 260, y: placed.y - 180 };
    placed.x = placed.anchorX = spot.x;
    placed.y = placed.anchorY = spot.y;

    settle(physics);

    expect(Math.hypot(placed.x - spot.x, placed.y - spot.y)).toBeLessThan(40);
  });

  it('lets a dragged company tug a placed neighbour along, and the neighbour spring back once let go', () => {
    const { graph, physics } = settled();
    const edge = graph.edges.find((item) => item.source.ticker !== 'TSLA' && item.target.ticker === 'TSLA')!;
    const [dragged, neighbour] = [edge.target, edge.source];
    neighbour.anchorX = neighbour.x;
    neighbour.anchorY = neighbour.y;
    const home = { x: neighbour.x, y: neighbour.y };

    // Dragged far away, and held there while the physics runs.
    dragged.fx = dragged.x + 600;
    dragged.fy = dragged.y;
    physics.alphaTarget(0.22).alpha(0.22);
    for (let i = 0; i < 120; i++) physics.tick();
    const tugged = Math.hypot(neighbour.x - home.x, neighbour.y - home.y);

    // Let go: the drag ends, and the web settles.
    dragged.anchorX = dragged.fx;
    dragged.anchorY = dragged.fy;
    dragged.fx = null;
    dragged.fy = null;
    physics.alphaTarget(0);
    settle(physics);

    expect(tugged).toBeGreaterThan(30);
    expect(Math.hypot(neighbour.x - home.x, neighbour.y - home.y)).toBeLessThan(tugged);
  });

  it('opens with every name readable and no link hidden behind another company, at the zoom it opens at', () => {
    const graph = sampleGraph();
    const room = { top: 70, right: 40, bottom: 104, left: 40 };
    const physics = createPhysics(graph.nodes, graph.edges);
    settleForView(physics, (bounds) => fitCamera(bounds, 1100, 700, room).k);
    const k = fitCamera(boundsOf(graph.nodes), 1100, 700, room).k;
    const scale = labelScale(k);

    // No link runs through a company it does not join.
    const hidden = graph.edges.flatMap((edge) =>
      graph.nodes
        .filter((node) => node !== edge.source && node !== edge.target)
        .filter((node) => lineCrosses(edge.source, edge.target, boxOf({ ...node, labelRoom: 1 }, node.x, node.y)))
        .map((node) => `${edge.summary} behind ${node.name}`),
    );
    expect(hidden).toEqual([]);

    // On screen at that zoom, no label runs into another label or another company's tile.
    const boxes = graph.nodes.flatMap((node) => {
      const half = ((node.labelWidth - 8) * scale) / 2;
      const top = (node.y + node.size / 2 + 9) * k;
      const tile = (node.size / 2) * k;
      return [
        { owner: node.name, x1: node.x * k - half, x2: node.x * k + half, y1: top, y2: top + node.labelHeight * scale },
        {
          owner: node.name,
          x1: node.x * k - tile,
          x2: node.x * k + tile,
          y1: node.y * k - tile,
          y2: node.y * k + tile,
        },
      ];
    });
    const clashes: string[] = [];
    for (let i = 0; i < boxes.length; i++) {
      for (let j = i + 1; j < boxes.length; j++) {
        const a = boxes[i]!;
        const b = boxes[j]!;
        if (a.owner !== b.owner && a.x1 < b.x2 && b.x1 < a.x2 && a.y1 < b.y2 && b.y1 < a.y2) {
          clashes.push(`${a.owner} / ${b.owner}`);
        }
      }
    }
    expect(clashes).toEqual([]);
  });
});
