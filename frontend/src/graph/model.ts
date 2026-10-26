// The living graph's model: the page's companies and relationships turned into what the physics moves and the renderer
// draws. Everything here is plain data and pure functions, so it can be tested without a browser.
import type { AlertEvent } from '../api/alerts';
import type { Severity } from '../api/news';
import { type GraphEdge, type GraphNode, chainColours, flowLayout } from '../pages/supplyChain';

/** A company on the living graph. x, y, vx, vy, fx and fy belong to the physics (d3-force writes them). */
export type LiveNode = {
  id: string;
  // The name as the page has it (the key for its logo domain), and as shown.
  sourceName: string;
  name: string;
  ticker: string | null;
  isHolding: boolean;
  weight: number | null;
  // "Supplies 2 Holdings" on a company several holdings depend on; how many, for its badge.
  sharedSummary: string | null;
  sharedCount: number;
  // The tile's side, in the graph's own units.
  size: number;
  // The colour of the holding's chain, on a holding with arrows (see chainColours); null otherwise.
  colour: string | null;
  // Its place in the flow, 0 for the first suppliers: the gentle left-to-right pull aims for it.
  rank: number;
  // The label under the tile: the company's name, and a second, smaller line (ticker and weight, or what it supplies).
  title: string;
  subtitle: string | null;
  // The box the label takes, in the graph's own units, for spacing companies apart.
  labelWidth: number;
  labelHeight: number;
  // How much more room than labelWidth and labelHeight the label is given, in the graph's own units: labels are drawn
  // at a size the eye can read whatever the zoom, so at the zoom the graph opens at they need more (see settleForView).
  labelRoom: number;
  // For a company without a logo: its initials, on a gradient of its own.
  initials: string;
  hue: number;
  // Where the user placed it, if they have: the physics holds it near here (see forceAnchors).
  anchorX?: number | null;
  anchorY?: number | null;
  x: number;
  y: number;
  vx?: number;
  vy?: number;
  fx?: number | null;
  fy?: number | null;
};

/** A relationship on the living graph, pointing from supplier to customer. */
export type LiveEdge = {
  id: string;
  source: LiveNode;
  target: LiveNode;
  relationshipId: number;
  reviewed: boolean;
  critical: boolean;
  colour: string;
  summary: string;
};

export type LiveGraph = {
  nodes: LiveNode[];
  edges: LiveEdge[];
  chains: { id: string; label: string; colour: string }[];
};

// Tile sides, in the graph's own units: a supplier or customer, a shared one, and the smallest and largest holding.
// A holding is marked by its colour and chamfer, so it is only a little bigger, and other logos stay readable.
export const COMPANY_SIZE = 50;
export const SHARED_SIZE = 54;
const HOLDING_MIN = 54;
const HOLDING_MAX = 62;
// The gap between a tile and its label, and each label line's height.
export const LABEL_GAP = 9;
export const TITLE_LINE = 15;
export const SUBTITLE_LINE = 14;
// An arrow with no holding's colour.
export const NEUTRAL_EDGE = '#5d6b80';
// Longest name shown before it is cut short.
const TITLE_MAX = 24;

/** How big a holding's tile is: bigger the more of the portfolio it is, by the square root so small ones still show. */
export function holdingTile(weight: number | null): number {
  if (weight === null || !Number.isFinite(weight) || weight <= 0) return HOLDING_MIN;
  return Math.min(HOLDING_MAX, HOLDING_MIN + 2 * Math.sqrt(weight));
}

/** A name cut to fit under a tile, with an ellipsis. */
export function clip(text: string, max = TITLE_MAX): string {
  return text.length <= max ? text : `${text.slice(0, max - 1).trimEnd()}…`;
}

/** A company's initials, for when it has no logo: its first two words' first letters ("Taiwan Semiconductor" TS). */
export function initialsOf(name: string): string {
  const words = name
    .replace(/[^\p{L}\p{N}\s&-]/gu, ' ')
    .split(/[\s-]+/)
    .filter((word) => word && !/^(the|and|of|&)$/i.test(word));
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0]!.slice(0, 2).toUpperCase();
  return (words[0]![0]! + words[1]![0]!).toUpperCase();
}

/** A hue of the company's own, from its name, for its initials' gradient: the same company always gets the same one. */
export function hueOf(name: string): number {
  let hash = 0;
  for (const character of name) hash = (Math.imul(hash, 31) + character.charCodeAt(0)) | 0;
  return Math.abs(hash) % 360;
}

