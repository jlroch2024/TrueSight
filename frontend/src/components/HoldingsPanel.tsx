// The Supply Chain page's left panel: every holding, largest first, and the companies several holdings depend on.
// Clicking one chooses it, which focuses and centres it on the graph and shows it in the Inspector. A holding that is
// not on the graph yet (not analysed, or no report found) is listed greyed, with its status, and cannot be chosen.
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import { STATUS_LABELS } from '../format';
import { type SharedCompany, connectionsOf, formatWeight, sharedSummary } from '../pages/supplyChain';

type Props = {
  holdings: Holding[];
  // Holding id to its node on the graph; a holding missing here is not on the graph.
  nodeIdByHolding: Map<number, string>;
  relationships: Relationship[];
  shared: SharedCompany[];
  selectedNodeId: string | null;
  onSelect: (nodeId: string) => void;
};

/** Largest weight first; holdings without a weight after those with one; then by ticker. */
export function byWeight(a: Holding, b: Holding): number {
  return (b.weight ?? -1) - (a.weight ?? -1) || a.ticker.localeCompare(b.ticker);
}

function plural(count: number, word: string): string {
  return `${count} ${word}${count === 1 ? '' : 's'}`;
}

export function HoldingsPanel({ holdings, nodeIdByHolding, relationships, shared, selectedNodeId, onSelect }: Props) {
  return (
    <div className="holdings-panel">
      <ul className="panel-list">
        {[...holdings].sort(byWeight).map((holding) => {
          const nodeId = nodeIdByHolding.get(holding.id);
          const weight = formatWeight(holding.weight);
          const name = holding.companyDisplayName;
          if (!nodeId) {
            return (
              <li key={holding.id} className="panel-row unavailable">
                <span className="panel-row-main">
                  <span className="ticker">{holding.ticker}</span>
                  {weight && <span className="panel-weight">{weight}</span>}
                </span>
                <span className="panel-row-detail">{STATUS_LABELS[holding.status]}</span>
              </li>
            );
          }
          const { suppliers, customers } = connectionsOf(nodeId, relationships);
          return (
            <li key={holding.id}>
              <button
                type="button"
                className={`panel-row${nodeId === selectedNodeId ? ' chosen' : ''}`}
                aria-pressed={nodeId === selectedNodeId}
                onClick={() => onSelect(nodeId)}
              >
                <span className="panel-row-main">
                  <span className="ticker">{holding.ticker}</span>
                  {name && <span className="panel-name">{name}</span>}
                  {weight && <span className="panel-weight">{weight}</span>}
                </span>
                <span className="panel-row-detail">
                  {plural(suppliers.length, 'Supplier')} · {plural(customers.length, 'Customer')}
                </span>
              </button>
            </li>
          );
        })}
      </ul>

      {shared.length > 0 && (
        <>
          <h2>Shared Suppliers and Customers</h2>
          <p className="muted small">Companies you do not hold that several of your holdings depend on.</p>
          <ul className="panel-list">
            {shared.map((company) => (
              <li key={company.id}>
                <button
                  type="button"
                  className={`panel-row shared${company.id === selectedNodeId ? ' chosen' : ''}`}
                  aria-pressed={company.id === selectedNodeId}
                  onClick={() => onSelect(company.id)}
                >
                  <span className="panel-row-main">
                    {company.ticker && <span className="ticker">{company.ticker}</span>}
                    <span className="panel-name">{company.displayName}</span>
                  </span>
                  <span className="panel-row-detail">{sharedSummary(company)}</span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
