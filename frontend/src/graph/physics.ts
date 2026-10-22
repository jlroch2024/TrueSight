// The living graph's physics, on d3-force: linked companies are held together by springs, every company pushes the
// others away, no two tiles or labels may overlap, and a gentle pull towards each company's place in the chain keeps
// suppliers mostly to the left of their customers while the network finds its own shape. A company the user has placed
// is held there by a soft anchor, so a neighbour dragged past can still tug it, and it springs back. The graph opens
// already settled (the physics runs out of sight first), and only moves again when the user drags a company.
import { type Force, type Simulation, forceLink, forceManyBody, forceSimulation, forceX, forceY } from 'd3-force';
import { lineCrosses } from '../pages/supplyChain';
import { type LiveEdge, type LiveNode, LABEL_GAP, boxOf, labelScale } from './model';

// How far apart the chain's columns are, in the graph's own units: the gentle left-to-right pull aims for them.
export const FLOW_GAP = 300;
// How gently every company is pulled to its column across and to the middle down.
const ACROSS = 0.045;
const DOWN = 0.04;
// How firmly a company the user has placed is drawn back to where it was put.
export const ANCHOR = 0.5;
// How much bigger than its tile the room kept round a holding is, for the drill-down.
const HOLDING_ROOM = 1.3;
// How much of an overlap the live collision eases away each step.
const SOFT_COLLISION = 0.12;
// How thick the medium the companies move through is: once settled, they glide and come to rest, never bounce.
const SETTLED_DRAG = 0.6;
// How far a link keeps from any company it does not join, and how firmly it is kept there.
const LINK_CLEARANCE = 14;
const CLEAR_LINKS = 0.5;
// The most extra room a label is given for the zoom the graph opens at (so a huge graph does not spread without end).
const MOST_LABEL_ROOM = 2.4;
// The room kept around every company's tile and label.
export const BREATHING_ROOM = 22;
// How long the physics runs out of sight before the graph first shows.
const SETTLE_TICKS = 420;

type Link = { source: LiveNode; target: LiveNode; id: string };

/**
 * Holds each company the user has placed (anchorX, anchorY) near where it was put: drawn back towards it like a soft
 * spring, so it stays there when nothing disturbs it, yet a neighbour dragged past can still tug it along.
 */
export function forceAnchors(strength = ANCHOR): Force<LiveNode, undefined> {
  let nodes: LiveNode[] = [];
  const force = (alpha: number) => {
    for (const node of nodes) {
      if (node.anchorX == null || node.anchorY == null || node.fx != null) continue;
      node.vx = (node.vx ?? 0) + (node.anchorX - node.x) * strength * alpha;
      node.vy = (node.vy ?? 0) + (node.anchorY - node.y) * strength * alpha;
    }
  };
  force.initialize = (all: LiveNode[]) => {
    nodes = all;
  };
  return force;
}

/**
 * Keeps companies from overlapping: each is a rectangle (its tile, and its label hanging below it), not a circle, so
 * d3's round collision would either overlap labels or space everything too far. Overlapping pairs are pushed apart
 * along whichever way they overlap least, half each (a held company does not move, so the other takes it all).
 */
