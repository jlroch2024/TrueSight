// The Supply Chain page: a workspace with the portfolio's holdings on the left, its suppliers and customers as a graph
// in the middle, and the details of whatever is chosen on the right (the Inspector).
//
// One thing is chosen at a time: a company (from the graph, the holdings or the Inspector) or a relationship (an arrow,
// or a supplier or customer in the Inspector). The graph keeps a chosen company in focus and centred, and draws a chosen
// relationship's arrow selected. Clicking the graph's background lets go.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { type Holding, holdingApi } from '../api/portfolios';
import { type Relationship, relationshipApi } from '../api/relationships';
import { settingsApi } from '../api/settings';
import { HoldingsPanel } from '../components/HoldingsPanel';
import { Inspector } from '../components/Inspector';
import { SupplyChainGraph } from '../components/SupplyChainGraph';
import { paths } from '../paths';
import { type Selection, buildGraphElements, holdingNodeIds, sharedCompanies, withSharedSummaries } from './supplyChain';

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

export function SupplyChainPage() {
  const { portfolioId = '' } = useParams();
  const navigate = useNavigate();
  // undefined while loading.
  const [relationships, setRelationships] = useState<Relationship[] | undefined>(undefined);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [selection, setSelection] = useState<Selection>(null);
  // Empty until the settings arrive, or when no Logo.dev token is set: the graph then shows letters.
  const [logoToken, setLogoToken] = useState('');

  useEffect(() => {
    let active = true;
    setRelationships(undefined);
    setHoldings([]);
    setError(null);
    setSelection(null);
    Promise.all([
      relationshipApi.list(portfolioId),
      holdingApi.list(portfolioId),
      // The logos are a nicety: if the settings cannot be fetched, the graph still shows, with letters.
      settingsApi.get().catch(() => ({ logoToken: '' })),
    ])
      .then(([foundRelationships, foundHoldings, settings]) => {
        if (!active) return;
        setRelationships(foundRelationships);
        setHoldings(foundHoldings);
        setLogoToken(settings.logoToken ?? '');
      })
      .catch((e: unknown) => {
        if (active) setError(messageOf(e));
      });
    return () => {
      active = false;
    };
  }, [portfolioId]);

  const { nodes, edges } = useMemo(
    () => buildGraphElements(relationships ?? [], holdings),
    [relationships, holdings],
  );
  // The companies several holdings depend on: drawn amber on the graph, and listed in the left panel.
  const shared = useMemo(() => sharedCompanies(relationships ?? [], nodes), [relationships, nodes]);
  const graphNodes = useMemo(() => withSharedSummaries(nodes, shared), [nodes, shared]);
  const nodeIdByHolding = useMemo(() => holdingNodeIds(holdings, nodes), [holdings, nodes]);

  const selectNode = useCallback(
    (nodeId: string | null) => setSelection(nodeId ? { kind: 'company', nodeId } : null),
    [],
  );
  const selectRelationship = useCallback(
    (relationshipId: number) => setSelection({ kind: 'relationship', relationshipId }),
    [],
  );

  if (error) return <p className="error">{error}</p>;
  if (relationships === undefined) return <p className="muted">Loading…</p>;

  if (relationships.length === 0) {
    return (
      <section>
        <h1>Supply Chain</h1>
        <p className="muted">
          This portfolio has no relationships yet.{' '}
          {holdings.length === 0 ? (
            <>
              Upload holdings on the <button type="button" className="inline-action" onClick={() => navigate(paths.portfolio(portfolioId))}>Portfolio page</button>, then run Analyse
              to find suppliers and customers in their annual reports.
            </>
          ) : (
            <>
              Run Analyse on the <button type="button" className="inline-action" onClick={() => navigate(paths.portfolio(portfolioId))}>Portfolio page</button> to find suppliers and
              customers in this portfolio's holdings' annual reports.
            </>
          )}
        </p>
      </section>
    );
  }

  return (
    <section className="workspace-page">
      <h1>Supply Chain</h1>
      <p className="muted">Who supplies your holdings and who buys from them, from their latest annual reports.</p>

      <div className="workspace">
        <aside className="workspace-panel workspace-left" aria-label="Holdings">
          <HoldingsPanel
            holdings={holdings}
            nodeIdByHolding={nodeIdByHolding}
            relationships={relationships}
            shared={shared}
            selectedNodeId={selection?.kind === 'company' ? selection.nodeId : null}
            onSelect={selectNode}
          />
        </aside>

        <div className="workspace-graph">
          <SupplyChainGraph
            nodes={graphNodes}
            edges={edges}
            onEdgeSelect={selectRelationship}
            onNodeSelect={selectNode}
            selectedNodeId={selection?.kind === 'company' ? selection.nodeId : null}
            selectedRelationshipId={selection?.kind === 'relationship' ? selection.relationshipId : null}
            logoToken={logoToken}
          />
          <p className="graph-hint">Drag to move · Scroll to zoom · Click an arrow for its evidence</p>
        </div>

        <aside className="workspace-panel workspace-inspector" aria-label="Inspector">
          <Inspector
            selection={selection}
            nodes={graphNodes}
            relationships={relationships}
            holdings={holdings}
            nodeIdByHolding={nodeIdByHolding}
            onSelectNode={selectNode}
            onSelectRelationship={selectRelationship}
          />
        </aside>
      </div>
    </section>
  );
}
