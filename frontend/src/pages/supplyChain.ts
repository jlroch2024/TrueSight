// Turns a portfolio's holdings and relationships into graph elements. Kept apart from the graph that draws them
// (src/graph/) so this logic can be tested on its own, in milliseconds.
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
 * colours largest first, by their weight in the portfolio (holdings of equal weight, or with none, by ticker, or name
 * for those without); there are only as many colours as CHAIN_COLOURS holds, and a colour is never used twice, so the
 * arrows of any smaller holding keep no colour (the graph draws them grey), as do arrows that lead to no holding at all.
 */
export function chainColours(
  nodes: Pick<GraphNode, 'id' | 'name' | 'ticker' | 'isHolding' | 'weight'>[],
  edges: Pick<GraphEdge, 'id' | 'source' | 'target'>[],
): { edges: Map<string, string>; key: ChainKey[] } {
  const linked = new Set(edges.flatMap((edge) => [edge.source, edge.target]));
  const labelOf = (node: Pick<GraphNode, 'name' | 'ticker'>) => node.ticker ?? displayName(node.name);
  const holdings = nodes
    .filter((node) => node.isHolding && linked.has(node.id))
    .sort((a, b) => (b.weight ?? -1) - (a.weight ?? -1) || labelOf(a).localeCompare(labelOf(b)));
  const key = holdings
    .slice(0, CHAIN_COLOURS.length)
    .map((node, index) => ({ id: node.id, label: labelOf(node), colour: CHAIN_COLOURS[index]! }));
  const colourOf = new Map(key.map((entry) => [entry.id, entry.colour]));
  const rank = new Map(holdings.map((node, index) => [node.id, index]));

  // The nearest holding along the arrows, walking towards customers (or towards suppliers): of those equally near, the
  // largest.
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

// The arrows are dashes that travel from supplier to customer: 8 px of line, then a 5 px gap, repeating, at 20 px a
// second.
export const DASH_PATTERN = [8, 5];
const DASH_PERIOD = 13;
const DASH_SPEED = 20;

/**
 * Where the dash pattern sits after {@code elapsedMs} of animation, as a value for Cytoscape's {@code line-dash-offset}.
 * An arrow is drawn from its supplier to its customer, and a larger offset slides the pattern back towards the
 * supplier, so for the dashes to travel towards the customer the offset gets smaller as time passes. It wraps once a
 * whole dash and gap have gone by, so it never grows without end.
 */
export function dashOffsetAt(elapsedMs: number): number {
  return 0 - (((elapsedMs / 1000) * DASH_SPEED) % DASH_PERIOD);
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

// How far a floating company drifts from where it rests (a holding, as the heavier, a little less), how long one of its
// slow sways takes, and how long its drift takes to fade back in once it has been moved.
export const FLOAT_REACH = 10;
const HOLDING_FLOAT_REACH = 7;
const FLOAT_PERIOD_MS = 9000;
export const FLOAT_FADE_MS = 1200;

/**
 * How far a company has floated from where it rests, {@code elapsedMs} into the animation: each company sways along its
 * own slow, looping path, two gentle sways on each axis at unrelated speeds, so no two move in step and none repeats
 * soon. The path comes from the company's id, so it is the same every time the graph is drawn. It never goes further
 * than FLOAT_REACH from the resting place (a holding no further than HOLDING_FLOAT_REACH).
 */
export function floatOffset(id: string, elapsedMs: number, isHolding: boolean): Point {
  let hash = 0;
  for (const character of id) hash = (Math.imul(hash, 31) + character.charCodeAt(0)) | 0;
  const seed = (shift: number) => ((hash >>> shift) & 255) / 255;
  // Each axis sways at most this far, so together they never go further than the reach.
  const reach = (isHolding ? HOLDING_FLOAT_REACH : FLOAT_REACH) / Math.SQRT2;
  // Each company's own pace: its sways take between 0.8 and 1.3 times the usual period.
  const speedX = (2 * Math.PI) / (FLOAT_PERIOD_MS * (0.8 + seed(0) * 0.5));
  const speedY = (2 * Math.PI) / (FLOAT_PERIOD_MS * (0.8 + seed(8) * 0.5));
  const phaseX = seed(16) * 2 * Math.PI;
  const phaseY = seed(24) * 2 * Math.PI;
  const t = elapsedMs;
  return {
    x: reach * (0.7 * Math.sin(t * speedX + phaseX) + 0.3 * Math.cos(t * speedX * 1.618 + phaseY)),
    y: reach * (0.7 * Math.cos(t * speedY + phaseY) + 0.3 * Math.sin(t * speedY * 1.414 + phaseX)),
  };
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
export type SavedLayout = {
  positions: Record<string, Point>;
  zoom?: number;
  pan?: Point;
  // Which of the positions the user placed (the rest are where the graph settled); without it, every position was
  // placed by the user. And the room labels were given when the graph settled, so it can be put back exactly.
  placed?: string[];
  labelRoom?: number;
};

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
 * evenly over the same height, so a band with few companies fills it too. The height is chosen so the whole group
 * comes closest to the frame's shape. Within a band, companies keep the flow's order (which keeps arrows from
 * crossing), row by row (in a row, those sending more arrows onwards further right), and every other lane sits half a
 * row lower, so the graph does not look like a grid. No two companies can overlap, and as bands never overlap, every
 * arrow still runs from left to right. Separate groups are laid out on their own (the graph packs them afterwards).
 * Nothing is random: the same graph always comes out the same.
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
    // How far left and right each company may later move without leaving its band.
    const bandOf = new Map<string, { from: number; to: number }>();
    arrange(height).forEach(({ rows, lanes }, b) => {
      const band = filled[b]!;
      const cell = cells[b]!;
      // Rows spread evenly over the whole height; every other lane half a row lower, the band then centred again.
      const rowHeight = height / rows;
      const sway = rows > 1 && lanes > 1 ? rowHeight / 2 : 0;
      // Within a row, a company sending more arrows onwards than it receives sits further right: its arrows leave
      // to the right, so they do not run through the row's other companies (and arrows coming in from the left do not
      // run through it).
      const leaning = (id: string) =>
        edges.filter((edge) => edge.source === id).length - edges.filter((edge) => edge.target === id).length;
      const ordered = Array.from({ length: rows }, (_, row) =>
        band.slice(row * lanes, (row + 1) * lanes).sort((a, b) => leaning(a) - leaning(b)),
      ).flat();
      ordered.forEach((id, index) => {
        const row = Math.floor(index / lanes);
        const lane = index % lanes;
        const middle = (row + 0.5) * rowHeight + (lane % 2) * sway - sway / 2;
        positions.set(id, {
          x: left + (lane + 0.5) * cell.width,
          y: middle - boxOf(id).below,
        });
      });
      for (const id of band) {
        const half = boxOf(id).width / 2 + SPREAD_GAP_X / 2;
        bandOf.set(id, { from: left + half, to: left + lanes * cell.width - half });
      }
      left += lanes * cell.width + SPREAD_BAND_GAP;
    });
    clearTheLines(group, edges, positions, boxOf, bandOf);
  }
  return positions;
}

// How far a line keeps from a company it passes, how finely a company's new place is searched for, and how far it may
// move. The clearance is kept a little under how far a floating company drifts (FLOAT_REACH): more would stretch a
// crowded graph tall, and a company only brushes a line's edge for a moment at the far end of a sway.
const LINE_CLEARANCE = 8;
const CLEAR_STEP = 6;
const CLEAR_REACH = 300;

/** The box a company at this point takes, with room around it. */
function boxAround(box: NodeBox, at: Point, room: number) {
  const middle = at.y + box.below;
  return {
    x1: at.x - box.width / 2 - room,
    x2: at.x + box.width / 2 + room,
    y1: middle - box.height / 2 - room,
    y2: middle + box.height / 2 + room,
  };
}

/** Whether the straight line from a to b passes through the box (clipping the line to the box, Liang–Barsky). */
export function lineCrosses(a: Point, b: Point, box: { x1: number; x2: number; y1: number; y2: number }): boolean {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  let enter = 0;
  let leave = 1;
  for (const [p, q] of [
    [-dx, a.x - box.x1],
    [dx, box.x2 - a.x],
    [-dy, a.y - box.y1],
    [dy, box.y2 - a.y],
  ] as const) {
    if (p === 0) {
      if (q < 0) return false;
      continue;
    }
    const r = q / p;
    if (p < 0) {
      if (r > leave) return false;
      enter = Math.max(enter, r);
    } else {
      if (r < enter) return false;
      leave = Math.min(leave, r);
    }
  }
  return true;
}

/**
 * Arrows are straight, so a company standing on the line between two others would sit on an arrow that is not its own
 * (as Panasonic would on Contemporary Amperex's arrow to Tesla). Each such company moves the shortest way, up, down or
 * sideways within its band, to where no arrow passes through it, its own arrows pass through no other company, and it
 * overlaps none; when it finds no such place within reach, the arrow's customer or supplier moves instead. Companies
 * keep their bands, so arrows still run left to right. The reach is kept short so the graph keeps the frame's shape:
 * where very many arrows meet one company (a dozen suppliers of one holding), one may still pass behind a company.
 */
function clearTheLines(
  group: string[],
  edges: Pick<GraphEdge, 'source' | 'target'>[],
  positions: Map<string, Point>,
  boxOf: (id: string) => NodeBox,
  bandOf: Map<string, { from: number; to: number }>,
): void {
  const inGroup = new Set(group);
  const lines = edges.filter(({ source, target }) => source !== target && inGroup.has(source) && inGroup.has(target));
  const at = (id: string) => positions.get(id)!;
  const sitsOnLine = (id: string, point: Point) =>
    lines.some(
      ({ source, target }) =>
        source !== id &&
        target !== id &&
        lineCrosses(at(source), at(target), boxAround(boxOf(id), point, LINE_CLEARANCE)),
    );
  const fits = (id: string, point: Point) => {
    if (sitsOnLine(id, point)) return false;
    const mine = boxAround(boxOf(id), point, SPREAD_GAP_Y / 2);
    for (const other of group) {
      if (other === id) continue;
      const theirs = boxAround(boxOf(other), at(other), SPREAD_GAP_Y / 2);
      if (mine.x1 < theirs.x2 && theirs.x1 < mine.x2 && mine.y1 < theirs.y2 && theirs.y1 < mine.y2) return false;
      const box = boxAround(boxOf(other), at(other), LINE_CLEARANCE);
      for (const { source, target } of lines) {
        if (source !== id && target !== id) continue;
        if (source === other || target === other) continue;
        const from = source === id ? point : at(source);
        const to = target === id ? point : at(target);
        if (lineCrosses(from, to, box)) return false;
      }
    }
    return true;
  };

  // Every move a company might make, the shortest first: up and down, and sideways within its band.
  const offsets: Point[] = [];
  for (let dx = -CLEAR_REACH; dx <= CLEAR_REACH; dx += CLEAR_STEP * 2) {
    for (let dy = -CLEAR_REACH; dy <= CLEAR_REACH; dy += CLEAR_STEP) {
      if (dx !== 0 || dy !== 0) offsets.push({ x: dx, y: dy });
    }
  }
  offsets.sort((a, b) => Math.hypot(a.x, a.y) - Math.hypot(b.x, b.y) || a.x - b.x || a.y - b.y);
  // Moves a company to the nearest place that fits; false when there is none within reach.
  const move = (id: string) => {
    const start = at(id);
    const band = bandOf.get(id) ?? { from: start.x, to: start.x };
    for (const offset of offsets) {
      const point = { x: start.x + offset.x, y: start.y + offset.y };
      if (point.x < band.from || point.x > band.to) continue;
      if (fits(id, point)) {
        positions.set(id, point);
        return true;
      }
    }
    return false;
  };
  for (let pass = 0; pass < 6; pass++) {
    let moved = false;
    for (const { source, target } of lines) {
      for (const id of group) {
        if (id === source || id === target) continue;
        if (!lineCrosses(at(source), at(target), boxAround(boxOf(id), at(id), LINE_CLEARANCE))) continue;
        // The company in the way moves; failing that, the arrow's customer or its supplier does.
        if (move(id) || move(target) || move(source)) moved = true;
      }
    }
    if (!moved) break;
  }
}