export function forceBoxes(room = BREATHING_ROOM, soft = false): Force<LiveNode, undefined> {
  let nodes: LiveNode[] = [];
  // Settling out of sight, overlaps are put right at once; live, they are eased apart through the companies' speed, so
  // a company nudged into another drifts it aside rather than knocking it.
  const shift = (node: LiveNode, axis: 'x' | 'y', by: number) => {
    if (soft) {
      if (axis === 'x') node.vx = (node.vx ?? 0) + by * SOFT_COLLISION;
      else node.vy = (node.vy ?? 0) + by * SOFT_COLLISION;
    } else {
      node[axis] += by;
    }
  };
  // A holding is given extra room, as it is drawn bigger when the view zooms out (see holdingGrowth).
  const boxFor = (node: LiveNode) =>
    boxOf(node.isHolding ? { ...node, size: node.size * HOLDING_ROOM } : node, node.x, node.y);
  const force = () => {
    for (let i = 0; i < nodes.length; i++) {
      const a = nodes[i]!;
      const boxA = boxFor(a);
      for (let j = i + 1; j < nodes.length; j++) {
        const b = nodes[j]!;
        const boxB = boxFor(b);
        const overlapX = Math.min(boxA.x2, boxB.x2) - Math.max(boxA.x1, boxB.x1) + room;
        const overlapY = Math.min(boxA.y2, boxB.y2) - Math.max(boxA.y1, boxB.y1) + room;
        if (overlapX <= 0 || overlapY <= 0) continue;
        const aFixed = a.fx != null;
        const bFixed = b.fx != null;
        if (aFixed && bFixed) continue;
        const shareA = aFixed ? 0 : bFixed ? 1 : 0.5;
        const shareB = 1 - shareA;
        if (overlapX < overlapY) {
          // A box is centred on its company across, so the one further left goes left.
          const direction = a.x === b.x ? (i % 2 ? 1 : -1) : a.x < b.x ? -1 : 1;
          shift(a, 'x', direction * overlapX * shareA);
          shift(b, 'x', -direction * overlapX * shareB);
        } else {
          const middleA = (boxA.y1 + boxA.y2) / 2;
          const middleB = (boxB.y1 + boxB.y2) / 2;
          const direction = middleA === middleB ? (i % 2 ? 1 : -1) : middleA < middleB ? -1 : 1;
          shift(a, 'y', direction * overlapY * shareA);
          shift(b, 'y', -direction * overlapY * shareB);
        }
      }
    }
  };
  force.initialize = (all: LiveNode[]) => {
    nodes = all;
  };
  return force;
}

/**
 * Keeps links clear of the companies they do not join: a company with a link running through its tile or label is
 * steered off it, to whichever side it is already on, so no link is hidden behind someone else's tile.
 */
export function forceClearLinks(edges: LiveEdge[], strength = CLEAR_LINKS): Force<LiveNode, undefined> {
  let nodes: LiveNode[] = [];
  const force = (alpha: number) => {
    for (const node of nodes) {
      if (node.fx != null) continue;
      const box = boxOf(node, node.x, node.y);
      const centre = { x: (box.x1 + box.x2) / 2, y: (box.y1 + box.y2) / 2 };
      const half = { x: (box.x2 - box.x1) / 2 + LINK_CLEARANCE, y: (box.y2 - box.y1) / 2 + LINK_CLEARANCE };
      for (const edge of edges) {
        if (edge.source === node || edge.target === node) continue;
        const a = edge.source;
        const b = edge.target;
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const lengthSquared = dx * dx + dy * dy || 1;
        const t = Math.max(0, Math.min(1, ((centre.x - a.x) * dx + (centre.y - a.y) * dy) / lengthSquared));
        const away = { x: centre.x - (a.x + dx * t), y: centre.y - (a.y + dy * t) };
        // How deep inside the company's box (grown by the clearance) the link runs: 0 at its edge, 1 at its middle.
        const reach = Math.hypot(away.x / half.x, away.y / half.y);
        if (reach >= 1) continue;
        const length = Math.hypot(away.x, away.y);
        // Straight through the middle: off to the link's left.
        const direction = length > 0.01 ? { x: away.x / length, y: away.y / length } : { x: -dy, y: dx };
        const norm = Math.hypot(direction.x, direction.y) || 1;
        const push = (1 - reach) * Math.min(half.x, half.y) * strength * alpha;
        node.vx = (node.vx ?? 0) + (direction.x / norm) * push;
        node.vy = (node.vy ?? 0) + (direction.y / norm) * push;
      }
    }
  };
  force.initialize = (all: LiveNode[]) => {
    nodes = all;
  };
  force.edges = edges;
  return force;
}

