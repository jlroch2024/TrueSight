// The Supply Chain page: the open portfolio's holdings and their suppliers and customers, as a graph. Clicking an
// edge shows the evidence it stands for.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { type Holding, holdingApi } from '../api/portfolios';
import { type Relationship, relationshipApi } from '../api/relationships';
import { SupplyChainGraph } from '../components/SupplyChainGraph';
import { paths } from '../paths';
import { buildGraphElements, showInReportUrl } from './supplyChain';

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

export function SupplyChainPage() {
  const { portfolioId = '' } = useParams();
  // undefined while loading.
  const [relationships, setRelationships] = useState<Relationship[] | undefined>(undefined);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    setRelationships(undefined);
    setHoldings([]);
    setError(null);
    setSelectedId(null);
    Promise.all([relationshipApi.list(portfolioId), holdingApi.list(portfolioId)])
      .then(([foundRelationships, foundHoldings]) => {
        if (!active) return;
        setRelationships(foundRelationships);
        setHoldings(foundHoldings);
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
  const handleEdgeSelect = useCallback((relationshipId: number) => setSelectedId(relationshipId), []);
  const selected = relationships?.find((r) => r.id === selectedId) ?? null;

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
              Upload holdings on the <Link to={paths.portfolio(portfolioId)}>Portfolio page</Link>, then run Analyse
              to find suppliers and customers in their annual reports.
            </>
          ) : (
            <>
              Run Analyse on the <Link to={paths.portfolio(portfolioId)}>Portfolio page</Link> to find suppliers and
              customers in this portfolio's holdings' annual reports.
            </>
          )}
        </p>
      </section>
    );
  }

  return (
    <section>
      <h1>Supply Chain</h1>
      <p className="muted">
        Drag a company to rearrange the graph, scroll to zoom, and click an edge to see the evidence behind it.
      </p>

      <SupplyChainGraph nodes={nodes} edges={edges} onEdgeSelect={handleEdgeSelect} />

      {selected && (
        <div className="card evidence-panel">
          <h2>
            {selected.company.name} {selected.type === 'SUPPLIER' ? '\u2190' : '\u2192'} {selected.counterparty.name}
          </h2>
          <p className="muted small">
            {selected.type === 'SUPPLIER'
              ? `${selected.counterparty.name} supplies ${selected.company.name}`
              : `${selected.counterparty.name} buys from ${selected.company.name}`}
            {selected.provides ? `: ${selected.provides}.` : '.'}
          </p>
          <blockquote>{selected.evidence}</blockquote>
          <p className="muted small">
            {selected.report.form}, filed {selected.report.filingDate}
          </p>
          <a href={showInReportUrl(selected.report.url, selected.evidence)} target="_blank" rel="noreferrer">
            Show in Report
          </a>
        </div>
      )}
    </section>
  );
}
