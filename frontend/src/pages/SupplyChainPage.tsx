// The Supply Chain page: a workspace with the portfolio's holdings on the left, its suppliers and customers as a graph
// in the middle, and the details of whatever is chosen on the right (the Inspector).
//
// One thing is chosen at a time: a company (from the graph, the holdings or the Inspector) or a relationship (an arrow,
// or a supplier or customer in the Inspector). The graph keeps a chosen company in focus and centred, and draws a chosen
// relationship's arrow selected. Clicking the graph's background lets go.
//
// The left panel has two tabs: Holdings, and News (Check News and the headlines it collected). "?panel=news" in the
// address opens the News tab, as the Portfolio page's See News does.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { type NewsArticle, newsApi } from '../api/news';
import { type Holding, type Portfolio, holdingApi, portfolioApi } from '../api/portfolios';
import { type Relationship, relationshipApi } from '../api/relationships';
import { settingsApi } from '../api/settings';
import { HoldingsPanel } from '../components/HoldingsPanel';
import { Inspector } from '../components/Inspector';
import { NewsPanel } from '../components/NewsPanel';
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
  const [searchParams] = useSearchParams();
  const [panel, setPanel] = useState<'holdings' | 'news'>(searchParams.get('panel') === 'news' ? 'news' : 'holdings');
  // The portfolio, for Check News and its status; null until it arrives.
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  // undefined while loading.
  const [news, setNews] = useState<NewsArticle[] | undefined>(undefined);
  const [newsError, setNewsError] = useState<string | null>(null);
  // The company the News tab is narrowed to, by name.
  const [newsCompany, setNewsCompany] = useState<string | null>(null);

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
    setPortfolio(null);
    portfolioApi
      .list()
      .then((all) => {
        if (active) setPortfolio(all.find((p) => String(p.id) === portfolioId) ?? null);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [portfolioId]);

  // The news is loaded at first, and again each time a news check finishes.
  const newsCheckedAt = portfolio?.newsCheckedAt ?? null;
  const newsChecking = portfolio?.newsStatus === 'CHECKING';
  useEffect(() => {
    if (newsChecking) return;
    let active = true;
    setNewsError(null);
    newsApi
      .list(portfolioId)
      .then((articles) => {
        if (active) setNews(articles);
      })
      .catch((e: unknown) => {
        if (active) setNewsError(messageOf(e));
      });
    return () => {
      active = false;
    };
  }, [portfolioId, newsCheckedAt, newsChecking]);

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
  // A company named in the news: chosen on the graph, found by its name as the graph's companies are.
  const selectCompanyByName = (name: string) => {
    const node = nodes.find((n) => n.name === name);
    if (node) setSelection({ kind: 'company', nodeId: node.id });
  };
  const showAllNews = (name: string) => {
    setNewsCompany(name);
    setPanel('news');
  };

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

      <div className="workspace">
        <aside className="workspace-panel workspace-left" aria-label="Holdings and News">
          <div className="panel-tabs" role="tablist" aria-label="Left panel">
            <button
              type="button"
              role="tab"
              id="tab-holdings"
              aria-selected={panel === 'holdings'}
              aria-controls="panel-holdings"
              onClick={() => setPanel('holdings')}
            >
              Holdings
            </button>
            <button
              type="button"
              role="tab"
              id="tab-news"
              aria-selected={panel === 'news'}
              aria-controls="panel-news"
              onClick={() => setPanel('news')}
            >
              News{news && news.length > 0 ? ` (${news.length})` : ''}
            </button>
          </div>
          {panel === 'holdings' ? (
            <div role="tabpanel" id="panel-holdings" aria-labelledby="tab-holdings">
              <HoldingsPanel
                holdings={holdings}
                nodeIdByHolding={nodeIdByHolding}
                relationships={relationships}
                shared={shared}
                selectedNodeId={selection?.kind === 'company' ? selection.nodeId : null}
                onSelect={selectNode}
              />
            </div>
          ) : (
            <div role="tabpanel" id="panel-news" aria-labelledby="tab-news">
              <NewsPanel
                portfolio={portfolio}
                onPortfolioChange={setPortfolio}
                articles={news}
                error={newsError}
                companyFilter={newsCompany}
                onClearFilter={() => setNewsCompany(null)}
                onSelectCompany={selectCompanyByName}
              />
            </div>
          )}
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
          <p className="graph-hint">Drag to Move · Scroll to Zoom · Click an Arrow for Its Evidence</p>
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
            news={news}
            onShowAllNews={showAllNews}
          />
        </aside>
      </div>
    </section>
  );
}
