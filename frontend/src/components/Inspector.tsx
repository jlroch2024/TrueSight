// The Supply Chain page's right panel: the details of whatever is chosen.
//
// A company: its name, ticker, whether it is held (and how much), its annual report if it is a holding, and its
// suppliers and customers on the graph, each a button that chooses that relationship. A relationship: what it is, the
// report's own sentence, Show in Report, and a button for each of the two companies.
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import { openInNewTab, showInReportUrl } from '../format';
import {
  type Connection,
  type GraphNode,
  type Selection,
  connectionsOf,
  formatWeight,
  relationshipSummary,
  plainName,
} from '../pages/supplyChain';

type Props = {
  selection: Selection;
  nodes: GraphNode[];
  relationships: Relationship[];
  holdings: Holding[];
  nodeIdByHolding: Map<number, string>;
  onSelectNode: (nodeId: string) => void;
  onSelectRelationship: (relationshipId: number) => void;
};

export function Inspector({ selection, nodes, relationships, holdings, nodeIdByHolding, onSelectNode, onSelectRelationship }: Props) {
  if (selection?.kind === 'company') {
    const node = nodes.find((n) => n.id === selection.nodeId);
    if (node) {
      const holding = holdings.find((h) => nodeIdByHolding.get(h.id) === node.id) ?? null;
      return (
        <CompanyDetails
          node={node}
          holding={holding}
          relationships={relationships}
          onSelectRelationship={onSelectRelationship}
        />
      );
    }
  }
  if (selection?.kind === 'relationship') {
    const relationship = relationships.find((r) => r.id === selection.relationshipId);
    if (relationship) return <RelationshipDetails relationship={relationship} onSelectNode={onSelectNode} />;
  }
  return (
    <div className="inspector">
      <h2>Inspector</h2>
      <p className="muted">Click a company or an arrow to see its details.</p>
    </div>
  );
}

function CompanyDetails({
  node,
  holding,
  relationships,
  onSelectRelationship,
}: {
  node: GraphNode;
  holding: Holding | null;
  relationships: Relationship[];
  onSelectRelationship: (relationshipId: number) => void;
}) {
  const { suppliers, customers } = connectionsOf(node.id, relationships);
  const weight = formatWeight(node.weight);
  return (
    <div className="inspector">
      <p className="inspector-kind">Company</p>
      <h2>{node.name}</h2>
      <p className="inspector-facts">
        {node.ticker && <span className="ticker">{node.ticker}</span>}
        <span>{node.isHolding ? (weight ? `Holding · ${weight} of the portfolio` : 'Holding') : 'Not held'}</span>
      </p>
      {holding?.reportType && holding.reportDate && (
        <div className="inspector-report">
          <p className="muted small">
            {holding.reportType}, filed {holding.reportDate}
          </p>
          {holding.reportLink && (
            <button type="button" className="secondary" onClick={() => openInNewTab(holding.reportLink!)}>
              Show Report
            </button>
          )}
        </div>
      )}
      <ConnectionList title="Suppliers" connections={suppliers} onSelect={onSelectRelationship} />
      <ConnectionList title="Customers" connections={customers} onSelect={onSelectRelationship} />
    </div>
  );
}

function ConnectionList({
  title,
  connections,
  onSelect,
}: {
  title: string;
  connections: Connection[];
  onSelect: (relationshipId: number) => void;
}) {
  return (
    <section className="inspector-section" aria-label={title}>
      <h3>
        {title} <span className="muted">({connections.length})</span>
      </h3>
      {connections.length === 0 ? (
        <p className="muted small">None on this graph.</p>
      ) : (
        <ul className="panel-list">
          {connections.map(({ relationship, company }) => (
            <li key={relationship.id}>
              <button type="button" className="panel-row" onClick={() => onSelect(relationship.id)}>
                <span className="panel-row-main">
                  {company.ticker && <span className="ticker">{company.ticker}</span>}
                  <span className="panel-name">{plainName(company.name)}</span>
                </span>
                {relationship.provides && <span className="panel-row-detail">{relationship.provides}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function RelationshipDetails({
  relationship,
  onSelectNode,
}: {
  relationship: Relationship;
  onSelectNode: (nodeId: string) => void;
}) {
  const [supplier, customer] =
    relationship.type === 'SUPPLIER'
      ? [relationship.counterparty, relationship.company]
      : [relationship.company, relationship.counterparty];
  return (
    <div className="inspector">
      <p className="inspector-kind">Relationship</p>
      <h2>{relationshipSummary(relationship)}</h2>
      <blockquote>{relationship.evidence}</blockquote>
      <p className="muted small">
        {relationship.report.form}, filed {relationship.report.filingDate}
      </p>
      <button type="button" onClick={() => openInNewTab(showInReportUrl(relationship.report.url, relationship.evidence))}>
        Show in Report
      </button>
      <section className="inspector-section" aria-label="Companies">
        <h3>Companies</h3>
        <ul className="panel-list">
          {[
            { role: 'Supplier', company: supplier },
            { role: 'Customer', company: customer },
          ].map(({ role, company }) => (
            <li key={role}>
              <button type="button" className="panel-row" onClick={() => onSelectNode(String(company.id))}>
                <span className="panel-row-main">
                  {company.ticker && <span className="ticker">{company.ticker}</span>}
                  <span className="panel-name">{plainName(company.name)}</span>
                </span>
                <span className="panel-row-detail">{role}</span>
              </button>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
