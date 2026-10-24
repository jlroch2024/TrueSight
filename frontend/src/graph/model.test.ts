import { describe, expect, it } from 'vitest';
import type { GraphEdge, GraphNode } from '../pages/supplyChain';
import { buildLiveGraph } from './model';

describe('buildLiveGraph', () => {
  it('marks an edge critical when its relationship is a sole or single source', () => {
    const nodes: GraphNode[] = [
      { id: 'supplier', name: 'Supplier Corporation', ticker: null, isHolding: false, weight: null },
      { id: 'holding', name: 'Holding Corporation', ticker: 'HOLD', isHolding: true, weight: 100 },
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
});
