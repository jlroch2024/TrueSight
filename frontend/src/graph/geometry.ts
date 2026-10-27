// Where things are on the living graph: an arrow's ends (at the edge of each tile, not its middle), and what is under
// the pointer. Plain functions over positions, so they can be tested without drawing anything.
import { type LiveEdge, type LiveNode, LABEL_GAP } from './model';

export type Point = { x: number; y: number };

// The room between a tile's edge and the end of an arrow touching it.
export const ARROW_GAP = 7;

/**
 * Where a line from the middle of a square tile heading towards a point leaves the tile, plus a gap: tiles are
 * squares (with round corners), so the line is cut where it meets the square's side.
 */
export function leaveTile(centre: Point, towards: Point, size: number, gap = ARROW_GAP): Point {
  const dx = towards.x - centre.x;
  const dy = towards.y - centre.y;
  const longest = Math.max(Math.abs(dx), Math.abs(dy));
  if (longest === 0) return { ...centre };
  const inside = size / 2 / longest;
  const length = Math.hypot(dx, dy);
  const t = inside + gap / length;
  return { x: centre.x + dx * t, y: centre.y + dy * t };
}

// A holding's ring stands 6 outside its tile, so an arrow stops further out from a holding, clear of the ring.
export const HOLDING_ARROW_GAP = 11;

/**
 * An arrow's two ends, between the edges of its two tiles (further out from a holding, past its ring); null when the
 * tiles are so close there is no room.
 */
export function arrowEnds(from: Point, to: Point, fromSize: number, toSize: number, fromGap = ARROW_GAP, toGap = ARROW_GAP) {
  const start = leaveTile(from, to, fromSize, fromGap);
  const end = leaveTile(to, from, toSize, toGap);
  const along = (end.x - start.x) * (to.x - from.x) + (end.y - start.y) * (to.y - from.y);
  return along > 0 ? { start, end } : null;
}

/** How far a point is from the straight line between a and b. */
export function distanceToSegment(point: Point, a: Point, b: Point): number {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const lengthSquared = dx * dx + dy * dy;
  const t =
    lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared));
  return Math.hypot(point.x - (a.x + dx * t), point.y - (a.y + dy * t));
}

/** The company under a point (in the graph's own units): its tile, or its label. The one drawn last wins. */
export function nodeAt(
  nodes: LiveNode[],
  positionOf: (node: LiveNode) => Point,
  point: Point,
  sizeOf: (node: LiveNode) => number = (node) => node.size,
): LiveNode | null {
  for (let i = nodes.length - 1; i >= 0; i--) {
    const node = nodes[i]!;
    const at = positionOf(node);
    const half = sizeOf(node) / 2;
    const onTile = Math.abs(point.x - at.x) <= half + 3 && Math.abs(point.y - at.y) <= half + 3;
    const labelTop = at.y + half + LABEL_GAP - 2;
    const onLabel =
      Math.abs(point.x - at.x) <= node.labelWidth / 2 &&
      point.y >= labelTop &&
      point.y <= labelTop + node.labelHeight + 4;
    if (onTile || onLabel) return node;
  }
  return null;
}

/** The arrow under a point, within a reach (in the graph's own units): the nearest, if any is that close. */
export function edgeAt(
  edges: LiveEdge[],
  positionOf: (node: LiveNode) => Point,
  point: Point,
  reach: number,
  sizeOf: (node: LiveNode) => number = (node) => node.size,
): LiveEdge | null {
  let best: LiveEdge | null = null;
  let bestDistance = reach;
  for (const edge of edges) {
    const ends = arrowEnds(positionOf(edge.source), positionOf(edge.target), sizeOf(edge.source), sizeOf(edge.target));
    if (!ends) continue;
    const distance = distanceToSegment(point, ends.start, ends.end);
    if (distance <= bestDistance) {
      best = edge;
      bestDistance = distance;
    }
  }
  return best;
}
