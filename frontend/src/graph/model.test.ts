import { describe, expect, it } from 'vitest';
import type { GraphEdge, GraphNode } from '../pages/supplyChain';
import stylesheet from '../styles.css?raw';
import { ALERT_COLOUR, buildLiveGraph } from './model';
import { shareWidth } from './render';

describe('buildLiveGraph', () => {
  it('carries the share of a link, and thickens an arrow from 10% to twice as thick at 100%', () => {
    const nodes: GraphNode[] = [
      { id: 'supplier', name: 'Supplier Corporation', displayName: 'Supplier', ticker: null, isHolding: false, weight: null },
      { id: 'holding', name: 'Holding Corporation', displayName: 'Holding', ticker: 'HOLD', isHolding: true, weight: 100 },
    ];
    const edge: GraphEdge = {
      id: 'tagged', source: 'supplier', target: 'holding', relationshipId: 1, reviewStatus: 'UNREVIEWED', criticality: null,
      summary: 'Supplier supplies Holding', share: 0.91,
    };

    expect(buildLiveGraph(nodes, [edge]).edges[0]!.share).toBe(0.91);
    expect(buildLiveGraph(nodes, [{ ...edge, share: null }]).edges[0]!.share).toBeNull();
    expect(shareWidth(null)).toBe(1);
    expect(shareWidth(0.05)).toBe(1);
    expect(shareWidth(0.1)).toBe(1);
    expect(shareWidth(1)).toBe(2);
    expect(shareWidth(0.55)).toBeCloseTo(1.5);
  });

  it('marks an edge critical when its relationship is a sole or single source', () => {
    const nodes: GraphNode[] = [
      { id: 'supplier', name: 'Supplier Corporation', displayName: 'Supplier', ticker: null, isHolding: false, weight: null },
      { id: 'holding', name: 'Holding Corporation', displayName: 'Holding', ticker: 'HOLD', isHolding: true, weight: 100 },
    ];
    const edge = (id: string, relationshipId: number, criticality: GraphEdge['criticality']): GraphEdge => ({
      id,
      source: 'supplier',
      target: 'holding',
      relationshipId,
      reviewStatus: 'UNREVIEWED',
      criticality,
      summary: 'Supplier supplies Holding',
    });

    const graph = buildLiveGraph(nodes, [
      edge('sole', 1, 'SOLE_SOURCE'),
      edge('single', 2, 'SINGLE_SOURCE'),
      edge('ordinary', 3, null),
    ]);

    expect(graph.edges.map((item) => item.critical)).toEqual([true, true, false]);
  });

  // The SEC's "NVIDIA CORP" is "NVIDIA" in its own report; the graph never reformats a name itself.
  it('names each company by the display name the API sends, and asks for its logo by the stored name', () => {
    const graph = buildLiveGraph(
      [{ id: 'nvidia', name: 'NVIDIA CORP', displayName: 'NVIDIA', ticker: 'NVDA', isHolding: true, weight: 100 }],
      [],
    );

    expect(graph.nodes[0]).toMatchObject({ name: 'NVIDIA', title: 'NVIDIA', sourceName: 'NVIDIA CORP' });
  });
});

describe('ALERT_COLOUR', () => {
  // A company's beacon must be the colour its severity has everywhere else (alert cards, severity bars, event marks):
  // one severity set across the app.
  it('is the colour of each severity everywhere in the app', () => {
    const colourOf = (variable: string) => new RegExp(`--${variable}:\\s*(#[0-9a-fA-F]{6})`).exec(stylesheet)?.[1];
    const severity = (level: string) =>
      new RegExp(`\\[data-severity='${level}'\\]\\s*\\{\\s*--sev:\\s*var\\(--([a-z]+)\\)`).exec(stylesheet)?.[1];

    expect(ALERT_COLOUR.HIGH.toLowerCase()).toBe(colourOf(severity('HIGH')!)?.toLowerCase());
    expect(ALERT_COLOUR.MEDIUM.toLowerCase()).toBe(colourOf(severity('MEDIUM')!)?.toLowerCase());
    expect(ALERT_COLOUR.LOW.toLowerCase()).toBe(colourOf(severity('LOW')!)?.toLowerCase());
  });

  it('tells the three severities apart', () => {
    expect(new Set(Object.values(ALERT_COLOUR)).size).toBe(3);
  });
});