/** How wide a line of text is, in the graph's own units, when nothing can measure it: about 6.9 per letter. */
export function estimateWidth(text: string, fontSize: number): number {
  return text.length * fontSize * 0.58;
}

/**
 * Builds the living graph from the page's companies and relationships. measure says how wide a label line is (the
 * browser's own measurement when there is one); positions are where each company starts, before the physics settles
 * it (by default, the flow's columns).
 */
export function buildLiveGraph(
  nodes: GraphNode[],
  edges: GraphEdge[],
  measure: (text: string, fontSize: number) => number = estimateWidth,
): LiveGraph {
  const chains = chainColours(nodes, edges);
  const colourOf = new Map(chains.key.map((entry) => [entry.id, entry.colour]));
  const sizeOf = (node: GraphNode) =>
    node.isHolding ? holdingTile(node.weight) : node.sharedSummary ? SHARED_SIZE : COMPANY_SIZE;
  const flow = flowLayout(
    nodes.map((node) => node.id),
    edges,
    (id) => sizeOf(nodes.find((node) => node.id === id)!) + 40,
  );
  const ranks = [...flow.values()].map((point) => point.x);
  const firstColumn = ranks.length ? Math.min(...ranks) : 0;

  const liveNodes = nodes.map((node): LiveNode => {
    const name = node.displayName;
    const title = clip(name);
    const weight = node.isHolding && node.weight !== null ? `${Number(node.weight.toFixed(1))}%` : null;
    const subtitle = node.isHolding
      ? [node.ticker, weight].filter(Boolean).join(' · ') || null
      : (node.sharedSummary ?? node.ticker ?? null);
    const labelWidth = Math.max(measure(title, 12), subtitle ? measure(subtitle, 10.5) : 0) + 8;
    const point = flow.get(node.id) ?? { x: 0, y: 0 };
    return {
      id: node.id,
      sourceName: node.name,
      name,
      ticker: node.ticker,
      isHolding: node.isHolding,
      weight: node.weight,
      sharedSummary: node.sharedSummary ?? null,
      sharedCount: Number(/\d+/.exec(node.sharedSummary ?? '')?.[0] ?? 0),
      size: sizeOf(node),
      colour: colourOf.get(node.id) ?? null,
      rank: Math.round((point.x - firstColumn) / 200),
      title,
      subtitle,
      labelWidth,
      labelHeight: TITLE_LINE + (subtitle ? SUBTITLE_LINE : 0),
      labelRoom: 1,
      initials: initialsOf(name),
      hue: hueOf(name),
      x: point.x,
      y: point.y,
    };
  });

  const byId = new Map(liveNodes.map((node) => [node.id, node]));
  const liveEdges = edges.flatMap((edge): LiveEdge[] => {
    const source = byId.get(edge.source);
    const target = byId.get(edge.target);
    if (!source || !target || source === target) return [];
    return [
      {
        id: edge.id,
        source,
        target,
        relationshipId: edge.relationshipId,
        reviewed: edge.reviewStatus === 'APPROVED',
        critical: edge.criticality !== null,
        colour: chains.edges.get(edge.id) ?? NEUTRAL_EDGE,
        summary: edge.summary,
      },
    ];
  });
  return { nodes: liveNodes, edges: liveEdges, chains: chains.key };
}

/** How big labels are drawn at a zoom: they grow a little when zoomed in and shrink when zoomed out. */
export function labelScale(k: number): number {
  return Math.min(1.15, Math.max(0.7, k));
}

/**
 * How visible a company's label is at a zoom, 0 to 1: zooming out, suppliers' and customers' labels fade first, then
 * holdings', until none is left; zooming in, they come back in the opposite order.
 */
export function labelZoom(k: number, holding: boolean): number {
  const [from, to] = holding ? [0.17, 0.25] : [0.3, 0.42];
  return Math.min(1, Math.max(0, (k - from) / (to - from)));
}

/** The box a company takes with its label, centred on (x, y) being the tile's middle: for spacing and for clicking. */
export function boxOf(
  node: Pick<LiveNode, 'size' | 'labelWidth' | 'labelHeight'> & { labelRoom?: number },
  x: number,
  y: number,
) {
  const room = node.labelRoom ?? 1;
  const width = Math.max(node.size, node.labelWidth * room);
  const top = y - node.size / 2;
  const bottom = y + node.size / 2 + LABEL_GAP + node.labelHeight * room;
  return { x1: x - width / 2, x2: x + width / 2, y1: top, y2: bottom };
}

