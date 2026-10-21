// Turns a portfolio's holdings and relationships into graph elements. Kept apart from SupplyChainGraph.tsx (which
// draws it with Cytoscape) so this logic can be tested on its own, in milliseconds.
import type { Holding } from '../api/portfolios';
import type { Relationship, ReviewStatus } from '../api/relationships';

// weight is the holding's share of the portfolio, in percent, as uploaded; null for other companies, or when the CSV
// had no weight.
// sharedSummary is set (e.g. "Supplies 4 Holdings") only on a company that several holdings depend on; see
// sharedCompanies.
export type GraphNode = {
  id: string;
  name: string;
  ticker: string | null;
  isHolding: boolean;
  weight: number | null;
  sharedSummary?: string;
};
export type GraphEdge = {
  id: string;
  source: string;
  target: string;
  relationshipId: number;
  reviewStatus: ReviewStatus;
  summary: string;
};

/**
 * One node per company, one edge per relationship, the arrow pointing from supplier to customer.
 *
 * Every relationship's {@code company} is a holding: the backend only looks at the reports of companies the
 * portfolio holds. A holding whose company was found but that has no relationships yet (no supplier or customer was
 * named in its report) would otherwise be missing from the graph, so it is added as a node on its own. It is matched
 * to a node already in the graph by name, because {@link Holding} does not carry a company id.
 */
export function buildGraphElements(
  relationships: Relationship[],
  holdings: Holding[],
): { nodes: GraphNode[]; edges: GraphEdge[] } {
  const nodes = new Map<string, GraphNode>();

  function upsert(id: string, name: string, ticker: string | null, isHolding: boolean): void {
    const existing = nodes.get(id);
    if (existing) {
      if (isHolding) existing.isHolding = true;
      if (existing.ticker === null && ticker) existing.ticker = ticker;
      return;
    }
    nodes.set(id, { id, name, ticker, isHolding, weight: null });
  }

  const edges: GraphEdge[] = relationships.map((relationship) => {
    const companyId = String(relationship.company.id);
    const counterpartyId = String(relationship.counterparty.id);
    upsert(companyId, relationship.company.name, relationship.company.ticker, true);
    upsert(counterpartyId, relationship.counterparty.name, relationship.counterparty.ticker, false);

    // SUPPLIER: the counterparty supplies the company, so the arrow runs counterparty -> company.
    // CUSTOMER: the counterparty buys from the company, so the arrow runs company -> counterparty.
    const [source, target] =
      relationship.type === 'SUPPLIER' ? [counterpartyId, companyId] : [companyId, counterpartyId];
    return {
      id: `relationship-${relationship.id}`,
      source,
      target,
      relationshipId: relationship.id,
      reviewStatus: relationship.reviewStatus,
      summary: relationshipSummary(relationship),
    };
  });

  const nodeIdByName = new Map<string, string>();
  for (const node of nodes.values()) nodeIdByName.set(node.name, node.id);

  for (const holding of holdings) {
    if (!holding.companyName) continue; // not analysed yet, analysing, failed, or no report was found
    const existingId = nodeIdByName.get(holding.companyName);
    if (existingId) {
      const existing = nodes.get(existingId)!;
      existing.isHolding = true;
      existing.weight = holding.weight;
      continue;
    }
    const id = `holding-${holding.id}`;
    nodes.set(id, { id, name: holding.companyName, ticker: holding.ticker, isHolding: true, weight: holding.weight });
    nodeIdByName.set(holding.companyName, id);
  }

  return { nodes: [...nodes.values()], edges };
}

// Endings that say nothing about which company it is, so the graph leaves them out. The same list the backend's
// CompanyDirectory ignores when comparing names, so "Taiwan Semiconductor Manufacturing Co., Ltd." and
// "... Company Limited" both become "Taiwan Semiconductor Manufacturing".
const LEGAL_ENDINGS = new Set([
  'inc', 'incorporated', 'corp', 'corporation', 'co', 'company', 'ltd', 'limited',
  'plc', 'nv', 'sa', 'ag', 'se', 'llc', 'lp',
]);

/**
 * A company's name without its legal ending: "NVIDIA Corporation" becomes "NVIDIA", "Advanced Micro Devices, Inc."
 * becomes "Advanced Micro Devices". The SEC's registration mark after a slash ("QUALCOMM INC/DE") goes too. Endings
 * are taken off the end one word at a time, so "Co., Ltd." loses both, and a name is never emptied: "Company" stays.
 */
