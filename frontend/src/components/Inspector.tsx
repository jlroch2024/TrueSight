// The Supply Chain page's right panel: the details of whatever is chosen. Each kind of information has its own look,
// so it can be told apart at a glance: a header with chips (ticker, held or not, shared), labelled facts, suppliers in
// lime and customers in teal (goods flow in from one and out to the other), the evidence in a tinted quote block, and
// news as small cards.
//
// A company: its name, ticker, whether it is held (and how much), its annual report if it is a holding, and its
// suppliers and customers on the graph, each a button that chooses that relationship. A relationship: what it is, the
// report's own sentence, Show in Report, and a button for each of the two companies.
import type { NewsArticle } from '../api/news';
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import { formatDate, openInNewTab, showInReportUrl, titleCase } from '../format';
import {
  type Connection,
  type GraphNode,
  type Selection,
  connectionsOf,
  displayName,
  formatWeight,
  relationshipSummary,
} from '../pages/supplyChain';
import { NewsItem } from './NewsPanel';

// How many of a company's headlines the Inspector shows; Show All News lists the rest.
const NEWS_SHOWN = 3;

type Props = {
  selection: Selection;
  nodes: GraphNode[];
  relationships: Relationship[];
  holdings: Holding[];
  nodeIdByHolding: Map<number, string>;
  onSelectNode: (nodeId: string) => void;
  onSelectRelationship: (relationshipId: number) => void;
  // The portfolio's recent news; undefined while it loads or when the page has none to give.
  news?: NewsArticle[];
  // Opens the News tab, narrowed to one company.
  onShowAllNews?: (companyName: string) => void;
};

export function Inspector({
  selection,
  nodes,
  relationships,
  holdings,
  nodeIdByHolding,
  onSelectNode,
  onSelectRelationship,
  news,
  onShowAllNews,
}: Props) {
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
          news={news}
          onShowAllNews={onShowAllNews}
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
  news,
  onShowAllNews,
}: {
  node: GraphNode;
  holding: Holding | null;
  relationships: Relationship[];
  onSelectRelationship: (relationshipId: number) => void;
  news?: NewsArticle[];
  onShowAllNews?: (companyName: string) => void;
}) {
  const companyNews = news?.filter((article) => article.company.name === node.name);
  const { suppliers, customers } = connectionsOf(node.id, relationships);
  const weight = formatWeight(node.weight);
  return (
    <div className="inspector">
      <header className={`inspector-head${node.isHolding ? ' holding' : ''}`}>
        <p className="inspector-kind">Company</p>
        <h2 title={node.name}>{displayName(node.name)}</h2>
        <p className="chips">
          {node.ticker && <span className="chip chip-ticker">{node.ticker}</span>}
          <span className={`chip ${node.isHolding ? 'chip-holding' : 'chip-muted'}`}>
            {node.isHolding ? (weight ? `Holding · ${weight} of the Portfolio` : 'Holding') : 'Not Held'}
          </span>
          {node.sharedSummary && <span className="chip chip-shared">{node.sharedSummary}</span>}
        </p>
      </header>
      {holding?.reportType && holding.reportDate && (
        <dl className="facts">
          <div>
            <dt>Annual Report</dt>
            <dd>
              {holding.reportType}, Filed {formatDate(holding.reportDate)}
            </dd>
          </div>
          {holding.reportLink && (
            <button type="button" className="secondary small-button" onClick={() => openInNewTab(holding.reportLink!)}>
              Show Report
            </button>
          )}
        </dl>
      )}
      <ConnectionList kind="suppliers" title="Suppliers" connections={suppliers} onSelect={onSelectRelationship} />
      <ConnectionList kind="customers" title="Customers" connections={customers} onSelect={onSelectRelationship} />
      {companyNews && (
        <section className="inspector-section" aria-label="Recent News">
          <h3>
            Recent News <span className="muted">({companyNews.length})</span>
          </h3>
          {companyNews.length === 0 ? (
            <p className="muted small">No news in the last 7 days.</p>
          ) : (
            <>
              <ul className="news-list">
                {companyNews.slice(0, NEWS_SHOWN).map((article) => (
                  <NewsItem key={article.id} article={article} />
                ))}
              </ul>
              {onShowAllNews && (
                <button type="button" className="secondary" onClick={() => onShowAllNews(node.name)}>
                  Show All News
                </button>
              )}
            </>
          )}
        </section>
      )}
    </div>
  );
}

function ConnectionList({
  kind,
  title,
  connections,
  onSelect,
}: {
  // Suppliers are drawn in lime and customers in teal, each with its own marker, so the two lists never blur.
  kind: 'suppliers' | 'customers';
  title: string;
  connections: Connection[];
  onSelect: (relationshipId: number) => void;
}) {
  return (
    <section className={`inspector-section connections ${kind}`} aria-label={title}>
      <h3>
        {title} <span className="muted">({connections.length})</span>
      </h3>
      {connections.length === 0 ? (
        <p className="muted small">None on This Graph</p>
      ) : (
        <ul className="panel-list">
          {connections.map(({ relationship, company }) => (
            <li key={relationship.id}>
              <button type="button" className="panel-row" onClick={() => onSelect(relationship.id)}>
                <span className="panel-row-main">
                  {company.ticker && <span className="ticker">{company.ticker}</span>}
                  <span className="panel-name">{displayName(company.name)}</span>
                </span>
                {relationship.provides && <span className="panel-row-detail">{titleCase(relationship.provides)}</span>}
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
      <header className="inspector-head relationship">
        <p className="inspector-kind">Relationship</p>
        <h2>{relationshipSummary(relationship)}</h2>
        <p className="chips flow">
          <span className="chip chip-supplier">{supplier.ticker ?? displayName(supplier.name)}</span>
          <span className="flow-arrow" aria-hidden="true">
            {'\u2192'}
          </span>
          <span className="chip chip-customer">{customer.ticker ?? displayName(customer.name)}</span>
        </p>
      </header>
      <p className="muted small review-status">
        {relationship.reviewStatus === 'APPROVED'
          ? `Approved by an Analyst${relationship.reviewedAt ? ` on ${formatDate(relationship.reviewedAt)}` : ''}`
          : relationship.reviewStatus === 'UNREVIEWED'
            ? 'Not Yet Reviewed by an Analyst'
            : 'Rejected by an Analyst'}
      </p>
      {relationship.reviewStatus === 'APPROVED' && relationship.reviewNote && (
        <p className="muted small review-note">{relationship.reviewNote}</p>
      )}
      <figure className="evidence">
        <figcaption>Evidence From the Annual Report</figcaption>
        <blockquote>{relationship.evidence}</blockquote>
      </figure>
      <dl className="facts">
        <div>
          <dt>Annual Report</dt>
          <dd>
            {relationship.report.form}, Filed {formatDate(relationship.report.filingDate)}
          </dd>
        </div>
      </dl>
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
                  <span className="panel-name">{displayName(company.name)}</span>
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
