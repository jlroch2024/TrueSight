// Scrub the Graph Through the Years: the graph of every year at once, and which of it each year shows. The layout is
// worked out over all of it (every company and arrow of every year, and today's), so changing the year only shows and
// hides: each company keeps its place. Pure functions, so they can be tested without a browser.
import type { History, HistoryEntry } from '../../api/history';
import type { RelationshipType } from '../../api/relationships';
import type { GraphEdge, GraphNode } from '../../pages/supplyChain';
import type { WorkspaceView } from './view';

/** A year of the scrubber: a year of the history, or Today (null). */
export type Year = number | null;

/** The companies and arrows a year shows, and which of them are new that year. */
export type YearShown = {
  shown: { nodeIds: string[]; edgeIds: string[] };
  fresh: { nodeIds: string[]; edgeIds: string[]; tag: string } | null;
};

export type Timeline = {
  // Today's graph and every older company and arrow too, for the graph to lay out once.
  nodes: GraphNode[];
  edges: GraphEdge[];
  // The years of the history, oldest first; the scrubber adds Today.
  years: number[];
  // What a year shows. Today shows today's graph and nothing older.
  at: (year: Year) => YearShown;
  // The relationship a link was in a year, for the Inspector: null if the year did not name it.
  entryOf: (year: Year, relationshipId: number) => HistoryEntry | null;
};

/** A relationship's key, as the backend makes it (company, counterparty, type): the same in every year. */
export const keyOf = (companyId: number, counterpartyId: number, type: RelationshipType) =>
  `${companyId}-${counterpartyId}-${type}`;

/**
 * The graph of every year at once. A company older years name but today's graph does not have is added from the
 * history's companies, and an arrow likewise (given a negative relationship id of its own, so it is never mistaken for
 * one of today's). With no history there is nothing to add: the graph is today's, and every year shows it whole.
 */
export function buildTimeline(view: WorkspaceView, history: History | null): Timeline {
  const years = history?.years ?? [];
  if (!history || years.length === 0) {
    const all = { nodeIds: view.nodes.map((node) => node.id), edgeIds: view.edges.map((edge) => edge.id) };
    return { nodes: view.nodes, edges: view.edges, years: [], at: () => ({ shown: all, fresh: null }), entryOf: () => null };
  }

  const nodes = [...view.nodes];
  const edges = [...view.edges];
  const namesOf = new Map(history.companies.map((company) => [company.id, company]));
  const nodeIdByCompany = new Map<number, string>();
  const nodeOf = (companyId: number): string | null => {
    const known = nodeIdByCompany.get(companyId);
    if (known) return known;
    const company = namesOf.get(companyId);
    // Today's graph has it, by id; or a holding without links has a node of its own, found by name.
    const id = nodes.some((node) => node.id === String(companyId))
      ? String(companyId)
      : (company && view.nodeIdOfName.get(company.name)) || null;
    if (id) {
      nodeIdByCompany.set(companyId, id);
      return id;
    }
    if (!company) return null;
    nodes.push({
      id: String(companyId),
      name: company.name,
      displayName: company.displayName,
      ticker: company.ticker,
      isHolding: false,
      weight: null,
    });
    nodeIdByCompany.set(companyId, String(companyId));
    return String(companyId);
  };

  // Today's arrows by key, and an arrow of its own for each key only older years name.
  const edgeOfKey = new Map<string, GraphEdge>();
  for (const relationship of view.relationships) {
    const edge = view.edges.find((item) => item.relationshipId === relationship.id);
    const key = keyOf(relationship.company.id, relationship.counterparty.id, relationship.type);
    if (edge && !edgeOfKey.has(key)) edgeOfKey.set(key, edge);
  }
  const keyOfEdge = new Map<number, string>([...edgeOfKey].map(([key, edge]) => [edge.relationshipId, key]));
  let older = 0;
  for (const year of years) {
    for (const entry of year.relationships) {
      if (edgeOfKey.has(entry.key)) continue;
      const company = nodeOf(entry.companyId);
      const counterparty = nodeOf(entry.counterpartyId);
      if (!company || !counterparty) continue;
      // SUPPLIER: the counterparty supplies the company, so the arrow runs counterparty to company.
      const [source, target] = entry.type === 'SUPPLIER' ? [counterparty, company] : [company, counterparty];
      const supplier = entry.type === 'SUPPLIER' ? entry.counterpartyId : entry.companyId;
      const customer = entry.type === 'SUPPLIER' ? entry.companyId : entry.counterpartyId;
      older += 1;
      const edge: GraphEdge = {
        id: `past-${entry.key}`,
        source,
        target,
        relationshipId: -older,
        reviewStatus: 'UNREVIEWED',
        criticality: null,
        summary: `${namesOf.get(supplier)?.displayName ?? 'A company'} supplies ${namesOf.get(customer)?.displayName ?? 'a company'}`,
      };
      edgeOfKey.set(entry.key, edge);
      keyOfEdge.set(edge.relationshipId, entry.key);
      edges.push(edge);
    }
  }

  const holdings = nodes.filter((node) => node.isHolding).map((node) => node.id);
  const unique = (ids: (string | undefined)[]) => [...new Set(ids.filter((id): id is string => !!id))];
  const shownIn = (year: number) => {
    const entries = years.find((item) => item.year === year)?.relationships ?? [];
    const edgeIds = unique(entries.map((entry) => edgeOfKey.get(entry.key)?.id));
    const wanted = new Set(edgeIds);
    const ends = edges.filter((edge) => wanted.has(edge.id)).flatMap((edge) => [edge.source, edge.target]);
    return { edgeIds, nodeIds: unique([...holdings, ...ends]), entries };
  };

  const at = (year: Year): YearShown => {
    if (year === null) {
      return { shown: { nodeIds: view.nodes.map((node) => node.id), edgeIds: view.edges.map((edge) => edge.id) }, fresh: null };
    }
    const { edgeIds, nodeIds, entries } = shownIn(year);
    const position = years.findIndex((item) => item.year === year);
    // The first year has nothing before it to be new against.
    if (position <= 0) return { shown: { nodeIds, edgeIds }, fresh: null };
    const earlier = new Set(shownIn(years[position - 1]!.year).nodeIds);
    return {
      shown: { nodeIds, edgeIds },
      fresh: {
        nodeIds: nodeIds.filter((id) => !earlier.has(id)),
        edgeIds: unique(entries.filter((entry) => entry.change === 'NEW').map((entry) => edgeOfKey.get(entry.key)?.id)),
        tag: `New in ${year}`,
      },
    };
  };

  const entryOf = (year: Year, relationshipId: number): HistoryEntry | null => {
    if (year === null) return null;
    const key = keyOfEdge.get(relationshipId);
    return years.find((item) => item.year === year)?.relationships.find((entry) => entry.key === key) ?? null;
  };

  return { nodes, edges, years: years.map((item) => item.year), at, entryOf };
}