export function plainName(name: string): string {
  const words = name.replace(/\s*\/\s*[A-Za-z]+\s*\/?\s*$/, '').trim().split(/\s+/);
  const ending = (word: string) => word.toLowerCase().replace(/[^a-z0-9]/g, '');
  while (words.length > 1 && LEGAL_ENDINGS.has(ending(words[words.length - 1]!))) words.pop();
  // The comma before a dropped ending ("Devices, Inc.") is left behind; remove it.
  return words.join(' ').replace(/[\s,]+$/, '');
}

// The longest name that fits under a company's tile on the graph.
export const SHORT_NAME_LENGTH = 14;

/** A name short enough for the graph: its display name, cut with an ellipsis if it is still too long. */
/**
 * A company's name as the website shows it, the same way for every company: its plain name (no legal ending), in Title
 * Case. The SEC writes most names in capitals ("NVIDIA CORP", "AMAZON COM INC"); those words become Title Case
 * ("NVIDIA CORP" becomes "Nvidia"), except short ones of four letters or fewer, which are usually acronyms and stay as they are ("ASML
 * Holding", "AMD"). A trailing "COM" is the company's web address, so "AMAZON COM" becomes "Amazon.com". A name already
 * in mixed case ("Apple Inc.") keeps its own capitals. The stored name is never changed.
 */
export function displayName(name: string): string {
  const plain = plainName(name);
  // Judged on the whole stored name: "NVIDIA Corporation" is written in mixed case, so its "NVIDIA" stays as it is.
  if (/[a-z]/.test(name)) return plain;
  const words = plain.split(' ');
  if (words.length > 1 && words[words.length - 1] === 'COM') {
    words.pop();
    words[words.length - 1] += '.com';
  }
  return words.map((word) => (/[A-Z]{5,}/.test(word) ? titleWord(word) : word)).join(' ');
}

// "MANUFACTURING" becomes "Manufacturing"; each part of "SK-HYNIX" or "AMAZON.com" is done on its own.
function titleWord(word: string): string {
  return word.replace(/[A-Z]+/g, (part) => part[0] + part.slice(1).toLowerCase());
}

export function shortName(name: string): string {
  const plain = displayName(name);
  if (plain.length <= SHORT_NAME_LENGTH) return plain;
  return `${plain.slice(0, SHORT_NAME_LENGTH - 1).trimEnd()}\u2026`;
}

/** A holding's weight as a percentage: 18 becomes "18%", 12.5 "12.5%", 3.333 "3.33%". Null when there is none. */
export function formatWeight(weight: number | null): string | null {
  if (weight === null || !Number.isFinite(weight)) return null;
  return `${Number(weight.toFixed(2))}%`;
}

/** The side of a company's square tile on the graph, in pixels. Holdings with a weight grow from here. */
export const TILE_SIZE = 44;
/** The biggest a holding's tile can grow, so one large position cannot cover the graph. */
export const MAX_TILE_SIZE = 110;

/**
 * How big a holding's tile is drawn: 44 pixels plus 9 times the square root of its weight, at most 110, so bigger
 * positions stand out while small ones stay readable. The square root keeps the growth gentle: 4% adds 18 pixels,
 * 25% adds 45. A holding with no weight, or a weight of 0, keeps the usual size.
 */
export function holdingSize(weight: number | null): number {
  if (weight === null || !Number.isFinite(weight) || weight <= 0) return TILE_SIZE;
  return Math.min(MAX_TILE_SIZE, TILE_SIZE + 9 * Math.sqrt(weight));
}

/**
 * The label under a company's tile: a holding's ticker and weight ("NVDA 18%"), otherwise its ticker, or a short
 * name when it has no ticker.
 */
export function nodeLabel(node: GraphNode): string {
  if (!node.ticker) return shortName(node.name);
  const weight = node.isHolding ? formatWeight(node.weight) : null;
  return weight ? `${node.ticker} ${weight}` : node.ticker;
}

const LOGO_HOST = 'https://img.logo.dev';
// Logo.dev's usual JPEG, whose white background keeps a dark logo visible on the dark graph, at twice the size it is
// shown so it stays sharp when zoomed in. fallback=404 makes Logo.dev answer "not found" for a company it does not
// know, instead of drawing its own letter, so the graph can show its own.
const LOGO_OPTIONS = 'size=64&retina=true&fallback=404';

