// Turns a portfolio's holdings and relationships into a graph, and builds the "Show in Report" link. Kept apart from
// SupplyChainGraph.tsx (which draws it with Cytoscape) so this logic can be tested on its own, in milliseconds.
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';

export type GraphNode = { id: string; name: string; ticker: string | null; isHolding: boolean };
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
    nodes.set(id, { id, name, ticker, isHolding });
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
      nodes.get(existingId)!.isHolding = true;
      continue;
    }
    const id = `holding-${holding.id}`;
    nodes.set(id, { id, name: holding.companyName, ticker: holding.ticker, isHolding: true });
    nodeIdByName.set(holding.companyName, id);
  }

  return { nodes: [...nodes.values()], edges };
}

/**
 * The link that opens the report on the SEC website, scrolled to and highlighting the evidence, with a
 * {@link https://wicg.github.io/scroll-to-text-fragment/ Text Fragment}. Chrome and Edge support it; other browsers
 * just open the report at the top.
 *
 * Evidence of 10 words or fewer is used whole. Longer evidence uses only its first 5 and last 5 words, so the
 * fragment still matches even if the SEC's own formatting puts something unexpected in between.
 */
export function showInReportUrl(reportUrl: string, evidence: string): string {
  const words = evidence.trim().split(/\s+/);
  const fragment =
    words.length <= 10
      ? encodeForFragment(evidence)
      : `${encodeForFragment(words.slice(0, 5).join(' '))},${encodeForFragment(words.slice(-5).join(' '))}`;
  return `${reportUrl}#:~:text=${fragment}`;
}

// encodeURIComponent leaves "-" as it is, but a Text Fragment uses "-" to separate a prefix or suffix from the quoted
// text, so a hyphen inside the quote itself must be escaped too, or Chrome and Edge misread where the quote ends.
function encodeForFragment(text: string): string {
  return encodeURIComponent(text).replaceAll('-', '%2D');
}