/** The physics for a graph, not yet running (the caller ticks it). */
export function createPhysics(nodes: LiveNode[], edges: LiveEdge[]): Simulation<LiveNode, Link> {
  const ranks = nodes.map((node) => node.rank);
  const middleRank = ranks.length ? (Math.min(...ranks) + Math.max(...ranks)) / 2 : 0;
  const links: Link[] = edges.map((edge) => ({ source: edge.source, target: edge.target, id: edge.id }));
  const degree = new Map<string, number>();
  for (const link of links) {
    degree.set(link.source.id, (degree.get(link.source.id) ?? 0) + 1);
    degree.set(link.target.id, (degree.get(link.target.id) ?? 0) + 1);
  }
  return forceSimulation<LiveNode, Link>(nodes)
    .stop()
    .velocityDecay(0.32)
    .force(
      'links',
      forceLink<LiveNode, Link>(links)
        .id((node) => node.id)
        // Long enough for both tiles and their labels, plus room for the arrow to be seen.
        .distance((link) => 70 + (link.source.size + link.target.size) * 0.9 + LABEL_GAP * 2)
        // A busy company's springs are each weaker, so it is not pulled into a knot.
        .strength((link) => 0.7 / Math.min(degree.get(link.source.id) ?? 1, degree.get(link.target.id) ?? 1)),
    )
    .force(
      'repel',
      forceManyBody<LiveNode>()
        .strength((node) => -380 - node.size * 9)
        .distanceMax(900),
    )
    .force('boxes', forceBoxes())
    .force('clear', forceClearLinks(edges))
    .force('anchors', forceAnchors())
    .force('across', forceX<LiveNode>((node) => (node.rank - middleRank) * FLOW_GAP).strength(ACROSS))
    .force('down', forceY<LiveNode>(0).strength(DOWN));
}

/** Runs the physics out of sight until the graph has settled, so it opens still and in place. */
export function settle(physics: Simulation<LiveNode, Link>, ticks = SETTLE_TICKS) {
  physics.alpha(1);
  for (let i = 0; i < ticks; i++) physics.tick();
  physics.alpha(0);
}

/**
 * Settles the graph for the view it opens in, so the first look needs nothing changed: labels are drawn at a size the
 * eye can read whatever the zoom, so once settled, each company's label is given the room it will take at the zoom the
 * view will fit the graph at (zoomFor, from the graph's bounds), and the graph settles again around that; a few rounds,
 * as the room changes the zoom a little each time.
 */
export function settleForView(
  physics: Simulation<LiveNode, Link>,
  zoomFor: (bounds: { x1: number; y1: number; x2: number; y2: number }) => number,
  rounds = 3,
) {
  const nodes = physics.nodes();
  for (const node of nodes) node.labelRoom = 1;
  settle(physics);
  for (let round = 0; round < rounds; round++) {
    const k = zoomFor(boundsOf(nodes));
    const room = Math.min(MOST_LABEL_ROOM, Math.max(1, labelScale(k) / k));
    if (nodes.every((node) => Math.abs(node.labelRoom - room) < 0.02)) break;
    for (const node of nodes) node.labelRoom = room;
    physics.alpha(0.6);
    for (let i = 0; i < 240; i++) physics.tick();
    physics.alpha(0);
  }
  clearLinks(nodes, (physics.force('clear') as unknown as { edges: LiveEdge[] }).edges ?? []);
  goLive(physics);
}

/**
 * Makes a settled graph ready to be touched: overlaps are eased apart rather than put right at once, and the companies
 * move through a thicker medium, so a drag nudges the companies around it like molecules in a suspension: they drift
 * aside, and glide to rest, without snapping or bouncing.
 */
export function goLive(physics: Simulation<LiveNode, Link>) {
  physics.force('boxes', forceBoxes(BREATHING_ROOM, true));
  physics.velocityDecay(SETTLED_DRAG);
}

/**
 * The last tidy-up after settling: any company a link still runs through (one it does not join) is moved the shortest
 * way, in small steps up to a reach, to the nearest place where it is part of fewer crossings (none, if there is such a
 * place) and overlaps no other company; if no place is better, one of the link's own companies moves instead. A few
 * rounds, as one move can open the way for another. Companies the user placed never move.
 */