/**
 * Where a company's logo is on Logo.dev: by ticker when it has one; otherwise by the website domain the backend checked
 * belongs to it (see GET /api/logos/domains). Null without either, or without a Logo.dev token: the graph then shows the
 * company's letter. Logo.dev is never asked by name, because it answers a name it does not know with whichever company
 * shares a word of it (a restaurant called Yuan for King Yuan Electronics), and a letter is better than a wrong logo.
 */
export function logoUrl(node: Pick<GraphNode, 'name' | 'ticker'>, token: string, domain?: string): string | null {
  if (!token) return null;
  const path = node.ticker ? `ticker/${encodeURIComponent(node.ticker)}` : domain ? encodeURIComponent(domain) : null;
  return path ? `${LOGO_HOST}/${path}?token=${encodeURIComponent(token)}&${LOGO_OPTIONS}` : null;
}

/** The part of the graph that stays lit while one company is in focus. */
export type Focus = { nodeIds: Set<string>; edgeIds: Set<string> };

/**
 * What stays lit when the user points at one company: the company itself, its direct suppliers and customers, and the
 * arrows that join them to it. Everything else is faded. A company with no arrows is lit on its own.
 *
 * Only arrows that touch the company are kept. An arrow between two of its neighbours is not one of the company's own
 * connections, so it fades with the rest.
 */
export function focusOn(nodeId: string, edges: GraphEdge[]): Focus {
  const nodeIds = new Set<string>([nodeId]);
  const edgeIds = new Set<string>();
  for (const edge of edges) {
    if (edge.source !== nodeId && edge.target !== nodeId) continue;
    edgeIds.add(edge.id);
    nodeIds.add(edge.source);
    nodeIds.add(edge.target);
  }
  return { nodeIds, edgeIds };
}

// The colours of the holdings' chains, handed out in this order. Checked as a set against the graph's dark panel
// (#0b2028) with the dataviz skill's palette validator, every pair against every other: each pair is told apart with
// full colour vision (difference at least 15) and with each kind of colour blindness (at least 9), and each stands out
// from the panel by at least 3:1. None is the teal, lime or amber that already mean holding, chosen and shared.
export const CHAIN_COLOURS = ['#d7587e', '#185afa', '#0fac13', '#8480f2', '#a25927', '#2c9fbb', '#9d45ab'];

/** A holding whose chain has a colour, for the graph's key: its ticker (or name), and the colour. */
export type ChainKey = { id: string; label: string; colour: string };

/**
 * Gives each holding's chain its own colour, so the user can follow it across the graph: every arrow into or out of a
 * holding takes that holding's colour, and an arrow further out (a supplier's supplier) takes the colour of the
 * holding it leads to. An arrow from one holding to another takes its customer's colour. Holdings with arrows get
 * colours in the order of their tickers (names for those without); there are only as many colours as CHAIN_COLOURS
 * holds, and a colour is never used twice, so the arrows of any further holding keep no colour (the graph draws them
 * grey), as do arrows that lead to no holding at all.
 */
export function chainColours(
  nodes: Pick<GraphNode, 'id' | 'name' | 'ticker' | 'isHolding'>[],
  edges: Pick<GraphEdge, 'id' | 'source' | 'target'>[],
): { edges: Map<string, string>; key: ChainKey[] } {
  const linked = new Set(edges.flatMap((edge) => [edge.source, edge.target]));
  const labelOf = (node: Pick<GraphNode, 'name' | 'ticker'>) => node.ticker ?? displayName(node.name);
  const holdings = nodes
    .filter((node) => node.isHolding && linked.has(node.id))
    .sort((a, b) => labelOf(a).localeCompare(labelOf(b)));
  const key = holdings
    .slice(0, CHAIN_COLOURS.length)
    .map((node, index) => ({ id: node.id, label: labelOf(node), colour: CHAIN_COLOURS[index]! }));
  const colourOf = new Map(key.map((entry) => [entry.id, entry.colour]));
  const rank = new Map(holdings.map((node, index) => [node.id, index]));

  // The nearest holding along the arrows, walking towards customers (or towards suppliers): of those equally near, the
  // first in ticker order.
  const nearest = (start: string, towards: 'target' | 'source'): string | null => {
    const from = towards === 'target' ? 'source' : 'target';
    const seen = new Set([start]);
    let ring = [start];
    while (ring.length) {
      const found = ring.filter((id) => rank.has(id)).sort((a, b) => rank.get(a)! - rank.get(b)!);
      if (found.length) return found[0]!;
      const next: string[] = [];
      for (const id of ring) {
        for (const edge of edges) {
          const onward = edge[towards];
          if (edge[from] === id && !seen.has(onward)) {
            seen.add(onward);
            next.push(onward);
          }
        }
      }
      ring = next;
    }
    return null;
  };

  const result = new Map<string, string>();
  for (const edge of edges) {
    // Its customer if that is a holding, else its supplier if that is, else the holding it leads to (or comes from).
    const holding = rank.has(edge.target)
      ? edge.target
      : rank.has(edge.source)
        ? edge.source
        : (nearest(edge.target, 'target') ?? nearest(edge.source, 'source'));
    const colour = holding ? colourOf.get(holding) : undefined;
    if (colour) result.set(edge.id, colour);
  }
  return { edges: result, key };
}

