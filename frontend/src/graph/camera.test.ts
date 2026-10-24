// The living graph's camera, geometry, logo looks and label placement: the arithmetic behind what is drawn.
import { describe, expect, it } from 'vitest';
import { CameraRig, FLY_MS, between, clampZoom, easeInOut, fitCamera, toScreen, toWorld } from './camera';
import { arrowEnds, distanceToSegment, edgeAt, leaveTile, nodeAt } from './geometry';
import { logoSource, treatmentOf } from './logos';
import { type LiveEdge, type LiveNode, buildLiveGraph } from './model';
import { mix } from './render';
import { Scene, easeOutBack } from './scene';

const node = (id: string, x: number, y: number, extra: Partial<LiveNode> = {}): LiveNode => ({
  id,
  sourceName: id,
  name: id,
  ticker: null,
  isHolding: false,
  weight: null,
  sharedSummary: null,
  sharedCount: 0,
  size: 40,
  colour: null,
  rank: 0,
  title: id,
  subtitle: null,
  labelWidth: 60,
  labelHeight: 15,
  labelRoom: 1,
  initials: 'XX',
  hue: 0,
  x,
  y,
  ...extra,
});
const at = (item: LiveNode) => ({ x: item.x, y: item.y });

describe('the camera', () => {
  it('turns points from the graph to the screen and back', () => {
    const camera = { x: 100, y: 50, k: 2 };
    const point = { x: 12, y: -7 };

    expect(toScreen(camera, point)).toEqual({ x: 124, y: 36 });
    expect(toWorld(camera, toScreen(camera, point))).toEqual(point);
  });

  it('fits a box in what the controls leave clear, centred there', () => {
    const camera = fitCamera({ x1: 0, y1: 0, x2: 1000, y2: 500 }, 1200, 800, {
      top: 70,
      right: 100,
      bottom: 130,
      left: 100,
    });

    expect(camera.k).toBe(1);
    // The box's middle lands in the middle of the clear area, not of the screen.
    expect(toScreen(camera, { x: 500, y: 250 })).toEqual({ x: 600, y: 370 });
  });

  it('never zooms a small graph in too far, nor any graph out past the limit', () => {
    expect(fitCamera({ x1: 0, y1: 0, x2: 10, y2: 10 }, 1000, 1000).k).toBeLessThanOrEqual(1.35);
    expect(clampZoom(0.001)).toBe(0.12);
    expect(clampZoom(99)).toBe(3.2);
  });

  it('keeps the point under the pointer still while a wheel zoom glides in', () => {
    const rig = new CameraRig();
    rig.setSize(800, 600);
    rig.jump({ x: 0, y: 0, k: 1 });
    const pointer = { x: 300, y: 200 };
    const under = toWorld(rig.camera, pointer);

    rig.zoomAt(2, pointer);
    for (let t = 16; t <= 1000; t += 16) rig.step(16, t);

    expect(rig.camera.k).toBeCloseTo(2);
    expect(toWorld(rig.camera, pointer).x).toBeCloseTo(under.x);
    expect(toWorld(rig.camera, pointer).y).toBeCloseTo(under.y);
    expect(rig.moving).toBe(false);
  });

  it('flies to a camera, easing in and out, and lands exactly on it', () => {
    const rig = new CameraRig();
    rig.setSize(800, 600);
    rig.jump({ x: 0, y: 0, k: 1 });
    const target = { x: -400, y: 120, k: 2.5 };

    rig.flyTo(target);
    rig.step(16, 0);
    rig.step(16, FLY_MS / 2);
    expect(rig.moving).toBe(true);
    rig.step(16, FLY_MS + 1);

    expect(rig.camera).toEqual(target);
    expect(easeInOut(0)).toBe(0);
    expect(easeInOut(1)).toBe(1);
    expect(between({ x: 0, y: 0, k: 1 }, target, 1, 800, 600).k).toBeCloseTo(2.5);
  });

  it('lets a pan drift on after it is let go, slowing to a stop', () => {
    const rig = new CameraRig();
    rig.jump({ x: 0, y: 0, k: 1 });

    rig.release({ x: 1, y: 0 });
    rig.step(16, 16);
    const early = rig.camera.x;
    for (let t = 32; t < 5000; t += 16) rig.step(16, t);

    expect(early).toBeGreaterThan(0);
    expect(rig.camera.x).toBeGreaterThan(early);
    expect(rig.moving).toBe(false);
  });

  it('lands every move at once when motion is reduced', () => {
    const rig = new CameraRig(true);
    rig.setSize(800, 600);
    rig.jump({ x: 0, y: 0, k: 1 });

    rig.flyTo({ x: 10, y: 20, k: 2 });
    rig.release({ x: 5, y: 5 });

    expect(rig.camera).toEqual({ x: 10, y: 20, k: 2 });
    expect(rig.moving).toBe(false);
  });
});

