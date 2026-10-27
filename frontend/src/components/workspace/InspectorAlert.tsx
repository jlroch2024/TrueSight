// The Inspector's alert view: the event's mark, severity, type and headline; a strip of big figures (how much of the
// portfolio it reaches, how many holdings, how sure the assessment is); Open Article and Close Alert or Restore; what
// happened and why it matters; how it reaches each holding, as a path of linked tiles drawn as the graph draws them;
// the analyst-approved suggested next step; and its other sources.
import { ChevronRight, BellOff, Clock, FileText, Lightbulb, Newspaper, ShieldCheck, Undo2, Waypoints } from 'lucide-react';
import type { CSSProperties } from 'react';
import type { AlertEvent, ReachedHolding } from '../../api/alerts';
import { EVENT_TYPE_LABELS, SEVERITY_LABELS, formatAge, openInNewTab } from '../../format';
import { NEUTRAL_EDGE } from '../../graph/model';
import { Confidence, EventMark, SeverityBars } from '../EventMark';
import { ConfidenceTag } from '../Confidence';
import { Count } from '../Count';
import { FlowLink } from '../FlowLink';
import { Tile } from '../Tile';
import { Stat, Stats } from './InspectorCompany';
import { type Chosen, type WorkspaceView, alertKey, exposureOf } from './view';

const SEVERITY_TONE = { HIGH: 'var(--high)', MEDIUM: 'var(--medium)', LOW: 'var(--low)' } as const;

export function AlertView({
  view,
  wanted,
  busy,
  onChoose,
  onClose,
  onRestore,
}: {
  view: WorkspaceView;
  // The alert's key (see alertKey).
  wanted: string;
  busy: boolean;
  onChoose: (chosen: Chosen) => void;
  onClose: (alert: AlertEvent) => void;
  onRestore: (alert: AlertEvent) => void;
}) {
  const alert = view.alerts.find((item) => alertKey(item) === wanted);
  if (!alert) return <p className="muted">Alert No Longer Listed</p>;
  const { article, assessment } = alert.headline;
  const reached = [...alert.holdings].sort((a, b) => Number(a.indirect) - Number(b.indirect) || a.ticker.localeCompare(b.ticker));
  const reachedIds = new Set(reached.map((item) => item.holdingId));
  const direct = new Set(reached.filter((item) => !item.indirect).map((item) => item.holdingId)).size;
  const indirect = reachedIds.size - direct;
  const companyNode = article.companyName ? view.nodeIdOfName.get(article.companyName) : undefined;
  const closed = alert.status === 'DISMISSED';
  const tone = SEVERITY_TONE[assessment.severity];
  const exposure = exposureOf(alert, view);

  return (
    <>
      <div className="alert-hero" style={{ '--tone': tone } as CSSProperties}>
        <EventMark type={assessment.eventType} severity={assessment.severity} size={46} />
        <div className="row-text">
          <div className="alert-hero-top">
            <SeverityBars severity={assessment.severity} />
            <span className="a-sev">{SEVERITY_LABELS[assessment.severity]}</span>
            <span className="a-type">{EVENT_TYPE_LABELS[assessment.eventType]}</span>
            {closed && <span className="chip">Closed</span>}
          </div>
          <h2>{article.title}</h2>
          <p className="alert-hero-meta">
            <Newspaper size={13} /> {article.source ?? 'Source Not Recorded'}
            <span className="dot-sep" />
            <Clock size={13} /> {formatAge(article.publishedAt)}
          </p>
        </div>
      </div>

      <Stats>
        <Stat value={<Count value={exposure.value} decimals={Number.isInteger(exposure.value) ? 0 : 1} suffix={exposure.suffix} />} label={exposure.label} tone={tone}>
          <span className="expo-cells wide" aria-hidden="true">
            {view.holdings.map((holding) => (
              <i key={holding.id} className={reachedIds.has(holding.id) ? 'on' : ''} />
            ))}
          </span>
        </Stat>
        <Stat value={reachedIds.size} label={reachedIds.size === 1 ? 'Holding Reached' : 'Holdings Reached'} tone="var(--text)">
          <span className="reach-split">
            <span><i className="direct" />{direct} Direct</span>
            {indirect > 0 && <span><i className="indirect" />{indirect} Indirect</span>}
          </span>
        </Stat>
        <Stat value={<Confidence level={assessment.confidence} />} label={`${SEVERITY_LABELS[assessment.confidence]} Confidence`} tone="var(--text)" />
      </Stats>

      <div className="actions one-line">
        <button type="button" className="btn small" onClick={() => openInNewTab(article.url)}>
          <Newspaper size={15} />
          Open Article
        </button>
        {closed ? (
          <button type="button" className="btn small" disabled={busy} onClick={() => onRestore(alert)}>
            <Undo2 size={15} />
            Restore
          </button>
        ) : (
          <button type="button" className="btn small" disabled={busy} onClick={() => onClose(alert)}>
            <BellOff size={15} />
            Close Alert
          </button>
        )}
        {companyNode && (
          <button type="button" className="btn small quiet" onClick={() => onChoose({ kind: 'company', nodeId: companyNode })}>
            <FileText size={15} />
            The Company
          </button>
        )}
      </div>

      <section className="insp-section story">
        {assessment.summary && (
          <>
            <h3>What Happened</h3>
            <p className="prose">{assessment.summary}</p>
          </>
        )}
        <h3>Why It Matters</h3>
        <p className="prose">{assessment.reason}</p>
        <p className="prose faint small">Based on the Headline Only</p>
      </section>

      <section className="insp-section" aria-label="How It Reaches You">
        <h3>
          <Waypoints size={16} className="muted" />
          How It Reaches You
        </h3>
        {reached.map((item) => (
          <PathRow key={`${item.holdingId}-${item.relationshipId}-${item.secondRelationshipId}`} item={item} view={view} onChoose={onChoose} />
        ))}
      </section>

      {alert.recommendation && (
        <section className="insp-section" aria-label="Suggested Next Step">
          <h3>
            <Lightbulb size={16} className="lime-ink" />
            Suggested Next Step
          </h3>
          <div className="suggestion">
            <p>{alert.recommendation}</p>
            <small>
              <ShieldCheck size={12} /> Approved by an analyst. Research support, not investment advice.
            </small>
          </div>
        </section>
      )}

      {alert.sources.length > 1 && (
        <section className="insp-section" aria-label="Also Reported By">
          <h3>
            Also Reported By <span className="count">{alert.sources.length}</span>
          </h3>
          {alert.sources.map((source, index) => (
            <button key={`${source.url}-${index}`} type="button" className="source-row" onClick={() => openInNewTab(source.url)}>
              <span className="source-icon"><Newspaper size={14} /></span>
              <span>
                {source.title}
                <span className="source-meta">
                  {source.source ?? 'Source Not Recorded'} · {formatAge(source.publishedAt)}
                </span>
              </span>
            </button>
          ))}
        </section>
      )}
    </>
  );
}