export function clearLinks(nodes: LiveNode[], edges: LiveEdge[], reach = 480, step = 10) {
  const at = (node: LiveNode, x = node.x, y = node.y) => {
    const box = boxOf(node, x, y);
    return { x1: box.x1 - 4, y1: box.y1 - 4, x2: box.x2 + 4, y2: box.y2 + 4 };
  };
  // How many crossings a company would be part of at a place: links running through it, and its own links running
  // through others.
  const crossingsAt = (node: LiveNode, x: number, y: number) => {
    let count = 0;
    const box = at(node, x, y);
    for (const edge of edges) {
      const mine = edge.source === node || edge.target === node;
      if (!mine) {
        if (lineCrosses(edge.source, edge.target, box)) count++;
        continue;
      }
      const from = edge.source === node ? { x, y } : edge.source;
      const to = edge.target === node ? { x, y } : edge.target;
      for (const other of nodes) {
        if (other === edge.source || other === edge.target) continue;
        if (lineCrosses(from, to, at(other))) count++;
      }
    }
    return count;
  };
  const overlaps = (node: LiveNode, x: number, y: number) => {
    const mine = boxOf(node, x, y);
    const room = BREATHING_ROOM / 2;
    return nodes.some((other) => {
      if (other === node) return false;
      const theirs = boxOf(other, other.x, other.y);
      return (
        mine.x1 < theirs.x2 + room &&
        theirs.x1 < mine.x2 + room &&
        mine.y1 < theirs.y2 + room &&
        theirs.y1 < mine.y2 + room
      );
    });
  };
  const offsets: { x: number; y: number }[] = [];
  for (let dx = -reach; dx <= reach; dx += step) {
    for (let dy = -reach; dy <= reach; dy += step) if (dx || dy) offsets.push({ x: dx, y: dy });
  }
  offsets.sort((a, b) => Math.hypot(a.x, a.y) - Math.hypot(b.x, b.y) || a.x - b.x || a.y - b.y);
  // Moves a company to the nearest place where it is part of fewer crossings (none, if it can), overlapping nothing;
  // false when it is held, placed by the user, or no place is better.
  const move = (node: LiveNode) => {
    if (node.fx != null || node.anchorX != null) return false;
    const now = crossingsAt(node, node.x, node.y);
    if (now === 0) return false;
    let best: { x: number; y: number } | null = null;
    let fewest = now;
    for (const offset of offsets) {
      const x = node.x + offset.x;
      const y = node.y + offset.y;
      if (overlaps(node, x, y)) continue;
      const count = crossingsAt(node, x, y);
      if (count < fewest) {
        fewest = count;
        best = offset;
        if (count === 0) break;
      }
    }
    if (!best) return false;
    node.x += best.x;
    node.y += best.y;
    node.vx = 0;
    node.vy = 0;
    return true;
  };
  for (let pass = 0; pass < 4; pass++) {
    let moved = false;
    for (const edge of edges) {
      for (const node of nodes) {
        if (node === edge.source || node === edge.target) continue;
        if (!lineCrosses(edge.source, edge.target, at(node))) continue;
        // The company in the way moves; failing that (a busy one would drag its own links across others), one of the
        // link's own two companies does.
        if (move(node) || move(edge.target) || move(edge.source)) moved = true;
      }
    }
    if (!moved) break;
  }
}

/** The box every company takes, tiles and labels together, for fitting the view around them. */
export function boundsOf(
  nodes: (Pick<LiveNode, 'x' | 'y' | 'size' | 'labelWidth' | 'labelHeight'> & { labelRoom?: number })[],
) {
  if (nodes.length === 0) return { x1: -100, y1: -100, x2: 100, y2: 100 };
  let x1 = Infinity;
  let y1 = Infinity;
  let x2 = -Infinity;
  let y2 = -Infinity;
  for (const node of nodes) {
    const box = boxOf(node, node.x, node.y);
    x1 = Math.min(x1, box.x1);
    y1 = Math.min(y1, box.y1);
    x2 = Math.max(x2, box.x2);
    y2 = Math.max(y2, box.y2);
  }
  return { x1, y1, x2, y2 };
}
