// Turns a portfolio's holdings and relationships into graph elements. Kept apart from SupplyChainGraph.tsx (which
// draws it with Cytoscape) so this logic can be tested on its own, in milliseconds.
import type { Holding } from '../api/portfolios';
import type { Criticality, Relationship, ReviewStatus } from '../api/relationships';

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
  criticality: Criticality | null;
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
      criticality: relationship.criticality,
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
 * Where a company's logo is on Logo.dev: by ticker when it has one; otherwise by its website domain when one was found
 * (see GET /api/logos/domains), which Logo.dev knows far better than names; otherwise by its plain name ("NVIDIA", not
 * "NVIDIA Corporation", which Logo.dev matches less well). Null without a Logo.dev token: the graph then shows letters.
 */
export function logoUrl(node: Pick<GraphNode, 'name' | 'ticker'>, token: string, domain?: string): string | null {
  if (!token) return null;
  const path = node.ticker
    ? `ticker/${encodeURIComponent(node.ticker)}`
    : domain
      ? encodeURIComponent(domain)
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

// A flow marker takes this long to travel from supplier to customer, independent of the review line style.
export const FLOW_DURATION_MS = 2200;

/** Progress of the flow marker from supplier (0) to customer (1), wrapping once per cycle. */
export function flowProgressAt(elapsedMs: number): number {
  return (((elapsedMs % FLOW_DURATION_MS) + FLOW_DURATION_MS) % FLOW_DURATION_MS) / FLOW_DURATION_MS;
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