/** A company and everything it is directly linked to, with the arrows between them: what pointing at it lights. */
export function neighbourhood(nodeId: string, edges: Pick<LiveEdge, 'id' | 'source' | 'target'>[]) {
  const nodeIds = new Set([nodeId]);
  const edgeIds = new Set<string>();
  for (const edge of edges) {
    if (edge.source.id !== nodeId && edge.target.id !== nodeId) continue;
    edgeIds.add(edge.id);
    nodeIds.add(edge.source.id);
    nodeIds.add(edge.target.id);
  }
  return { nodeIds, edgeIds };
}

/** A holding's whole chain, by its colour: every arrow of that colour and the companies at their ends. */
export function chainOf(colour: string, edges: Pick<LiveEdge, 'id' | 'source' | 'target' | 'colour'>[]) {
  const nodeIds = new Set<string>();
  const edgeIds = new Set<string>();
  for (const edge of edges) {
    if (edge.colour !== colour) continue;
    edgeIds.add(edge.id);
    nodeIds.add(edge.source.id);
    nodeIds.add(edge.target.id);
  }
  return { nodeIds, edgeIds };
}

/**
 * The colour of a company's alert dot, by how severe its most severe open alert is: the colours of the severity tags
 * on the Alerts page (--danger, --amber and --lime in styles.css).
 */
export const ALERT_COLOUR: Record<Severity, string> = { HIGH: '#ff7272', MEDIUM: '#f5b73b', LOW: '#c4f36a' };

/** A disruption on a relationship: how serious, what kind, and the headline it came from. */
export type Risk = { severity: Severity; eventType: string; title: string };

const SEVERITY_ORDER: Severity[] = ['LOW', 'MEDIUM', 'HIGH'];

/**
 * The relationships the portfolio's open alerts run through, each with its most serious disruption: the graph marks
 * them. An alert about a holding itself runs through no relationship, so it marks none.
 */
export function risksFrom(
  events: Pick<AlertEvent, 'status' | 'headline' | 'holdings'>[],
): Map<number, Risk> {
  const risks = new Map<number, Risk>();
  for (const event of events) {
    if (event.status === 'DISMISSED') continue;
    const { severity, eventType } = event.headline.assessment;
    for (const holding of event.holdings) {
      if (holding.relationshipId == null) continue;
      const known = risks.get(holding.relationshipId);
      if (known && SEVERITY_ORDER.indexOf(known.severity) >= SEVERITY_ORDER.indexOf(severity)) continue;
      risks.set(holding.relationshipId, { severity, eventType, title: event.headline.article.title });
    }
  }
  return risks;
}

/** An event type as shown: "PRODUCTION_STOP" reads "Production Stop". */
export function eventLabel(eventType: string): string {
  return eventType
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

// The drill-down. While every company is fully shown, holdings and the others shrink alike with the graph. Zoomed out
// past DRILL_FULL, suppliers and customers recede (smaller and fainter, until DRILL_GONE), while a holding shrinks at
// half the rate of the graph (drawn this much bigger than its size, at most), until only the holdings stand out.
// Zooming in brings them back.
const MOST_HOLDING_GROWTH = 2.6;
const DRILL_FULL = 0.45;
const DRILL_GONE = 0.18;

/** How much bigger than its size a holding is drawn at a zoom: past DRILL_FULL, it shrinks at half the graph's rate. */
export function holdingGrowth(k: number): number {
  return k >= DRILL_FULL ? 1 : Math.min(MOST_HOLDING_GROWTH, Math.sqrt(DRILL_FULL / k));
}

/** How present a supplier or customer is at a zoom, 0 to 1: fully above DRILL_FULL, gone at DRILL_GONE. */
export function companyPresence(k: number): number {
  const t = Math.min(1, Math.max(0, (k - DRILL_GONE) / (DRILL_FULL - DRILL_GONE)));
  return t * t * (3 - 2 * t);
}

/** How big a company is drawn at a zoom, as a share of its size: holdings grow, others recede to just over half. */
export function drawnShare(node: Pick<LiveNode, 'isHolding'>, k: number): number {
  return node.isHolding ? holdingGrowth(k) : 0.55 + 0.45 * companyPresence(k);
}