/** A colour mixed with white: share 0 is the colour itself, 1 is white. For the bright packets that flow along a line. */
export function tint(hex: string, share: number): string {
  const channels = [1, 3, 5].map((at) => parseInt(hex.slice(at, at + 2), 16));
  return `#${channels
    .map((value) => Math.round(value + (255 - value) * share).toString(16).padStart(2, '0'))
    .join('')}`;
}

// The packets that flow along every line: how far apart they are and how fast they go, in the graph's own units (so
// they keep their spacing as the user zooms), how many one line can carry, and how far from each end they fade.
export const PACKET_SPACING = 120;
export const PACKET_SPEED = 60;
export const MAX_PACKETS = 8;
const PACKET_FADE = 12;

/**
 * Where the packets on a line of the given length (on screen) are at a moment, from supplier (0) to customer (1), each
 * with how visible it is: all lines carry packets at the same speed and spacing, whatever their length, so the whole
 * graph flows evenly, the way the prototype did. A packet fades in as it leaves the supplier and out as it reaches the
 * customer. A line too long for MAX_PACKETS spaces them further apart.
 */
export function packetsAlong(length: number, elapsedMs: number, zoom: number): { at: number; opacity: number }[] {
  if (!(length > 0)) return [];
  const spacing = Math.max(PACKET_SPACING * zoom, length / MAX_PACKETS);
  const travelled = (elapsedMs / 1000) * PACKET_SPEED * zoom;
  const offset = ((travelled % spacing) + spacing) % spacing;
  const packets: { at: number; opacity: number }[] = [];
  for (let distance = offset; distance < length && packets.length < MAX_PACKETS; distance += spacing) {
    packets.push({ at: distance / length, opacity: Math.min(1, distance / PACKET_FADE, (length - distance) / PACKET_FADE) });
  }
  return packets;
}

/**
 * A company that is not a holding, but supplies, or buys from, several of the portfolio's holdings: where one
 * company's trouble would hit several positions at once. The counts are of holdings, never of arrows.
 */
export type SharedCompany = {
  id: string;
  name: string;
  ticker: string | null;
  suppliesHoldings: number;
  buysFromHoldings: number;
};

// A company is shared once two holdings depend on it, on either side.
const SHARED_FROM = 2;

/**
 * The companies several holdings depend on, the most shared first.
 *
 * <ul>
 *   <li>A holding is never listed, even when other holdings supply it: the highlight is for companies the portfolio
 *       does not hold.</li>
 *   <li>Holdings are counted, not arrows: one holding that names the same company twice counts once.</li>
 *   <li>A company serving only one holding is not listed.</li>
 * </ul>
 *
 * Companies of equal rank are put in alphabetical order, so the list never shuffles between visits.
 */
