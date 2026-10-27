// A graph carried on from the one on screen (Scene.carryFrom): when the page's data changes, what is still there keeps
// its place and look, the opening does not play again, a new size is eased into, and what is gone fades where it was.
import { describe, expect, it } from 'vitest';
import type { GraphEdge, GraphNode } from '../pages/supplyChain';
import { buildLiveGraph } from './model';
import { Scene } from './scene';

const supplier: GraphNode = { id: 'a', name: 'Supplier', displayName: 'Supplier', ticker: null, isHolding: false, weight: null };
const holding: GraphNode = { id: 'b', name: 'Holding', displayName: 'Holding', ticker: 'HLD', isHolding: true, weight: 10 };
const other: GraphNode = { id: 'c', name: 'Other', displayName: 'Other', ticker: 'OTH', isHolding: true, weight: 10 };
const link: GraphEdge = {
  id: 'e',
  source: 'a',
  target: 'b',
  relationshipId: 1,
  reviewStatus: 'APPROVED',
  criticality: null,
  summary: 'Supplier supplies Holding',
};

/** A scene that has opened fully, with its companies moved somewhere of their own. */
function opened(nodes: GraphNode[], edges: GraphEdge[]) {
  const scene = new Scene(buildLiveGraph(nodes, edges), false);
  scene.physics.alpha(0);
  scene.graph.nodes.forEach((node, index) => {
    node.x = 100 + index * 50;
    node.y = -40 * index;
  });
  for (let t = 0; t <= 6000; t += 16) scene.update(16, t);
  return scene;
}

describe('carrying a graph on', () => {
  it('keeps every company where it was and does not open again', () => {
    const before = opened([supplier, holding], [link]);

    const after = new Scene(buildLiveGraph([supplier, holding], [link]), false);
    after.carryFrom(before);
    after.physics.alpha(0);
    after.update(16, 6016);

    expect(after.carried).toBe(true);
    expect(after.entrance()).toBeNull();
    for (const node of after.graph.nodes) {
      const was = before.nodeById(node.id)!;
      expect({ x: node.x, y: node.y }).toEqual({ x: was.x, y: was.y });
      expect(after.nodeLook.get(node.id)!.appear).toBe(1);
    }
  });

  it('eases a holding into its new size rather than jumping', () => {
    const before = opened([supplier, holding], [link]);
    const heavier = { ...holding, weight: 60 };

    const after = new Scene(buildLiveGraph([supplier, heavier], [link]), false);
    after.carryFrom(before);
    after.physics.alpha(0);
    const node = after.nodeById('b')!;
    after.update(16, 6016);
    const growing = after.sizeOf(node);
    for (let t = 6032; t < 9000; t += 16) after.update(16, t);

    expect(growing).toBeGreaterThan(before.nodeById('b')!.size);
    expect(growing).toBeLessThan(node.size);
    expect(after.sizeOf(node)).toBeCloseTo(node.size);
  });

  it('grows a new company in, and fades one taken away where it was', () => {
    const before = opened([supplier, holding, other], [link]);

    const after = new Scene(buildLiveGraph([supplier, holding], [link]), false);
    after.carryFrom(before);
    after.physics.alpha(0);

    expect(after.drawnNodes.map((node) => node.id)).toContain('c');
    for (let t = 6016; t < 7000; t += 16) after.update(16, t);
    expect(after.drawnNodes.map((node) => node.id)).not.toContain('c');

    const again = new Scene(buildLiveGraph([supplier, holding, other], [link]), false);
    again.carryFrom(after);
    again.physics.alpha(0);
    again.update(16, 7016);
    expect(again.nodeLook.get('c')!.appear).toBeLessThan(1);
    for (let t = 7032; t < 9000; t += 16) again.update(16, t);
    expect(again.nodeLook.get('c')!.appear).toBe(1);
  });
});
