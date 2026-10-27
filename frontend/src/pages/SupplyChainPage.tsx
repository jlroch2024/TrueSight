// The Supply Chain page: a workspace with the portfolio's holdings on the left, its suppliers and customers as a graph
// in the middle, and the details of whatever is chosen on the right (the Inspector). Both sides are drawers: the left
// one folds to a narrow strip, and the Inspector opens only while something is chosen, so the graph gets the room.
//
// One thing is chosen at a time: a company (from the graph, the holdings or the Inspector) or a relationship (an arrow,
// or a supplier or customer in the Inspector). The graph keeps a chosen company in focus and centred, and draws a chosen
// relationship's arrow selected. Clicking the graph's background lets go.
//
// The left panel lists the holdings. News reaches the user in one place, the Alerts page; here the portfolio's open
// alerts are shown where they matter: the Inspector lists a company's open alerts, and the graph marks each company
// that has some with a dot in its most severe alert's colour.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { type AlertEvent, alertApi } from '../api/alerts';
import { ApiError } from '../api/client';
import { type Holding, holdingApi } from '../api/portfolios';
import { type Relationship, relationshipApi } from '../api/relationships';
import { logoApi } from '../api/logos';
import { settingsApi } from '../api/settings';
import { HoldingsPanel } from '../components/HoldingsPanel';
import { Inspector } from '../components/Inspector';
import { LivingGraph } from '../graph/LivingGraph';
import { risksFrom } from '../graph/model';
import { paths } from '../paths';
import {
  type Selection,
  alertSeverities,
  buildGraphElements,
  holdingNodeIds,
  openAlerts,
  sharedCompanies,
  withSharedSummaries,
} from './supplyChain';

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
  // The left drawer can fold to a narrow strip, to give the graph more room. The Inspector, on the right, is open only
  // while something is chosen.
  const [leftOpen, setLeftOpen] = useState(true);
  // Empty until the settings arrive, or when no Logo.dev token is set: the graph then shows letters.
  const [logoToken, setLogoToken] = useState('');
  // The website domains of companies without a ticker, so their logos can be found too.
  const [logoDomains, setLogoDomains] = useState<Record<string, string>>({});
  // The portfolio's open alerts, loaded once; undefined until they arrive, or when they could not be loaded.
  const [alerts, setAlerts] = useState<AlertEvent[] | undefined>(undefined);

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

  useEffect(() => {
    let active = true;
    setAlerts(undefined);
    alertApi
      .list(portfolioId)
      .then((found) => {
        if (active) setAlerts(openAlerts(found.alerts ?? []));
      })
      // The graph is still useful without them; the Inspector then shows no alerts section at all.
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [portfolioId]);
  const risks = useMemo(() => risksFrom(alerts ?? []), [alerts]);

  const { nodes, edges } = useMemo(
    () => buildGraphElements(relationships ?? [], holdings),
    [relationships, holdings],
  );
  // The companies several holdings depend on: drawn amber on the graph, and listed in the left panel.
  const shared = useMemo(() => sharedCompanies(relationships ?? [], nodes), [relationships, nodes]);
  const graphNodes = useMemo(() => withSharedSummaries(nodes, shared), [nodes, shared]);
  const nodeIdByHolding = useMemo(() => holdingNodeIds(holdings, nodes), [holdings, nodes]);
  // The most severe open alert of each company, for the dots on the graph.
  const alertDots = useMemo(
    () => alertSeverities(alerts ?? [], graphNodes, holdings, nodeIdByHolding),
    [alerts, graphNodes, holdings, nodeIdByHolding],
  );

  // Only with a Logo.dev token, and only for companies without a ticker. A failure just leaves their letters.
  useEffect(() => {
    const names = nodes.filter((node) => !node.ticker).map((node) => node.name);
    if (!logoToken || names.length === 0) return;
    let active = true;
    logoApi
      .domains(names)
      .then((found) => {
        if (active) setLogoDomains(found);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [nodes, logoToken]);

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
              Upload holdings on the <button type="button" className="inline-action" onClick={() => navigate(paths.portfolio(portfolioId))}>Portfolio Page</button>, then run Analyse
              to find suppliers and customers in their annual reports.
            </>
          ) : (
            <>
              Run Analyse on the <button type="button" className="inline-action" onClick={() => navigate(paths.portfolio(portfolioId))}>Portfolio Page</button> to find suppliers and
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

      <div className={`workspace${leftOpen ? '' : ' left-closed'}${selection ? '' : ' inspector-closed'}`}>
        <aside className="workspace-panel workspace-left" aria-label="Holdings">
          {!leftOpen ? (
            <button type="button" className="drawer-strip" onClick={() => setLeftOpen(true)}>
              Show Holdings
            </button>
          ) : (
            <>
              <div className="panel-head">
                <h2>Holdings</h2>
                <button
                  type="button"
                  className="drawer-close"
                  aria-label="Hide Holdings"
                  title="Hide Holdings"
                  onClick={() => setLeftOpen(false)}
                >
                  {'\u2039'}
                </button>
              </div>
              <HoldingsPanel
                holdings={holdings}
                nodeIdByHolding={nodeIdByHolding}
                relationships={relationships}
                shared={shared}
                selectedNodeId={selection?.kind === 'company' ? selection.nodeId : null}
                onSelect={selectNode}
              />
            </>
          )}
        </aside>

        <div className="workspace-graph">
          <LivingGraph
            nodes={graphNodes}
            edges={edges}
            onEdgeSelect={selectRelationship}
            onNodeSelect={selectNode}
            selectedNodeId={selection?.kind === 'company' ? selection.nodeId : null}
            selectedRelationshipId={selection?.kind === 'relationship' ? selection.relationshipId : null}
            logoToken={logoToken}
            logoDomains={logoDomains}
            layoutKey={`truesight.graph.${portfolioId}`}
            risks={risks}
            alertDots={alertDots}
          />
        </div>

        {selection && (
          <aside className="workspace-panel workspace-inspector" aria-label="Inspector">
            <button
              type="button"
              className="drawer-close inspector-close"
              aria-label="Close Inspector"
              title="Close Inspector"
              onClick={() => setSelection(null)}
            >
              {'\u00d7'}
            </button>
            <Inspector
              selection={selection}
              nodes={graphNodes}
              relationships={relationships}
              holdings={holdings}
              nodeIdByHolding={nodeIdByHolding}
              onSelectNode={selectNode}
              onSelectRelationship={selectRelationship}
              alerts={alerts}
            />
          </aside>
        )}
      </div>
    </section>
  );
}
