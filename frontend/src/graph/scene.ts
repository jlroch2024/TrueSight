// The living graph's scene: everything that changes over time, apart from drawing it. The physics runs while it is
// warm (after a drag, or switching between web and flow); companies bloom in from left to right when the graph opens,
// and arrows draw themselves between them; every company floats gently around where it rests; and pointing, choosing
// and lighting a chain all blend in and out rather than switching.
import type { Simulation } from 'd3-force';
import { floatOffset } from '../pages/supplyChain';
import {
  type LiveEdge,
  type LiveGraph,
  type LiveNode,
  type Risk,
  LABEL_GAP,
  chainOf,
  companyPresence,
  drawnShare,
  labelScale,
  labelZoom,
  neighbourhood,
} from './model';
import { createPhysics } from './physics';

type Point = { x: number; y: number };
type Box = { x1: number; y1: number; x2: number; y2: number; id: string };

const overlaps = (a: Box, b: Box) => a.x1 < b.x2 && b.x1 < a.x2 && a.y1 < b.y2 && b.y1 < a.y2;

export { labelScale } from './model';

// The entrance: each company waits a little longer the further right it is, then grows in; arrows follow their ends.
const ENTRANCE_DELAY_MS = 90;
const ENTRANCE_STEP_MS = 120;
const NODE_ENTRANCE_MS = 700;
const EDGE_ENTRANCE_MS = 650;
// How quickly lighting, dimming and pointing blend (ms: about two-thirds of the way in this time).
const BLEND_MS = 140;
// How long a company's float takes to fade back in once it has been let go.
const FLOAT_FADE_MS = 1400;
// The least room kept between two holdings' tiles, however far the view zooms out.
const HOLDING_GAP = 16;
// How fast the arrows' dashes travel, in the graph's own units a millisecond.
const FLOW_SPEED = 0.06;
// Below this warmth the physics is left asleep.
const ASLEEP = 0.004;

/** Overshoots a little and settles: the company's tile grows just past its size, then back. */
export function easeOutBack(t: number): number {
  const clamped = Math.min(1, Math.max(0, t));
  const c1 = 1.5;
  const c3 = c1 + 1;
  return 1 + c3 * (clamped - 1) ** 3 + c1 * (clamped - 1) ** 2;
}

export function easeOutCubic(t: number): number {
  const clamped = Math.min(1, Math.max(0, t));
  return 1 - (1 - clamped) ** 3;
}

/** How each company and arrow is shown at this moment: every value 0 to 1, blending towards where it is going. */
export type Look = { appear: number; lit: number; hover: number; label: number };

export class Scene {
  readonly physics: Simulation<LiveNode, { source: LiveNode; target: LiveNode; id: string }>;
  // The view's zoom, for the drill-down (see drawnShare): set every frame.
  zoom = 1;
  // The disruptions on the graph's links, by relationship id (see risksFrom).
  risks = new Map<number, Risk>();
  // How far the arrows' dashes have travelled, in the graph's own units: they move at one steady pace through the
  // graph, so zooming neither speeds them up nor makes them jump, and pointing at an arrow does not change their pace.
  flow = 0;
  hoveredNode: string | null = null;
  hoveredEdge: string | null = null;
  selectedNode: string | null = null;
  selectedEdge: string | null = null;
  spotlight: string | null = null;
  draggedNode: string | null = null;
  readonly nodeLook = new Map<string, Look>();
  readonly edgeLook = new Map<string, Look>();
  private readonly nodeDelay = new Map<string, number>();
  private readonly edgeDelay = new Map<string, number>();
  private readonly floatingSince = new Map<string, number>();
  // Which companies' labels have room to be shown, as last placed (see placeLabels).
  private readonly labelRoom = new Set<string>();
  private startedAt: number | null = null;
  now = 0;

  constructor(
    readonly graph: LiveGraph,
    private readonly still: boolean,
  ) {
    this.physics = createPhysics(graph.nodes, graph.edges);
    graph.nodes.forEach((node, index) => {
      this.nodeDelay.set(node.id, ENTRANCE_DELAY_MS + node.rank * ENTRANCE_STEP_MS + (index % 5) * 30);
      this.nodeLook.set(node.id, { appear: still ? 1 : 0, lit: 1, hover: 0, label: 0 });
    });
    for (const edge of graph.edges) {
      const ends = Math.max(this.nodeDelay.get(edge.source.id)!, this.nodeDelay.get(edge.target.id)!);
      this.edgeDelay.set(edge.id, ends + NODE_ENTRANCE_MS * 0.6);
      this.edgeLook.set(edge.id, { appear: still ? 1 : 0, lit: 1, hover: 0, label: 0 });
    }
  }