export function sharedCompanies(relationships: Relationship[], nodes: GraphNode[]): SharedCompany[] {
  const holdingIds = new Set(nodes.filter((node) => node.isHolding).map((node) => node.id));
  const supplies = new Map<string, Set<number>>();
  const buysFrom = new Map<string, Set<number>>();
  const companies = new Map<string, { name: string; ticker: string | null }>();

  for (const relationship of relationships) {
    const id = String(relationship.counterparty.id);
    if (holdingIds.has(id)) continue;
    companies.set(id, { name: relationship.counterparty.name, ticker: relationship.counterparty.ticker });
    // SUPPLIER: the counterparty supplies the holding. CUSTOMER: the counterparty buys from the holding.
    const side = relationship.type === 'SUPPLIER' ? supplies : buysFrom;
    const held = side.get(id) ?? new Set<number>();
    held.add(relationship.company.id);
    side.set(id, held);
  }

  const shared: SharedCompany[] = [];
  for (const [id, { name, ticker }] of companies) {
    const suppliesHoldings = supplies.get(id)?.size ?? 0;
    const buysFromHoldings = buysFrom.get(id)?.size ?? 0;
    if (suppliesHoldings < SHARED_FROM && buysFromHoldings < SHARED_FROM) continue;
    shared.push({ id, name, ticker, suppliesHoldings, buysFromHoldings });
  }

  // Only a side that is itself shared counts towards the rank: a company that supplies four holdings and buys from
  // one is ranked as four.
  const rank = (company: SharedCompany) =>
    (company.suppliesHoldings >= SHARED_FROM ? company.suppliesHoldings : 0) +
    (company.buysFromHoldings >= SHARED_FROM ? company.buysFromHoldings : 0);
  return shared.sort((a, b) => rank(b) - rank(a) || a.name.localeCompare(b.name));
}

/**
 * The label for a shared company, e.g. "Supplies 4 Holdings" or "Buys From 2 Holdings". A side held by only one
 * holding is left out, so it never reads "Supplies 1 Holdings"; a company shared on both sides gets both.
 */
export function sharedSummary(company: SharedCompany): string {
  const parts: string[] = [];
  if (company.suppliesHoldings >= SHARED_FROM) parts.push(`Supplies ${company.suppliesHoldings} Holdings`);
  if (company.buysFromHoldings >= SHARED_FROM) parts.push(`Buys From ${company.buysFromHoldings} Holdings`);
  return parts.join(' · ');
}

/** The graph's nodes, with each shared company carrying its summary so the graph can draw it amber and bigger. */
export function withSharedSummaries(nodes: GraphNode[], shared: SharedCompany[]): GraphNode[] {
  const summaryById = new Map(shared.map((company) => [company.id, sharedSummary(company)]));
  return nodes.map((node) => {
    const sharedSummaryText = summaryById.get(node.id);
    return sharedSummaryText === undefined ? node : { ...node, sharedSummary: sharedSummaryText };
  });
}

/** One of a company's suppliers or customers on the graph, with the relationship that links them. */
export type Connection = { relationship: Relationship; company: Relationship['company'] };

/**
 * A company's suppliers and customers on the graph, each listed once, in the order the relationships came.
 *
 * The company can be either end of a relationship. As the relationship's own company: SUPPLIER means the counterparty
 * supplies it, CUSTOMER that the counterparty buys from it. As the counterparty, the other way round.
 */
export function connectionsOf(nodeId: string, relationships: Relationship[]): { suppliers: Connection[]; customers: Connection[] } {
  const suppliers = new Map<number, Connection>();
  const customers = new Map<number, Connection>();
  for (const relationship of relationships) {
    const isCompany = String(relationship.company.id) === nodeId;
    const isCounterparty = String(relationship.counterparty.id) === nodeId;
    if (!isCompany && !isCounterparty) continue;
    const other = isCompany ? relationship.counterparty : relationship.company;
    const otherSupplies = (relationship.type === 'SUPPLIER') === isCompany;
    const side = otherSupplies ? suppliers : customers;
    if (!side.has(other.id)) side.set(other.id, { relationship, company: other });
  }
  return { suppliers: [...suppliers.values()], customers: [...customers.values()] };
}

/**
 * Where each holding is on the graph: holding id to node id. A holding not on the graph (not analysed yet, or no
 * report found) is missing. Matched by company name, as {@link buildGraphElements} does, because a holding does not
 * carry a company id.
 */
export function holdingNodeIds(holdings: Holding[], nodes: GraphNode[]): Map<number, string> {
  const nodeIdByName = new Map(nodes.map((node) => [node.name, node.id]));
  const result = new Map<number, string>();
  for (const holding of holdings) {
    const nodeId = holding.companyName ? nodeIdByName.get(holding.companyName) : undefined;
    if (nodeId) result.set(holding.id, nodeId);
  }
  return result;
}