describe('the geometry', () => {
  it('starts an arrow at the edge of its square tile, plus a gap, not at its middle', () => {
    expect(leaveTile({ x: 0, y: 0 }, { x: 100, y: 0 }, 40, 5)).toEqual({ x: 25, y: 0 });
    const diagonal = leaveTile({ x: 0, y: 0 }, { x: 100, y: 100 }, 40, 0);
    expect(diagonal.x).toBeCloseTo(20);
    expect(diagonal.y).toBeCloseTo(20);
  });

  it('draws no arrow between tiles so close there is no room for one', () => {
    expect(arrowEnds({ x: 0, y: 0 }, { x: 30, y: 0 }, 40, 40)).toBeNull();
    expect(arrowEnds({ x: 0, y: 0 }, { x: 200, y: 0 }, 40, 40)).not.toBeNull();
  });

  it('measures how far a point is from a line', () => {
    expect(distanceToSegment({ x: 50, y: 10 }, { x: 0, y: 0 }, { x: 100, y: 0 })).toBe(10);
    expect(distanceToSegment({ x: -30, y: 40 }, { x: 0, y: 0 }, { x: 100, y: 0 })).toBe(50);
  });

  it('finds the company under the pointer, on its tile or its label, the one on top first', () => {
    const below = node('below', 0, 0);
    const above = node('above', 10, 0);

    expect(nodeAt([below, above], at, { x: 12, y: 2 })?.id).toBe('above');
    // On the label, under the tile.
    expect(nodeAt([below], at, { x: 0, y: 36 })?.id).toBe('below');
    expect(nodeAt([below], at, { x: 200, y: 200 })).toBeNull();
  });

  it('finds a company by the size it is drawn at, not its size at 1:1', () => {
    const holding = node('holding', 0, 0, { size: 40 });

    // Drawn twice as big (zoomed out), its edge is 40 out, not 20.
    expect(nodeAt([holding], at, { x: 35, y: 0 })).toBeNull();
    expect(nodeAt([holding], at, { x: 35, y: 0 }, () => 80)?.id).toBe('holding');
  });

  it('finds the nearest arrow within reach', () => {
    const a = node('a', 0, 0);
    const b = node('b', 300, 0);
    const c = node('c', 0, 100);
    const edges = [
      { id: 'ab', source: a, target: b },
      { id: 'cb', source: c, target: b },
    ] as LiveEdge[];

    expect(edgeAt(edges, at, { x: 150, y: 3 }, 8)?.id).toBe('ab');
    expect(edgeAt(edges, at, { x: 150, y: 30 }, 8)).toBeNull();
  });
});

// A logo's pixels: every pixel the same colour and opacity, except a centre square of another.
function pixels(size: number, edge: [number, number, number, number], middle: [number, number, number, number]) {
  const data = new Uint8ClampedArray(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const inside = x > size / 4 && x < (size * 3) / 4 && y > size / 4 && y < (size * 3) / 4;
      data.set(inside ? middle : edge, (y * size + x) * 4);
    }
  }
  return data;
}

describe('how a logo sits on its tile', () => {
  it('fills the tile with a logo that has a solid background of its own', () => {
    const look = treatmentOf(pixels(48, [118, 185, 0, 255], [255, 255, 255, 255]), 48, 48);

    expect(look).toEqual({ treatment: 'bleed', background: 'rgb(118, 185, 0)', crop: { x: 0, y: 0, w: 1, h: 1 } });
  });

  it('puts a white logo on a clear background on a dark tile, where it can be seen', () => {
    expect(treatmentOf(pixels(48, [0, 0, 0, 0], [250, 250, 250, 255]), 48, 48).treatment).toBe('light');
  });

  it('puts any other logo on a white tile', () => {
    expect(treatmentOf(pixels(48, [0, 0, 0, 0], [20, 60, 160, 255]), 48, 48).treatment).toBe('plain');
    expect(treatmentOf(pixels(48, [255, 255, 255, 255], [20, 60, 160, 255]), 48, 48).treatment).toBe('plain');
  });

  it("trims a white border off a solid block with a mark on it (AMD's logo), so the block fills the tile", () => {
    // A black square inset on white, with a white bar drawn across its middle.
    const size = 64;
    const data = new Uint8ClampedArray(size * size * 4);
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const inBlock = x >= 12 && x < 52 && y >= 12 && y < 52;
        const onMark = inBlock && y >= 28 && y < 36 && x >= 20 && x < 44;
        const value = inBlock && !onMark ? 0 : 255;
        data.set([value, value, value, 255], (y * size + x) * 4);
      }
    }

    const look = treatmentOf(data, size, size);

    expect(look.treatment).toBe('bleed');
    expect(look.background).toBe('rgb(0, 0, 0)');
    expect(look.crop.x).toBeCloseTo(12 / 64);
    expect(look.crop.w).toBeCloseTo(40 / 64);
  });

  it("fills the tile with a logo that fills its own square on a shaded background (AMD's dark gradient)", () => {
    // Dark at the top, grey at the bottom, with a light mark in the middle and one reaching the top-right corner.
    const size = 64;
    const data = new Uint8ClampedArray(size * size * 4);
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const shade = Math.round(10 + (y / size) * 130);
        const mark = (x > 20 && x < 44 && y > 26 && y < 38) || (x > 52 && y < 12);
        data.set(mark ? [240, 240, 240, 255] : [shade, shade + 3, shade + 5, 255], (y * size + x) * 4);
      }
    }

    const look = treatmentOf(data, size, size);

    expect(look.treatment).toBe('bleed');
    expect(look.crop).toEqual({ x: 0, y: 0, w: 1, h: 1 });
  });

  it('trims the margin round a mark on white, so every mark is drawn the same size', () => {
    const look = treatmentOf(pixels(48, [255, 255, 255, 255], [20, 60, 160, 255]), 48, 48);

    expect(look.treatment).toBe('plain');
    expect(look.crop.w).toBeLessThan(0.6);
    expect(look.crop.x).toBeGreaterThan(0.2);
  });

  it('asks Logo.dev by ticker, else by domain, and nothing without a token', () => {
    expect(logoSource({ ticker: 'NVDA' }, 'pk_test')).toContain('/ticker/NVDA?token=pk_test');
    expect(logoSource({ ticker: null }, 'pk_test', 'zeiss.com')).toContain('/zeiss.com?token=pk_test');
    expect(logoSource({ ticker: null }, 'pk_test')).toBeNull();
    expect(logoSource({ ticker: 'NVDA' }, '')).toBeNull();
  });
});