  /** How far the opening has gone, 0 to 1; null before the first frame and once it has finished. */
  entrance(): number | null {
    if (this.still || this.startedAt === null) return null;
    const last = Math.max(0, ...this.edgeDelay.values(), ...this.nodeDelay.values()) + EDGE_ENTRANCE_MS;
    const progress = (this.now - this.startedAt) / last;
    return progress >= 1 ? null : Math.max(0, progress);
  }

  /** Whether the entrance has finished. */
  get entered(): boolean {
    const last = Math.max(0, ...this.edgeDelay.values(), ...this.nodeDelay.values()) + EDGE_ENTRANCE_MS;
    return this.still || (this.startedAt !== null && this.now - this.startedAt > last);
  }

  /** Whether the physics is still moving companies by itself. */
  get warm(): boolean {
    return this.physics.alpha() > ASLEEP;
  }

  /**
   * How big a company is drawn at the current zoom: holdings shrink slower than the graph, others recede. Holdings all
   * grow alike (their sizes still compare as their weights), and never so far that two would touch (holdingRoom).
   */
  sizeOf = (node: LiveNode): number =>
    node.size *
    (node.isHolding ? Math.min(drawnShare(node, this.zoom), this.holdingRoom) : drawnShare(node, this.zoom));

  // How far holdings may grow before two would touch, from where they are (see sizeOf); worked out every frame.
  private holdingRoom = Infinity;

  /** Works out how far holdings may grow: for each pair, until their tiles would come within HOLDING_GAP. */
  private measureHoldingRoom() {
    const holdings = this.graph.nodes.filter((node) => node.isHolding);
    let room = Infinity;
    for (let i = 0; i < holdings.length; i++) {
      for (let j = i + 1; j < holdings.length; j++) {
        const a = holdings[i]!;
        const b = holdings[j]!;
        const apart = Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
        room = Math.min(room, ((apart - HOLDING_GAP) * 2) / (a.size + b.size));
      }
    }
    this.holdingRoom = Math.max(1, room);
  }

  /** How present a company is at the current zoom, 0 to 1: holdings always, others fading as the view zooms out. */
  presenceOf = (node: LiveNode): number => (node.isHolding ? 1 : companyPresence(this.zoom));

  /** Where a company is drawn: where the physics has it, plus its float. */
  positionOf = (node: LiveNode): Point => {
    if (this.still || node.id === this.draggedNode) return { x: node.x, y: node.y };
    const since = this.floatingSince.get(node.id) ?? this.startedAt ?? this.now;
    const fade = Math.min(1, Math.max(0, (this.now - since) / FLOAT_FADE_MS));
    const offset = floatOffset(node.id, this.now, node.isHolding);
    return { x: node.x + offset.x * fade, y: node.y + offset.y * fade };
  };

  /** What is lit: the company pointed at and its neighbours, else a chosen chain, company or arrow; else everything. */
  focus(): { nodeIds: Set<string>; edgeIds: Set<string> } | null {
    const { edges } = this.graph;
    if (this.hoveredNode) return neighbourhood(this.hoveredNode, edges);
    if (this.spotlight) return chainOf(this.spotlight, edges);
    if (this.selectedNode) return neighbourhood(this.selectedNode, edges);
    if (this.selectedEdge) {
      const edge = edges.find((item) => item.id === this.selectedEdge);
      if (edge) return { nodeIds: new Set([edge.source.id, edge.target.id]), edgeIds: new Set([edge.id]) };
    }
    return null;
  }

  /** Lets the physics run, so the companies around a drag move with it. */
  heat(target: number) {
    this.physics.alphaTarget(target);
    if (this.physics.alpha() < target) this.physics.alpha(target);
  }

  /**
   * A company held by the pointer stops floating. Let go, it is anchored where it was dropped (held there softly, so a
   * neighbour dragged past can still tug it), and its float fades back in.
   */
  hold(nodeId: string | null) {
    const dropped = this.draggedNode && this.draggedNode !== nodeId ? this.nodeById(this.draggedNode) : undefined;
    if (dropped) {
      this.floatingSince.set(dropped.id, this.now);
      if (dropped.fx != null && dropped.fy != null) {
        dropped.anchorX = dropped.fx;
        dropped.anchorY = dropped.fy;
      }
      dropped.fx = null;
      dropped.fy = null;
    }
    this.draggedNode = nodeId;
  }

