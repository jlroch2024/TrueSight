// What the workspace shows, worked out once from what it loaded (holdings, relationships, alerts) and shared by the
// graph, the drawer and the Inspector, so each draws the same companies in the same colours with the same marks.
import { useMemo } from 'react';
import type { AlertEvent } from '../../api/alerts';
import type { Severity } from '../../api/news';
import type { Holding } from '../../api/portfolios';
import type { Relationship } from '../../api/relationships';
import { type Risk, risksFrom } from '../../graph/model';
import {
  type GraphEdge,
  type GraphNode,
  type SharedCompany,
  alertSeverities,
  buildGraphElements,
  chainColours,
  holdingNodeIds,
  openAlerts,
  sharedCompanies,
  withSharedSummaries,
} from '../../pages/supplyChain';

/** What is chosen, and shown in the Inspector: a company (by graph node id), a relationship, or an alert. */
export type Chosen =
  | { kind: 'company'; nodeId: string }
  | { kind: 'relationship'; relationshipId: number }
  | { kind: 'alert'; key: string }
  | null;

/** An alert's own key: the ids of the alerts grouped into its event. */
export const alertKey = (alert: Pick<AlertEvent, 'alertIds'>) => alert.alertIds.join('-');

/**
 * A holding not on the graph (not analysed yet, no report found, or none of its suppliers named) is still chosen the
 * same way, by an id of its own that no graph node has.
 */
export const offGraphId = (holdingId: number) => `holding-${holdingId}`;

export type WorkspaceView = {
  holdings: Holding[];
  relationships: Relationship[];
  alerts: AlertEvent[];
  nodes: GraphNode[];
  edges: GraphEdge[];
  shared: SharedCompany[];
  // Holding id to its node id, on the graph or off it (offGraphId).
  nodeIdOf: (holding: Holding) => string;
  // Holding id to graph node id, for holdings on the graph only.
  nodeIdByHolding: Map<number, string>;
  // Node id (on or off the graph) to its holding.
  holdingOfNode: Map<string, Holding>;
  // A company's stored name to its node id.
  nodeIdOfName: Map<string, string>;
  // Each coloured holding's chain colour, by node id.
  colourOf: Map<string, string>;
  // The most severe open alert of each company, by node id.
  alertDots: Map<string, Severity>;
  // The disruptions running through the graph's links, by relationship id.
  risks: Map<number, Risk>;
  open: AlertEvent[];
  // Whether the holdings have weights: an alert's exposure is then a share of the portfolio, otherwise a count of
  // holdings.
  weighted: boolean;
};

export function useWorkspaceView(
  holdings: Holding[],
  relationships: Relationship[],
  alerts: AlertEvent[],
  weighted: boolean,
): WorkspaceView {
  return useMemo(() => {
    const { nodes: bare, edges } = buildGraphElements(relationships, holdings);
    const shared = sharedCompanies(relationships, bare);
    const nodes = withSharedSummaries(bare, shared);
    const nodeIdByHolding = holdingNodeIds(holdings, nodes);
    const nodeIdOf = (holding: Holding) => nodeIdByHolding.get(holding.id) ?? offGraphId(holding.id);
    const holdingOfNode = new Map(holdings.map((holding) => [nodeIdOf(holding), holding]));
    const open = openAlerts(alerts);
    return {
      holdings,
      relationships,
      alerts,
      nodes,
      edges,
      shared,
      nodeIdOf,
      nodeIdByHolding,
      holdingOfNode,
      nodeIdOfName: new Map(nodes.map((node) => [node.name, node.id])),
      colourOf: new Map(chainColours(nodes, edges).key.map((entry) => [entry.id, entry.colour])),
      alertDots: alertSeverities(open, nodes, holdings, nodeIdByHolding),
      risks: risksFrom(open),
      open,
      weighted,
    };
  }, [holdings, relationships, alerts, weighted]);
}

/**
 * How much of the portfolio an alert reaches, as the figure to show: a share ("30%") when the holdings have weights, or
 * a count of holdings ("3", "of 10 Holdings") when they do not.
 */
export function exposureOf(alert: Pick<AlertEvent, 'exposure'>, view: Pick<WorkspaceView, 'weighted' | 'holdings'>) {
  const value = Number(alert.exposure.toFixed(1));
  return view.weighted
    ? { value, suffix: '%', label: 'of Portfolio' }
    : { value, suffix: '', label: `of ${view.holdings.length} ${view.holdings.length === 1 ? 'Holding' : 'Holdings'}` };
}

const SEVERITY_RANK: Record<Severity, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };

/** Alerts in the order to read them: the most severe first, then the most exposed, then the newest. */
export function byUrgency(a: AlertEvent, b: AlertEvent): number {
  return (
    SEVERITY_RANK[a.headline.assessment.severity] - SEVERITY_RANK[b.headline.assessment.severity] ||
    b.exposure - a.exposure ||
    b.headline.article.publishedAt.localeCompare(a.headline.article.publishedAt)
  );
}

/** The companies and links a chosen alert runs through, for the graph to light: its company, the holdings it reaches,
 *  and both links of a holding reached in two steps. */
export function spotlightOf(alert: AlertEvent, view: Pick<WorkspaceView, 'nodeIdByHolding' | 'nodeIdOfName'>) {
  const nodeIds = alert.holdings
    .map((reached) => view.nodeIdByHolding.get(reached.holdingId))
    .filter((id): id is string => !!id);
  const about = alert.headline.article.companyName ? view.nodeIdOfName.get(alert.headline.article.companyName) : undefined;
  if (about) nodeIds.push(about);
  const relationshipIds = alert.holdings.flatMap((reached) =>
    [reached.relationshipId, reached.secondRelationshipId].filter((id): id is number => id != null),
  );
  return { nodeIds: [...new Set(nodeIds)], relationshipIds: [...new Set(relationshipIds)] };
}