/**
 * How the alert reaches one holding: its ticker in its chain colour and whether directly, then the path as tiles joined
 * by links drawn as the graph draws them (the one the disruption runs through stalled in its colour, the rest flowing
 * in the holding's), and the path in words. Choosing it shows the holding.
 */
function PathRow({ item, view, onChoose }: { item: ReachedHolding; view: WorkspaceView; onChoose: (chosen: Chosen) => void }) {
  const holding = view.holdings.find((h) => h.id === item.holdingId);
  const nodeId = holding ? view.nodeIdOf(holding) : null;
  const chain = nodeId ? view.colourOf.get(nodeId) : undefined;
  const companies = view.relationships.flatMap((r) => [r.company, r.counterparty]);
  // Each company on the path as itself: its logo, and a holding's chamfer and chain colour.
  const step = (shown: string) => {
    const company = companies.find((found) => found.displayName === shown);
    const held = view.holdings.find((h) => h.companyDisplayName === shown);
    return {
      shown,
      name: company?.name ?? held?.companyName ?? shown,
      ticker: company?.ticker ?? held?.ticker ?? null,
      holding: !!held,
      colour: held ? view.colourOf.get(view.nodeIdOf(held)) : undefined,
    };
  };
  const steps = item.supplierName
    ? [step(item.supplierName), ...(item.middleName ? [step(item.middleName)] : []), step(item.customerName ?? '')]
    : [{ shown: item.ticker, name: holding?.companyName ?? item.ticker, ticker: item.ticker, holding: true, colour: chain }];
  const links = [item.relationshipId, item.secondRelationshipId];
  const linkLook = (index: number) => {
    const id = links[index];
    return { colour: chain ?? NEUTRAL_EDGE, risk: id != null ? (view.risks.get(id)?.severity ?? null) : null };
  };
  return (
    <button type="button" className="path-row" onClick={() => nodeId && onChoose({ kind: 'company', nodeId })}>
      <span className="path-head">
        <span className="ticker-chip" style={{ '--chain': chain } as CSSProperties}>
          <i />
          {item.ticker}
        </span>
        <span className={`reach-kind${item.indirect ? ' indirect' : ''}`}>{item.indirect ? 'Indirect' : 'Direct'}</span>
        {/* How confident TrueSight is about this path; none for an alert about the holding itself. */}
        {item.confidence && <ConfidenceTag confidence={item.confidence} />}
        {item.reviewStatus === 'UNREVIEWED' && <span className="faint small">Not Yet Reviewed by an Analyst</span>}
      </span>
      <span className="path-steps">
        {steps.map((shownStep, index) => (
          <span key={`${shownStep.name}-${index}`} className={`path-step${index > 0 ? ' linked' : ''}`}>
            {index > 0 && <FlowLink {...linkLook(index - 1)} />}
            <Tile name={shownStep.name} ticker={shownStep.ticker} size={30} holding={shownStep.holding} colour={shownStep.colour} />
          </span>
        ))}
      </span>
      <span className="path-text">
        {item.supplierName
          ? steps.map((shownStep, index) => (
              <span key={`${shownStep.shown}-${index}`}>
                {index > 0 && <ChevronRight size={12} className="path-sep" />}
                <b>{shownStep.shown}</b>
              </span>
            ))
          : 'Named in the Article'}
      </span>
    </button>
  );
}
