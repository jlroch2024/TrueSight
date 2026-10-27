// The Supply Chain page's right panel: the details of whatever is chosen. Each kind of information has its own look,
// so it can be told apart at a glance: a header with chips (ticker, held or not, shared), labelled facts, suppliers in
// lime and customers in teal (goods flow in from one and out to the other), the evidence in a tinted quote block, and
// alerts as small cards.
//
// A company: its name, ticker, whether it is held (and how much), its annual report if it is a holding, and its
// suppliers and customers on the graph, each a button that chooses that relationship, and its open alerts. A
// relationship: what it is, the
// report's own sentence, Show in Report, and a button for each of the two companies.
import type { AlertEvent } from '../api/alerts';
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import { formatDate, openInNewTab, showInReportUrl, titleCase } from '../format';
import {
  type Connection,
  type GraphNode,
  type Selection,
  alertPaths,
  alertsFor,
  connectionsOf,
  formatWeight,
  relationshipSummary,
} from '../pages/supplyChain';
import { ConfidenceReasons, ConfidenceTag } from './Confidence';

const SEVERITY_LABELS = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' } as const;

type Props = {
  selection: Selection;
  nodes: GraphNode[];
  relationships: Relationship[];
  holdings: Holding[];
  nodeIdByHolding: Map<number, string>;
  onSelectNode: (nodeId: string) => void;
  onSelectRelationship: (relationshipId: number) => void;
  // The portfolio's open alerts; undefined while they load or when they could not be loaded.
  alerts?: AlertEvent[];
};

export function Inspector({
  selection,
  nodes,
  relationships,
  holdings,
  nodeIdByHolding,
  onSelectNode,
  onSelectRelationship,
  alerts,
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
          alerts={alerts}
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
  alerts,
}: {
  node: GraphNode;
  holding: Holding | null;
  relationships: Relationship[];
  onSelectRelationship: (relationshipId: number) => void;
  alerts?: AlertEvent[];
}) {
  const companyAlerts = alerts ? alertsFor(alerts, node.name, holding?.id ?? null) : undefined;
  const { suppliers, customers } = connectionsOf(node.id, relationships);
  const weight = formatWeight(node.weight);
  return (
    <div className="inspector">
      <header className={`inspector-head${node.isHolding ? ' holding' : ''}`}>
        <p className="inspector-kind">Company</p>
        <h2 title={node.name}>{node.displayName}</h2>
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
      {companyAlerts && (
        <section className="inspector-section" aria-label="Alerts">
          <h3>
            Alerts <span className="muted">({companyAlerts.length})</span>
          </h3>
          {companyAlerts.length === 0 ? (
            <p className="muted small">No open alerts.</p>
          ) : (
            <ul className="news-list">
              {companyAlerts.map((event) => (
                <AlertItem key={event.alertIds.join('-')} event={event} holdingId={holding?.id ?? null} />
              ))}
            </ul>
          )}
        </section>
      )}
    </div>
  );
}

/**
 * One open alert, as a small card: its severity, the publisher and date, the headline, how it reaches the portfolio
 * ("TSMC supplies NVIDIA") with how confident TrueSight is about that path, and Open Article.
 */
function AlertItem({ event, holdingId }: { event: AlertEvent; holdingId: number | null }) {
  const { article, assessment } = event.headline;
  return (
    <li className="news-item">
      <p className="news-meta">
        <span className={`news-tag severity-${assessment.severity.toLowerCase()}`}>
          {SEVERITY_LABELS[assessment.severity]}
        </span>
        <span>
          {article.source ? `${article.source} · ` : ''}
          {formatDate(article.publishedAt)}
        </span>
      </p>
      <p className="news-title">{article.title}</p>
      {alertPaths(event, holdingId).map((path) => (
        <p key={path.text} className="muted small alert-path">
          <span>{path.text}</span>
          {path.confidence && <ConfidenceTag confidence={path.confidence} />}
        </p>
      ))}
      <button type="button" className="text-button" onClick={() => openInNewTab(article.url)}>
        Open Article
      </button>
    </li>
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
                  <span className="panel-name">{company.displayName}</span>
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
          <span className="chip chip-supplier">{supplier.ticker ?? supplier.displayName}</span>
          <span className="flow-arrow" aria-hidden="true">
            {'\u2192'}
          </span>
          <span className="chip chip-customer">{customer.ticker ?? customer.displayName}</span>
        </p>
      </header>
      <p className="muted small review-status">
        {relationship.source === 'ANALYST'
          ? 'Added by an Analyst'
          : relationship.reviewStatus === 'APPROVED'
            ? `Approved by an Analyst${relationship.reviewedAt ? ` on ${formatDate(relationship.reviewedAt)}` : ''}`
            : relationship.reviewStatus === 'UNREVIEWED'
              ? 'Not Yet Reviewed by an Analyst'
              : 'Rejected by an Analyst'}
      </p>
      <section className="inspector-section confidence" aria-label="Confidence">
        <ConfidenceTag confidence={relationship.confidence} />
        <ConfidenceReasons confidence={relationship.confidence} />
      </section>
      {relationship.reviewStatus === 'APPROVED' && relationship.reviewNote && (
        <p className="muted small review-note">{relationship.reviewNote}</p>
      )}
      {relationship.criticality && relationship.criticalityEvidence && (
        <figure className="evidence criticality-evidence">
          <figcaption>
            <span className="chip chip-shared">
              {relationship.criticality === 'SOLE_SOURCE' ? 'Sole Source' : 'Single Source'}
            </span>
          </figcaption>
          <blockquote>{relationship.criticalityEvidence}</blockquote>
          <button
            type="button"
            className="secondary small-button"
            aria-label="Show Criticality in Report"
            onClick={() => openInNewTab(showInReportUrl(relationship.report.url, relationship.criticalityEvidence!))}
          >
            Show in Report
          </button>
        </figure>
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
                  <span className="panel-name">{company.displayName}</span>
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
