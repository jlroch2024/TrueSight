// The Inspector's scenario view, "What If It Stops?": a company stopping its supply, and what that would reach in the
// portfolio. A strip of big figures (how much of the portfolio, how many holdings), each holding reached with its path
// drawn as the graph draws links, every link on those paths with its review status and confidence, the ones the report
// calls a sole or single source with their sentences, and the assumptions the answer rests on. The graph lights the
// same paths while this is open (see scenarioFocus in view.ts).
import { ChevronLeft, ChevronRight, CircleDashed, ExternalLink, ShieldCheck } from 'lucide-react';
import type { CSSProperties } from 'react';
import type { CompanySummary, Relationship } from '../../api/relationships';
import type { WhatIf, WhatIfHolding } from '../../api/whatIf';
import { openInNewTab, showInReportUrl, titleCase } from '../../format';
import { NEUTRAL_EDGE } from '../../graph/model';
import { ConfidenceTag } from '../Confidence';
import { Count } from '../Count';
import { FlowLink } from '../FlowLink';
import { Tile } from '../Tile';
import { Stat, Stats } from './InspectorCompany';
import { LoadingRows } from './HoldingsTab';
import type { Scenario } from './useWhatIf';
import { type Chosen, type WorkspaceView, scenarioExposure } from './view';

/** Who supplies whom in a relationship: SUPPLIER means the counterparty supplies the company, CUSTOMER the reverse. */
function direction(link: Relationship): { supplier: CompanySummary; customer: CompanySummary } {
  return link.type === 'SUPPLIER'
    ? { supplier: link.counterparty, customer: link.company }
    : { supplier: link.company, customer: link.counterparty };
}

const SOURCE_LABELS = { SOLE_SOURCE: 'Sole Source', SINGLE_SOURCE: 'Single Source' } as const;

export function ScenarioView({
  view,
  nodeId,
  scenario,
  onRetry,
  onChoose,
}: {
  view: WorkspaceView;
  nodeId: string;
  scenario: Scenario;
  onRetry: () => void;
  onChoose: (chosen: Chosen) => void;
}) {
  const node = view.nodes.find((item) => item.id === nodeId);
  const holding = view.holdingOfNode.get(nodeId) ?? null;
  const name = node?.name ?? holding?.companyName ?? holding?.ticker ?? '';
  const displayName = node?.displayName ?? holding?.companyDisplayName ?? name;
  const ticker = node?.ticker ?? holding?.ticker ?? null;

  return (
    <>
      <div className="insp-title">
        <Tile name={name} ticker={ticker} size={56} holding={!!holding} colour={view.colourOf.get(nodeId) ?? null} />
        <div className="row-text">
          <h2 title={name}>{displayName}</h2>
          <div className="chips">
            {ticker && <span className="chip strong">{ticker}</span>}
            <span className="chip">What If It Stops?</span>
          </div>
        </div>
      </div>

      <div className="actions one-line">
        <button type="button" className="btn small quiet" onClick={() => onChoose({ kind: 'company', nodeId })}>
          <ChevronLeft size={15} />
          Close Scenario
        </button>
      </div>

      {scenario.status === 'loading' && <LoadingRows />}
      {scenario.status === 'error' && (
        <>
          <p role="alert" className="error">{scenario.message}</p>
          <div className="actions">
            <button type="button" className="btn small" onClick={onRetry}>Retry</button>
          </div>
        </>
      )}
      {scenario.status === 'ready' && <Result result={scenario.result} view={view} onChoose={onChoose} />}
    </>
  );
}