/** A relationship in a sentence, supplier first: "Taiwan Semiconductor Manufacturing supplies NVIDIA: semiconductor wafers". */
export function relationshipSummary(relationship: Relationship): string {
  const [supplier, customer] =
    relationship.type === 'SUPPLIER'
      ? [relationship.counterparty, relationship.company]
      : [relationship.company, relationship.counterparty];
  const sentence = `${displayName(supplier.name)} supplies ${displayName(customer.name)}`;
  return relationship.provides ? `${sentence}: ${relationship.provides}` : sentence;
}

/** What is chosen on the Supply Chain page: a company, a relationship, or nothing. */
export type Selection = { kind: 'company'; nodeId: string } | { kind: 'relationship'; relationshipId: number } | null;

export type Point = { x: number; y: number };

// How far a dragged company's direct suppliers and customers follow it: a share of its move, so they trail behind it
// like elastic rather than moving with it.
export const FOLLOW_SHARE = 0.35;

/** Where a neighbour heads while a company is dragged from {@code from} to {@code to}: its resting place, moved by a share. */
export function followTarget(rest: Point, from: Point, to: Point, share: number = FOLLOW_SHARE): Point {
  return { x: rest.x + (to.x - from.x) * share, y: rest.y + (to.y - from.y) * share };
}

// How much of the remaining distance a moving neighbour covers each frame: quick at first, then slowing to a stop.
export const EASE = 0.18;

/** One frame of easing towards a target. Returns where the company is now, and whether it has arrived (within half a pixel). */
export function easeTowards(current: Point, target: Point, ease: number = EASE): { position: Point; arrived: boolean } {
  const dx = target.x - current.x;
  const dy = target.y - current.y;
  if (Math.hypot(dx, dy) < 0.5) return { position: target, arrived: true };
  return { position: { x: current.x + dx * ease, y: current.y + dy * ease }, arrived: false };
}

/**
 * Lays the graph out as a flow, the way goods move: every company in a column by its place in the chain, so each
 * arrow runs left to right from supplier to customer (suppliers' suppliers, suppliers, then their customers). Within a
 * column, companies are ordered to keep arrows from crossing: a few passes that place each one at the average height of
 * its neighbours in the next column over. Each group of connected companies is laid out on its own, from (0, 0); the
 * graph then packs the groups together.
 *
 * Companies in a loop (each supplying the other) cannot all be left of each other: the loop is broken at the arrow that
 * closes it, which is then the only one allowed to point backwards. A company that only supplies is placed in the
 * column just before its first customer, so it sits next to it rather than far out on the left.
 *
 * {@code heightOf} is how tall a company is with its label box, so taller ones get more room in their column.
 */