  /** One frame: dt is the milliseconds since the last. Returns whether anything moved by itself. */
  update(dt: number, now: number): void {
    this.now = now;
    this.startedAt ??= now;
    this.measureHoldingRoom();
    if (!this.still) this.flow += dt * FLOW_SPEED;
    if (this.warm) {
      // Several small steps when the frame was long, so the physics keeps its pace on a slow screen.
      const steps = Math.min(2, Math.max(1, Math.round(dt / 16)));
      for (let i = 0; i < steps; i++) this.physics.tick();
    }
    const blend = this.still ? 1 : 1 - Math.exp(-dt / BLEND_MS);
    const focus = this.focus();
    const elapsed = now - this.startedAt;
    for (const node of this.graph.nodes) {
      const look = this.nodeLook.get(node.id)!;
      if (!this.still)
        look.appear = Math.min(1, Math.max(0, (elapsed - this.nodeDelay.get(node.id)!) / NODE_ENTRANCE_MS));
      look.lit += ((focus ? (focus.nodeIds.has(node.id) ? 1 : 0) : 1) - look.lit) * blend;
      look.hover += ((node.id === this.hoveredNode || node.id === this.draggedNode ? 1 : 0) - look.hover) * blend;
      look.label += ((this.labelRoom.has(node.id) ? 1 : 0) - look.label) * blend;
    }
    for (const edge of this.graph.edges) {
      const look = this.edgeLook.get(edge.id)!;
      if (!this.still)
        look.appear = Math.min(1, Math.max(0, (elapsed - this.edgeDelay.get(edge.id)!) / EDGE_ENTRANCE_MS));
      look.lit += ((focus ? (focus.edgeIds.has(edge.id) ? 1 : 0) : 1) - look.lit) * blend;
      const pointed = edge.id === this.hoveredEdge || edge.id === this.selectedEdge;
      look.hover += ((pointed ? 1 : 0) - look.hover) * blend;
    }
  }

  /**
   * Decides which labels have room, as a map does: labels are drawn at a size the eye can read whatever the zoom, so
   * zoomed out they would run into each other. They are placed one by one in order of importance (the chosen and
   * pointed-at company, then holdings, largest first, then companies several holdings depend on, then the rest, bigger
   * first), and one that would overlap a label already placed, or another company's tile, waits until there is room.
   */
  placeLabels(camera: { x: number; y: number; k: number }, width: number, height: number) {
    const { k } = camera;
    this.zoom = k;
    const scale = labelScale(k);
    const tiles: Box[] = [];
    const placed: Box[] = [];
    const at = new Map<string, Point>();
    for (const node of this.graph.nodes) {
      const point = this.positionOf(node);
      at.set(node.id, point);
      const half = (this.sizeOf(node) / 2) * k;
      const sx = point.x * k + camera.x;
      const sy = point.y * k + camera.y;
      tiles.push({ x1: sx - half, y1: sy - half, x2: sx + half, y2: sy + half, id: node.id });
    }
    const importance = (node: LiveNode) =>
      (node.id === this.selectedNode || node.id === this.hoveredNode ? 1e6 : 0) +
      (node.isHolding ? 1e4 + (node.weight ?? 0) * 10 : 0) +
      node.sharedCount * 1e3 +
      node.size;
    const order = [...this.graph.nodes].sort((a, b) => importance(b) - importance(a));
    this.labelRoom.clear();
    for (const node of order) {
      if (this.nodeLook.get(node.id)!.appear < 0.5) continue;
      // Too far out for this label: it takes no room, so it keeps no other from showing.
      if (labelZoom(k, node.isHolding) <= 0) continue;
      const point = at.get(node.id)!;
      const halfWidth = ((node.labelWidth - 8) * scale) / 2;
      const sx = point.x * k + camera.x;
      const top = (point.y + this.sizeOf(node) / 2 + LABEL_GAP) * k + camera.y;
      const box: Box = {
        x1: sx - halfWidth - 3,
        y1: top,
        x2: sx + halfWidth + 3,
        y2: top + node.labelHeight * scale + 2,
        id: node.id,
      };
      // Off screen: no need to show it, and no reason to keep others from showing.
      if (box.x2 < 0 || box.x1 > width || box.y2 < 0 || box.y1 > height) continue;
      const pinned = node.id === this.selectedNode || node.id === this.hoveredNode;
      const blocked =
        !pinned &&
        (placed.some((other) => overlaps(box, other)) ||
          tiles.some((tile) => tile.id !== node.id && overlaps(box, tile)));
      if (blocked) continue;
      placed.push(box);
      this.labelRoom.add(node.id);
    }
  }

  edgeById(id: string | null): LiveEdge | undefined {
    return id ? this.graph.edges.find((edge) => edge.id === id) : undefined;
  }

  nodeById(id: string | null): LiveNode | undefined {
    return id ? this.graph.nodes.find((node) => node.id === id) : undefined;
  }
}