describe('the scene', () => {
  it('lets the more important of two crowded labels show, and the other wait for room', () => {
    const graph = buildLiveGraph(
      [
        { id: 'holding', name: 'Big Holding', ticker: 'BIG', isHolding: true, weight: 30 },
        { id: 'supplier', name: 'Small Supplier', ticker: null, isHolding: false, weight: null },
      ],
      [],
    );
    // Side by side, so close their labels would overlap.
    graph.nodes[0]!.x = 0;
    graph.nodes[1]!.x = 30;
    const scene = new Scene(graph, true);
    // Cool, as the graph leaves it once settled, so the physics does not move them apart first.
    scene.physics.alpha(0);
    scene.update(16, 0);

    scene.placeLabels({ x: 400, y: 300, k: 1 }, 800, 600);
    for (let t = 16; t < 2000; t += 16) scene.update(16, t);

    expect(scene.nodeLook.get('holding')!.label).toBeCloseTo(1);
    expect(scene.nodeLook.get('supplier')!.label).toBeCloseTo(0);
  });

  it('grows a tile just past its size and settles back', () => {
    expect(easeOutBack(0)).toBeCloseTo(0);
    expect(Math.max(...[0.6, 0.7, 0.8, 0.9].map(easeOutBack))).toBeGreaterThan(1);
    expect(easeOutBack(1)).toBeCloseTo(1);
  });

  it('mixes colours for a brighter arrow under the pointer', () => {
    expect(mix('#000000', '#ffffff', 0.5)).toBe('#808080');
    expect(mix('#185afa', '#ffffff', 0)).toBe('#185afa');
  });
});

describe('the opening', () => {
  it('sweeps light across the graph only while it opens', () => {
    const graph = buildLiveGraph(
      [
        { id: 'a', name: 'Supplier', ticker: null, isHolding: false, weight: null },
        { id: 'b', name: 'Holding', ticker: 'HLD', isHolding: true, weight: 10 },
      ],
      [
        {
          id: 'e',
          source: 'a',
          target: 'b',
          relationshipId: 1,
          reviewStatus: 'APPROVED',
          criticality: null,
          summary: 'Supplier supplies Holding',
        },
      ],
    );
    const opening = new Scene(graph, false);
    opening.physics.alpha(0);
    expect(opening.entrance()).toBeNull();

    opening.update(16, 1000);
    expect(opening.entrance()).toBe(0);
    opening.update(16, 1400);
    expect(opening.entrance()!).toBeGreaterThan(0);
    opening.update(16, 60_000);
    expect(opening.entrance()).toBeNull();

    const still = new Scene(graph, true);
    still.update(16, 1000);
    expect(still.entrance()).toBeNull();
  });

  it('moves the dashes at one steady pace through the graph, whether or not an arrow is pointed at', () => {
    const graph = buildLiveGraph(
      [
        { id: 'a', name: 'Supplier', ticker: null, isHolding: false, weight: null },
        { id: 'b', name: 'Holding', ticker: 'HLD', isHolding: true, weight: 10 },
      ],
      [
        {
          id: 'e',
          source: 'a',
          target: 'b',
          relationshipId: 1,
          reviewStatus: 'APPROVED',
          criticality: null,
          summary: 'Supplier supplies Holding',
        },
      ],
    );
    const scene = new Scene(graph, false);
    scene.physics.alpha(0);
    scene.update(16, 0);
    const paces: number[] = [];
    for (let t = 16; t <= 1600; t += 16) {
      scene.hoveredEdge = t > 800 ? 'e' : null;
      const before = scene.flow;
      scene.update(16, t);
      paces.push(scene.flow - before);
    }

    expect(new Set(paces.map((pace) => pace.toFixed(6))).size).toBe(1);
    expect(paces[0]).toBeGreaterThan(0);
  });
});
