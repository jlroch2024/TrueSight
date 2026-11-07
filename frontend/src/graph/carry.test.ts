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

  it('keeps a company being dragged held where the pointer has it, and the web warm around it', () => {
    const before = opened([supplier, holding], [link]);
    const dragged = before.nodeById('b')!;
    dragged.fx = 300;
    dragged.fy = -120;
    before.hold('b');
    before.heat(0.3);

    const after = new Scene(buildLiveGraph([supplier, holding, other], [link]), false);
    after.carryFrom(before);

    expect(after.draggedNode).toBe('b');
    expect(after.physics.alphaTarget()).toBe(0.3);
    const held = after.nodeById('b')!;
    expect({ fx: held.fx, fy: held.fy }).toEqual({ fx: 300, fy: -120 });
    after.physics.tick();
    expect({ x: held.x, y: held.y }).toEqual({ x: 300, y: -120 });

    after.hold(null);
    expect({ anchorX: held.anchorX, anchorY: held.anchorY, fx: held.fx }).toEqual({ anchorX: 300, anchorY: -120, fx: null });
  });

  it('lets go of a company being dragged when the new graph no longer has it', () => {
    const before = opened([supplier, holding, other], [link]);
    before.hold('c');

    const after = new Scene(buildLiveGraph([supplier, holding], [link]), false);
    after.carryFrom(before);

    expect(after.draggedNode).toBeNull();
  });
});

describe('showing only some of the graph (a year of the history)', () => {
  const everyone = { nodeIds: new Set(['a', 'b', 'c']), edgeIds: new Set(['e']) };
  const withoutSupplier = { nodeIds: new Set(['b', 'c']), edgeIds: new Set<string>() };

  /** A graph on screen with everything shown, as the page first sets it: only then do changes fade. */
  const onScreen = (nodes: GraphNode[], edges: GraphEdge[]) => {
    const scene = opened(nodes, edges);
    scene.setShown(everyone, null);
    return scene;
  };

  /** Frames from `from` for `ms`, 16 ms apart. */
  const run = (scene: Scene, from: number, ms: number) => {
    for (let t = from; t <= from + ms; t += 16) scene.update(16, t);
  };

  it('fades out what the chosen year did not name and fades it back in', () => {
    const scene = onScreen([supplier, holding, other], [link]);
    scene.setShown(withoutSupplier, null);

    run(scene, 6016, 100);
    const fading = scene.nodeLook.get('a')!.appear;
    expect(fading).toBeGreaterThan(0);
    expect(fading).toBeLessThan(1);

    run(scene, 6200, 400);
    expect(scene.nodeLook.get('a')!.appear).toBe(0);
    expect(scene.edgeLook.get('e')!.appear).toBe(0);
    expect(scene.nodeLook.get('b')!.appear).toBe(1);

    scene.setShown(everyone, null);
    run(scene, 6700, 400);
    expect(scene.nodeLook.get('a')!.appear).toBe(1);
    expect(scene.edgeLook.get('e')!.appear).toBe(1);
  });

  it('moves no company when the year changes', () => {
    const scene = onScreen([supplier, holding, other], [link]);
    scene.physics.stop();
    const places = () => scene.graph.nodes.map((node) => ({ id: node.id, x: node.x, y: node.y }));
    const before = places();

    scene.setShown(withoutSupplier, null);
    run(scene, 6016, 500);
    scene.setShown(everyone, null);
    run(scene, 6600, 500);

    expect(places()).toEqual(before);
    expect(scene.warm).toBe(false);
  });

  it('cannot be pointed at once it has faded out', () => {
    const scene = onScreen([supplier, holding, other], [link]);
    scene.setShown(withoutSupplier, null);
    run(scene, 6016, 500);

    expect(scene.isNodeThere(scene.nodeById('a')!)).toBe(false);
    expect(scene.isEdgeThere(scene.edgeById('e')!)).toBe(false);
    expect(scene.isNodeThere(scene.nodeById('b')!)).toBe(true);
  });

  it('starts a company it sees for the first time as it should be, rather than fading to it', () => {
    const before = opened([supplier, holding], [link]);
    const after = new Scene(buildLiveGraph([supplier, holding, other], [link]), false);
    after.carryFrom(before);
    after.setShown({ nodeIds: new Set(['a', 'b']), edgeIds: new Set(['e']) }, null);

    // 'c' came with the graph but is not in the year shown: hidden from its first frame.
    expect(after.nodeLook.get('c')!.shown).toBe(0);
    expect(after.nodeLook.get('a')!.shown).toBe(1);
  });

  it('changes years without fades when motion is reduced', () => {
    const scene = new Scene(buildLiveGraph([supplier, holding, other], [link]), true);
    scene.physics.alpha(0);
    scene.update(16, 16);
    scene.setShown(withoutSupplier, null);
    scene.update(16, 32);

    expect(scene.nodeLook.get('a')!.appear).toBe(0);
    expect(scene.edgeLook.get('e')!.appear).toBe(0);
  });

  it('lights what is new, and keeps it lit without animation when motion is reduced', () => {
    const scene = onScreen([supplier, holding, other], [link]);
    scene.setShown(everyone, { nodeIds: new Set(['a']), edgeIds: new Set(['e']), tag: 'New in 2025' });
    run(scene, 6016, 400);

    expect(scene.nodeLook.get('a')!.fresh).toBe(1);
    expect(scene.edgeLook.get('e')!.fresh).toBe(1);
    expect(scene.nodeLook.get('b')!.fresh).toBe(0);
    expect(scene.fresh!.tag).toBe('New in 2025');

    scene.setShown(everyone, null);
    run(scene, 6500, 400);
    expect(scene.nodeLook.get('a')!.fresh).toBe(0);

    const still = new Scene(buildLiveGraph([supplier, holding], [link]), true);
    still.setShown(everyone, { nodeIds: new Set(['a']), edgeIds: new Set(), tag: 'New in 2025' });
    still.update(16, 16);
    expect(still.nodeLook.get('a')!.fresh).toBe(1);
  });

  it('keeps what is shown when the graph is carried on', () => {
    const before = onScreen([supplier, holding, other], [link]);
    before.setShown(withoutSupplier, null);
    run(before, 6016, 500);

    const after = new Scene(buildLiveGraph([supplier, holding, other], [link]), false);
    after.carryFrom(before);
    after.setShown(withoutSupplier, null);
    after.update(16, 6600);

    expect(after.nodeLook.get('a')!.appear).toBe(0);
    expect(after.nodeLook.get('b')!.appear).toBe(1);
  });

  it('gives no room to the label of a company faded out', () => {
    const scene = onScreen([supplier, holding, other], [link]);
    scene.setShown(withoutSupplier, null);
    run(scene, 6016, 500);

    scene.placeLabels({ x: 400, y: 250, k: 1 }, 800, 500);
    run(scene, 6600, 200);

    expect(scene.nodeLook.get('a')!.label).toBe(0);
  });
});