function Result({ result, view, onChoose }: { result: WhatIf; view: WorkspaceView; onChoose: (chosen: Chosen) => void }) {
  const exposure = scenarioExposure(result);
  const linkById = new Map(result.links.map((link) => [link.id, link]));
  const reached = result.holdings.length;

  return (
    <>
      <Stats>
        <Stat
          value={<Count value={exposure.value} decimals={Number.isInteger(exposure.value) ? 0 : 1} suffix={exposure.suffix} />}
          label={exposure.label}
          tone={reached > 0 ? 'var(--high)' : 'var(--faint)'}
        />
        <Stat value={reached} label={reached === 1 ? 'Holding Reached' : 'Holdings Reached'} tone="var(--teal)" />
      </Stats>

      {reached === 0 && <p className="prose">No holding in this portfolio depends on {result.company.displayName}.</p>}

      {reached > 0 && (
        <section className="insp-section" aria-label="Holdings Reached">
          <h3>
            <span className="h3-mark alarm" />
            Holdings Reached <span className="count alarm">{reached}</span>
          </h3>
          {result.holdings.map((item) => (
            <ReachedRow key={item.holdingId} item={item} result={result} linkById={linkById} view={view} onChoose={onChoose} />
          ))}
        </section>
      )}

      {result.links.length > 0 && (
        <section className="insp-section" aria-label="Links on the Paths">
          <h3>
            <span className="h3-mark teal" />
            Links on the Paths <span className="count">{result.links.length}</span>
          </h3>
          {result.links.map((link) => (
            <PathLink key={link.id} link={link} view={view} onChoose={onChoose} />
          ))}
        </section>
      )}

      {result.criticalLinks.length > 0 && (
        <section className="insp-section" aria-label="Sole and Single Sources">
          <h3>
            <span className="h3-mark amber" />
            Sole and Single Sources <span className="count">{result.criticalLinks.length}</span>
          </h3>
          {result.criticalLinks.map((link) => {
            const { supplier, customer } = direction(link);
            return (
              <div key={link.id} className="critical-link">
                <p className="prose">
                  <b>{supplier.displayName}</b> to <b>{customer.displayName}</b>
                  {link.criticality && <span className="chip amber">{SOURCE_LABELS[link.criticality]}</span>}
                </p>
                {link.criticalityEvidence && <blockquote className="evidence">{link.criticalityEvidence}</blockquote>}
                {link.criticalityEvidence && (
                  <div className="actions">
                    <button
                      type="button"
                      className="btn small"
                      aria-label={`Show ${supplier.displayName} to ${customer.displayName} in Report`}
                      onClick={() => openInNewTab(showInReportUrl(link.report.url, link.criticalityEvidence!))}
                    >
                      <ExternalLink size={15} />
                      Show in Report
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </section>
      )}

      <p className="prose muted" aria-label="Assumptions">{result.assumptions}</p>
    </>
  );
}

/**
 * One holding reached: its ticker in its chain colour and whether directly, then the path from the stopped company as
 * tiles joined by links drawn as the graph draws them, and the path in words. Choosing it shows the holding.
 */
function ReachedRow({
  item,
  result,
  linkById,
  view,
  onChoose,
}: {
  item: WhatIfHolding;
  result: WhatIf;
  linkById: Map<number, Relationship>;
  view: WorkspaceView;
  onChoose: (chosen: Chosen) => void;
}) {
  const holding = view.holdings.find((found) => found.id === item.holdingId);
  const nodeId = holding ? view.nodeIdOf(holding) : null;
  const chain = nodeId ? view.colourOf.get(nodeId) : undefined;
  const links = item.path.map((id) => linkById.get(id)).filter((link): link is Relationship => !!link);
  const companies: CompanySummary[] =
    links.length > 0
      ? [direction(links[0]!).supplier, ...links.map((link) => direction(link).customer)]
      : [result.company];
  const shown = (company: CompanySummary) => ({
    name: company.name,
    ticker: company.ticker,
    // The stopped company is itself the holding when nothing lies between.
    holding: view.holdingOfNode.has(String(company.id)) || (links.length === 0 && !!holding),
    colour: view.colourOf.get(String(company.id)),
  });
  return (
    <button type="button" className="path-row" onClick={() => nodeId && onChoose({ kind: 'company', nodeId })}>
      <span className="path-head">
        <span className="ticker-chip" style={{ '--chain': chain } as CSSProperties}>
          <i />
          {item.ticker}
        </span>
        <span className={`reach-kind${item.indirect ? ' indirect' : ''}`}>
          {links.length === 0 ? 'Itself' : item.indirect ? 'Indirect' : 'Direct'}
        </span>
      </span>
      <span className="path-steps">
        {companies.map((company, index) => {
          const look = shown(company);
          return (
            <span key={`${company.id}-${index}`} className={`path-step${index > 0 ? ' linked' : ''}`}>
              {index > 0 && <FlowLink colour={chain ?? NEUTRAL_EDGE} />}
              <Tile name={look.name} ticker={look.ticker} size={30} holding={look.holding} colour={look.colour} />
            </span>
          );
        })}
      </span>
      <span className="path-text">
        {companies.map((company, index) => (
          <span key={`${company.id}-${index}`}>
            {index > 0 && <ChevronRight size={12} className="path-sep" />}
            <b>{company.displayName}</b>
          </span>
        ))}
      </span>
    </button>
  );
}

/**
 * A link on the paths: who supplies whom and what, its review status and how confident TrueSight is. Choosing it shows
 * the relationship, when the graph has it.
 */
function PathLink({ link, view, onChoose }: { link: Relationship; view: WorkspaceView; onChoose: (chosen: Chosen) => void }) {
  const { supplier, customer } = direction(link);
  const onGraph = view.relationships.some((relationship) => relationship.id === link.id);
  const body = (
    <>
      <span className="row-text">
        <span className="h-name">
          {supplier.displayName} <ChevronRight size={12} className="path-sep" /> {customer.displayName}
        </span>
        <span className="h-sub">
          {link.provides && <>{titleCase(link.provides)} · </>}
          {link.reviewStatus === 'APPROVED' ? (
            <>
              <ShieldCheck size={12} className="approved-mark" aria-hidden="true" /> Approved
            </>
          ) : (
            <>
              <CircleDashed size={12} aria-hidden="true" /> Not Yet Reviewed
            </>
          )}
          {link.criticality && <span className="chip amber">{SOURCE_LABELS[link.criticality]}</span>}
        </span>
      </span>
      <span className="row-end">
        <ConfidenceTag confidence={link.confidence} />
      </span>
    </>
  );
  return onGraph ? (
    <button type="button" className="link-row" onClick={() => onChoose({ kind: 'relationship', relationshipId: link.id })}>
      {body}
    </button>
  ) : (
    <div className="link-row">{body}</div>
  );
}