export function flowLayout(
  nodeIds: string[],
  edges: Pick<GraphEdge, 'source' | 'target'>[],
  heightOf: (id: string) => number,
  columnGap = 200,
  rowGap = 20,
): Map<string, Point> {
  const out = new Map<string, string[]>(nodeIds.map((id) => [id, []]));
  const into = new Map<string, string[]>(nodeIds.map((id) => [id, []]));
  for (const { source, target } of edges) {
    if (source === target || !out.has(source) || !out.has(target)) continue;
    out.get(source)!.push(target);
    into.get(target)!.push(source);
  }

  // The groups of connected companies, in the order their first company comes.
  const groupOf = new Map<string, number>();
  const groups: string[][] = [];
  for (const start of nodeIds) {
    if (groupOf.has(start)) continue;
    const group: string[] = [];
    const stack = [start];
    groupOf.set(start, groups.length);
    while (stack.length) {
      const id = stack.pop()!;
      group.push(id);
      for (const next of [...out.get(id)!, ...into.get(id)!]) {
        if (!groupOf.has(next)) {
          groupOf.set(next, groups.length);
          stack.push(next);
        }
      }
    }
    groups.push(group);
  }

  const positions = new Map<string, Point>();
  for (const group of groups) {
    // Arrows that close a loop are found with a depth-first walk and left out of the column rule.
    const loopArrows = new Set<string>();
    const state = new Map<string, 'open' | 'done'>();
    const walk = (id: string) => {
      state.set(id, 'open');
      for (const next of out.get(id)!) {
        if (state.get(next) === 'open') loopArrows.add(`${id}>${next}`);
        else if (!state.has(next)) walk(next);
      }
      state.set(id, 'done');
    };
    group.forEach((id) => {
      if (!state.has(id)) walk(id);
    });
    const forward = (from: string) => out.get(from)!.filter((to) => !loopArrows.has(`${from}>${to}`));
    const backward = (to: string) => into.get(to)!.filter((from) => !loopArrows.has(`${from}>${to}`));

    // Columns: the longest chain of suppliers before each company.
    const column = new Map<string, number>();
    const columnOf = (id: string): number => {
      if (column.has(id)) return column.get(id)!;
      column.set(id, 0); // guards the walk; loops are already removed
      const value = Math.max(0, ...backward(id).map((from) => columnOf(from) + 1));
      column.set(id, value);
      return value;
    };
    group.forEach(columnOf);
    // A company that only supplies moves up to the column just before its nearest customer.
    for (const id of group) {
      if (backward(id).length === 0 && forward(id).length > 0) {
        column.set(id, Math.max(0, Math.min(...forward(id).map((to) => column.get(to)!)) - 1));
      }
    }

    const columns: string[][] = [];
    group.forEach((id) => (columns[column.get(id)!] ??= []).push(id));
    for (let c = 0; c < columns.length; c++) columns[c] ??= [];

    // Ordering passes: by the average place of each company's neighbours in the column before, then the one after.
    const place = new Map<string, number>();
    const renumber = (ids: string[]) => ids.forEach((id, index) => place.set(id, index));
    columns.forEach(renumber);
    const average = (ids: string[], fallback: number) =>
      ids.length ? ids.reduce((sum, id) => sum + place.get(id)!, 0) / ids.length : fallback;
    for (let pass = 0; pass < 4; pass++) {
      for (let c = 1; c < columns.length; c++) {
        columns[c]!.sort((a, b) => average(backward(a), place.get(a)!) - average(backward(b), place.get(b)!));
        renumber(columns[c]!);
      }
      for (let c = columns.length - 2; c >= 0; c--) {
        columns[c]!.sort((a, b) => average(forward(a), place.get(a)!) - average(forward(b), place.get(b)!));
        renumber(columns[c]!);
      }
    }

    // Each column is stacked from the top, its companies centred on the tallest column's middle.
    const columnHeights = columns.map((ids) => ids.reduce((sum, id) => sum + heightOf(id) + rowGap, -rowGap));
    const tallest = Math.max(0, ...columnHeights);
    columns.forEach((ids, c) => {
      let y = (tallest - columnHeights[c]!) / 2;
      for (const id of ids) {
        const height = heightOf(id);
        positions.set(id, { x: c * columnGap, y: y + height / 2 });
        y += height + rowGap;
      }
    });
  }
  return positions;
}

/** What the user did to a portfolio's graph: the companies they dragged, and the zoom and pan. */
export type SavedLayout = { positions: Record<string, Point>; zoom?: number; pan?: Point };

/** The saved layout under this key, or null when there is none, it cannot be read, or the browser's storage is off. */
export function readSavedLayout(key: string): SavedLayout | null {
  try {
    const text = window.sessionStorage.getItem(key);
    if (!text) return null;
    const value = JSON.parse(text) as SavedLayout;
    return value && typeof value.positions === 'object' && value.positions !== null ? value : null;
  } catch {
    return null;
  }
}

/** Saves a layout for as long as the browser tab is open. Storage that is full or switched off is simply skipped. */
export function saveLayout(key: string, layout: SavedLayout): void {
  try {
    window.sessionStorage.setItem(key, JSON.stringify(layout));
  } catch {
    // Remembering the layout is a convenience: without storage, the graph opens in its calculated arrangement.
  }
}

/** Forgets a portfolio's saved layout, so its graph opens in the calculated arrangement again. */
export function forgetLayout(key: string): void {
  try {
    window.sessionStorage.removeItem(key);
  } catch {
    // Nothing was saved if storage is switched off.
  }
}

/** The space a company takes, for laying it out: its tile and label box together, and how far that box's middle sits
 * below the tile's middle (the label hangs under the tile). */
export type NodeBox = { width: number; height: number; below: number };

// The room kept between two companies side by side, and one above the other, and the extra room between bands, where
// the arrows run.
const SPREAD_GAP_X = 36;
const SPREAD_GAP_Y = 28;
const SPREAD_BAND_GAP = 90;

