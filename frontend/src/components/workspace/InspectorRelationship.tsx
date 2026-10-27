// The Inspector's relationship view: the claim as a picture (supplier, the link as the graph draws it, customer, and
// what is supplied on its own line), whether an analyst has reviewed it, how critical the supplier is, the report's own
// sentence with Show in Report, and a button for each of the two companies.
import { ArrowRight, CircleDashed, ExternalLink, FileText, Package, ShieldCheck, UserRoundPen } from 'lucide-react';
import type { CompanySummary } from '../../api/relationships';
import { formatDate, openInNewTab, showInReportUrl, titleCase } from '../../format';
import { NEUTRAL_EDGE } from '../../graph/model';
import { FlowLink } from '../FlowLink';
import { Tile } from '../Tile';
import { type Chosen, type WorkspaceView } from './view';

/**
 * A claim as a picture: the supplier, a link drawn as the graph draws it, and the customer, each named below its tile,
 * and what is supplied on a line of its own below them, centred, with the whole width for a long name.
 */
export function ClaimFlow({
  supplier,
  customer,
  provides,
  colour,
  size,
  view,
}: {
  supplier: CompanySummary;
  customer: CompanySummary;
  provides: string | null;
  colour: string;
  size: number;
  view?: Pick<WorkspaceView, 'holdingOfNode' | 'colourOf'>;
}) {
  const held = (company: CompanySummary) => view?.holdingOfNode.has(String(company.id)) ?? false;
  return (
    <div className="flow-line">
      <Tile name={supplier.name} ticker={supplier.ticker} size={size} holding={held(supplier)} colour={view?.colourOf.get(String(supplier.id))} />
      <FlowLink colour={colour} />
      <Tile name={customer.name} ticker={customer.ticker} size={size} holding={held(customer)} colour={view?.colourOf.get(String(customer.id))} />
      <small>Supplier</small>
      <small className="flow-customer">Customer</small>
      {provides && (
        <span className="wire-chip" title={titleCase(provides)}>
          <Package size={12} />
          <span>{titleCase(provides)}</span>
        </span>
      )}
    </div>
  );
}

export function RelationshipView({
  view,
  relationshipId,
  onChoose,
}: {
  view: WorkspaceView;
  relationshipId: number;
  onChoose: (chosen: Chosen) => void;
}) {
  const relationship = view.relationships.find((item) => item.id === relationshipId);
  if (!relationship) return <p className="muted">No Longer on the Graph</p>;
  const [supplier, customer] =
    relationship.type === 'SUPPLIER'
      ? [relationship.counterparty, relationship.company]
      : [relationship.company, relationship.counterparty];
  // The link in its holding's chain colour, as the graph draws it.
  const colour = view.colourOf.get(String(relationship.company.id)) ?? NEUTRAL_EDGE;
  const approved = relationship.reviewStatus === 'APPROVED';
  const review =
    relationship.source === 'ANALYST'
      ? 'Added by an Analyst'
      : approved
        ? `Approved by an Analyst${relationship.reviewedAt ? ` on ${formatDate(relationship.reviewedAt)}` : ''}`
        : relationship.reviewStatus === 'UNREVIEWED'
          ? 'Not Yet Reviewed by an Analyst'
          : 'Rejected by an Analyst';

  return (
    <>
      <div className="insp-title block">
        <ClaimFlow supplier={supplier} customer={customer} provides={relationship.provides} colour={colour} size={48} view={view} />
        <h2 className="claim-title">
          {supplier.displayName} <span className="muted light">Supplies</span> {customer.displayName}
        </h2>
      </div>

      <div className="chips">
        <span className={`chip${approved || relationship.source === 'ANALYST' ? ' teal' : ''}`}>
          {relationship.source === 'ANALYST' ? <UserRoundPen size={13} /> : approved ? <ShieldCheck size={13} /> : <CircleDashed size={13} />}
          {review}
        </span>
        {relationship.criticality && (
          <span className="chip amber">{relationship.criticality === 'SOLE_SOURCE' ? 'Sole Source' : 'Single Source'}</span>
        )}
      </div>
      {approved && relationship.reviewNote && <p className="prose muted review-note">{relationship.reviewNote}</p>}

      <blockquote className="evidence">
        {relationship.evidence}
        <footer>
          <FileText size={13} />
          From {relationship.company.displayName}’s {relationship.report.form}, Filed {formatDate(relationship.report.filingDate)}
        </footer>
      </blockquote>

      <div className="actions">
        <button
          type="button"
          className="btn primary"
          onClick={() => openInNewTab(showInReportUrl(relationship.report.url, relationship.evidence))}
        >
          <ExternalLink size={16} />
          Show in Report
        </button>
      </div>

      {relationship.criticality && relationship.criticalityEvidence && (
        <section className="insp-section" aria-label="How Critical">
          <h3>
            <span className="h3-mark amber" />
            {relationship.criticality === 'SOLE_SOURCE' ? 'Sole Source' : 'Single Source'}
          </h3>
          <blockquote className="evidence">{relationship.criticalityEvidence}</blockquote>
          <div className="actions">
            <button
              type="button"
              className="btn small"
              aria-label="Show Criticality in Report"
              onClick={() => openInNewTab(showInReportUrl(relationship.report.url, relationship.criticalityEvidence!))}
            >
              <ExternalLink size={15} />
              Show in Report
            </button>
          </div>
        </section>
      )}

      <section className="insp-section" aria-label="Companies">
        <h3>Companies</h3>
        {[supplier, customer].map((company) => (
          <button
            key={company.id}
            type="button"
            className="link-row"
            onClick={() => onChoose({ kind: 'company', nodeId: String(company.id) })}
          >
            <Tile
              name={company.name}
              ticker={company.ticker}
              size={32}
              holding={view.holdingOfNode.has(String(company.id))}
              colour={view.colourOf.get(String(company.id))}
            />
            <span className="row-text">
              <span className="h-name">{company.displayName}</span>
              <span className="h-sub">{company === supplier ? 'Supplier' : 'Customer'}</span>
            </span>
            <ArrowRight size={15} />
          </button>
        ))}
      </section>
    </>
  );
}
