// Turns a portfolio's holdings and relationships into graph elements. Kept apart from SupplyChainGraph.tsx (which
// draws it with Cytoscape) so this logic can be tested on its own, in milliseconds.
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';

// weight is the holding's share of the portfolio, in percent, as uploaded; null for other companies, or when the CSV
// had no weight.
// sharedSummary is set (e.g. "Supplies 4 holdings") only on a company that several holdings depend on; see
// sharedCompanies.
export type GraphNode = {
  id: string;
  name: string;
  ticker: string | null;
  isHolding: boolean;
  weight: number | null;
  sharedSummary?: string;
};
export type GraphEdge = { id: string; source: string; target: string; relationshipId: number };

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
    return { id: `relationship-${relationship.id}`, source, target, relationshipId: relationship.id };
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

/** A name short enough for the graph: its plain name, cut with an ellipsis if it is still too long. */
export function shortName(name: string): string {
  const plain = plainName(name);
  if (plain.length <= SHORT_NAME_LENGTH) return plain;
  return `${plain.slice(0, SHORT_NAME_LENGTH - 1).trimEnd()}\u2026`;
}

/** A holding's weight as a percentage: 18 becomes "18%", 12.5 "12.5%", 3.333 "3.33%". Null when there is none. */
export function formatWeight(weight: number | null): string | null {
  if (weight === null || !Number.isFinite(weight)) return null;
  return `${Number(weight.toFixed(2))}%`;
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
 * Where a company's logo is on Logo.dev: by ticker when it has one, otherwise by its plain name ("NVIDIA", not
 * "NVIDIA Corporation", which Logo.dev matches less well). Null without a Logo.dev token: the graph then shows letters.
 */
export function logoUrl(node: Pick<GraphNode, 'name' | 'ticker'>, token: string): string | null {
  if (!token) return null;
  const path = node.ticker
    ? `ticker/${encodeURIComponent(node.ticker)}`
    : `name/${encodeURIComponent(plainName(node.name))}`;
  return `${LOGO_HOST}/${path}?token=${encodeURIComponent(token)}&${LOGO_OPTIONS}`;
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

// The arrows are dashes that travel from supplier to customer: 8 px of line, then a 5 px gap, repeating.
export const DASH_PATTERN = [8, 5];
const DASH_PERIOD = 13; // 8 + 5
const DASH_SPEED = 20; // pixels per second

/**
 * Where the dash pattern sits after {@code elapsedMs} of animation, as a value for Cytoscape's {@code line-dash-offset}.
 *
 * An edge is drawn from its source (the supplier) to its target (the customer). A larger offset slides the pattern
 * back towards the source, so to make the dashes travel towards the customer the offset has to get smaller as time
 * passes. It wraps around once a whole dash and gap has gone by, so it never grows without end.
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
 * The label for a shared company, e.g. "Supplies 4 holdings" or "Buys from 2 holdings". A side held by only one
 * holding is left out, so it never reads "Supplies 1 holdings"; a company shared on both sides gets both.
 */
export function sharedSummary(company: SharedCompany): string {
  const parts: string[] = [];
  if (company.suppliesHoldings >= SHARED_FROM) parts.push(`Supplies ${company.suppliesHoldings} holdings`);
  if (company.buysFromHoldings >= SHARED_FROM) parts.push(`Buys from ${company.buysFromHoldings} holdings`);
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