/**
 * Lays the graph out to fill a frame of the given shape (width ÷ height), still reading left to right. Each column of
 * the flow (suppliers' suppliers, suppliers, holdings, customers) becomes a band across the frame. A band holding many
 * companies gets several lanes side by side instead of standing in one tall line, and every band spreads its rows
 * evenly over the same height, so a band with few companies fills it too. The height is chosen so the whole group comes
 * closest to the frame's shape. Within a band, companies keep the flow's order (which keeps arrows from crossing), row
 * by row, and every other lane sits half a row lower, so the graph does not look like a grid. No two companies can
 * overlap, and as bands never overlap, every arrow still runs from left to right. Separate groups are laid out on their
 * own (the graph packs them afterwards). Nothing is random: the same graph always comes out the same.
 */
export function spreadLayout(
  nodeIds: string[],
  edges: Pick<GraphEdge, 'source' | 'target'>[],
  boxOf: (id: string) => NodeBox,
  frameRatio: number,
): Map<string, Point> {
  const columnGap = 200;
  const flow = flowLayout(nodeIds, edges, (id) => boxOf(id).height, columnGap);
  const ratio = Number.isFinite(frameRatio) && frameRatio > 0.2 ? frameRatio : 1.6;

  // The groups of connected companies, laid out one at a time.
  const linked = new Map<string, string[]>(nodeIds.map((id) => [id, []]));
  for (const { source, target } of edges) {
    if (source === target || !linked.has(source) || !linked.has(target)) continue;
    linked.get(source)!.push(target);
    linked.get(target)!.push(source);
  }
  const seen = new Set<string>();
  const groups: string[][] = [];
  for (const start of nodeIds) {
    if (seen.has(start)) continue;
    const group: string[] = [];
    const stack = [start];
    seen.add(start);
    while (stack.length) {
      const id = stack.pop()!;
      group.push(id);
      for (const next of linked.get(id)!) {
        if (!seen.has(next)) {
          seen.add(next);
          stack.push(next);
        }
      }
    }
    groups.push(group);
  }

  const positions = new Map<string, Point>();
  for (const group of groups) {
    // Each band's companies in the flow's order, and the room one of them needs.
    const bands: string[][] = [];
    for (const id of group) (bands[Math.round(flow.get(id)!.x / columnGap)] ??= []).push(id);
    const filled = bands
      .filter((band) => band?.length)
      .map((band) => band.sort((a, b) => flow.get(a)!.y - flow.get(b)!.y));
    const cells = filled.map((band) => ({
      width: Math.max(...band.map((id) => boxOf(id).width)) + SPREAD_GAP_X,
      height: Math.max(...band.map((id) => boxOf(id).height)) + SPREAD_GAP_Y,
    }));

    // How each band is arranged for a given height: as many rows as fit, and as many lanes as that then takes.
    const arrange = (height: number) =>
      filled.map((band, b) => {
        const rows = Math.max(1, Math.min(band.length, Math.floor(height / cells[b]!.height)));
        const lanes = Math.ceil(band.length / rows);
        return { rows: Math.ceil(band.length / lanes), lanes };
      });
    const widthAt = (height: number) =>
      arrange(height).reduce((sum, { lanes }, b) => sum + lanes * cells[b]!.width + SPREAD_BAND_GAP, -SPREAD_BAND_GAP);

    // The height whose arrangement comes closest to the frame's shape: from one row up to every company in one line.
    const lowest = Math.max(...cells.map((cell) => cell.height));
    const highest = Math.max(...filled.map((band, b) => band.length * cells[b]!.height));
    let height = lowest;
    let best = Infinity;
    for (let candidate = lowest; candidate <= highest; candidate += 4) {
      const miss = Math.abs(Math.log(widthAt(candidate) / candidate / ratio));
      if (miss < best - 1e-9) {
        best = miss;
        height = candidate;
      }
    }

    let left = 0;
    arrange(height).forEach(({ rows, lanes }, b) => {
      const band = filled[b]!;
      const cell = cells[b]!;
      // Rows spread evenly over the whole height; every other lane half a row lower, the band then centred again.
      const rowHeight = height / rows;
      const sway = rows > 1 && lanes > 1 ? rowHeight / 2 : 0;
      band.forEach((id, index) => {
        const row = Math.floor(index / lanes);
        const lane = index % lanes;
        const middle = (row + 0.5) * rowHeight + (lane % 2) * sway - sway / 2;
        positions.set(id, {
          x: left + (lane + 0.5) * cell.width,
          y: middle - boxOf(id).below,
        });
      });
      left += lanes * cell.width + SPREAD_BAND_GAP;
    });
  }
  return positions;
}
