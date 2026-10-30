// The Inspector's company view: the company's tile and name, a strip of big figures (a holding's weight, its suppliers,
// customers and open alerts), a holding's actions (Show Report, Edit Weight, Remove) and facts (status, annual report,
// last analysed), its open alerts, and its suppliers and customers, each a button that chooses that relationship.
import { AnimatePresence, motion } from 'motion/react';
import { ArrowRight, CircleDashed, Clock, FileText, Package, Pencil, ShieldCheck, Trash2 } from 'lucide-react';
import { type CSSProperties, type FormEvent, type ReactNode, useState } from 'react';
import type { History } from '../../api/history';
import type { Holding } from '../../api/portfolios';
import { EVENT_TYPE_LABELS, SEVERITY_LABELS, formatAge, formatDate, openInNewTab, titleCase } from '../../format';
import { type Connection, alertsFor, connectionsOf } from '../../pages/supplyChain';
import { Count } from '../Count';
import { EventMark, SeverityBars } from '../EventMark';
import { Tile } from '../Tile';
import { State } from './status';
import { type Chosen, type WorkspaceView, alertKey, joinYears, namedIn } from './view';

/** A strip of big figures: what the eye should take in first. */
export function Stats({ children }: { children: ReactNode }) {
  return <div className="stats">{children}</div>;
}

export function Stat({ value, label, tone, children }: { value: ReactNode; label: string; tone?: string; children?: ReactNode }) {
  return (
    <div className="stat" style={tone ? ({ '--tone': tone } as CSSProperties) : undefined}>
      <strong className="num">{value}</strong>
      <span className="stat-label">{label}</span>
      {children}
    </div>
  );
}

export function CompanyView({
  view,
  nodeId,
  onChoose,
  onWeight,
  onRemove,
  history = null,
}: {
  view: WorkspaceView;
  nodeId: string;
  // What the SEC Filing Timeline has read, once it has: the years this company was named in.
  history?: History | null;
  onChoose: (chosen: Chosen) => void;
  // Saves a holding's new weight; answers the message to show if it was refused.
  onWeight: (holding: Holding, weight: string) => Promise<string | null>;
  onRemove: (holding: Holding) => void;
}) {
  const holding = view.holdingOfNode.get(nodeId) ?? null;
  const node =
    view.nodes.find((item) => item.id === nodeId) ??
    (holding && {
      id: nodeId,
      name: holding.companyName ?? holding.ticker,
      displayName: holding.companyDisplayName ?? holding.ticker,
      ticker: holding.ticker,
      isHolding: true,
      weight: holding.weight,
      sharedSummary: undefined,
    });
  if (!node) return <p className="muted">No Longer on the Graph</p>;
  const { suppliers, customers } = connectionsOf(nodeId, view.relationships);
  const theirs = alertsFor(view.open, node.name, holding?.id ?? null);
  // A node on the graph is keyed by its company's id; a holding off the graph has none the website knows.
  const companyId = /^\d+$/.test(nodeId) ? Number(nodeId) : null;
  const named = history && companyId !== null ? namedIn(history, companyId) : [];
  const colour = view.colourOf.get(nodeId) ?? null;
  const shared = view.shared.find((company) => company.id === nodeId);

  return (
    <>
      <div className="insp-title">
        <Tile
          name={node.name}
          ticker={node.ticker}
          size={56}
          holding={!!holding}
          colour={colour}
          beacon={view.alertDots.get(nodeId) ?? null}
          shared={shared ? Math.max(shared.suppliesHoldings, shared.buysFromHoldings) : undefined}
        />
        <div className="row-text">
          <h2 title={node.name}>{node.displayName}</h2>
          <div className="chips">
            {node.ticker && <span className="chip strong">{node.ticker}</span>}
            {holding ? <span className="chip teal">Holding</span> : <span className="chip">Not Held</span>}
            {node.sharedSummary && <span className="chip amber">{node.sharedSummary}</span>}
          </div>
        </div>
      </div>

      <Stats>
        {holding && (
          <Stat
            value={holding.weight === null ? '—' : <Count value={holding.weight} decimals={Number.isInteger(holding.weight) ? 0 : 1} suffix="%" />}
            label="Weight"
            tone={colour ?? 'var(--text)'}
          >
            <span className="stat-meter">
              <motion.i animate={{ width: `${Math.min(100, (holding.weight ?? 0) * 4)}%` }} transition={{ type: 'spring', stiffness: 200, damping: 30 }} />
            </span>
          </Stat>
        )}
        <Stat value={suppliers.length} label={suppliers.length === 1 ? 'Supplier' : 'Suppliers'} tone="var(--teal)" />
        <Stat value={customers.length} label={customers.length === 1 ? 'Customer' : 'Customers'} tone="var(--lime)" />
        <Stat value={theirs.length} label={theirs.length === 1 ? 'Open Alert' : 'Open Alerts'} tone={theirs.length ? 'var(--high)' : 'var(--faint)'} />
      </Stats>

      {holding && <HoldingActions holding={holding} onWeight={onWeight} onRemove={onRemove} />}

      {holding && (
        <div className="facts">
          <div className="fact">
            <span className="fact-icon"><ShieldCheck size={15} /></span>
            <span className="fact-label">Status</span>
            <span className="fact-value">
              <State status={holding.status} />
              {holding.statusReason && <span className="fact-reason">{holding.statusReason}</span>}
            </span>
          </div>
          {holding.reportType && (
            <div className="fact">
              <span className="fact-icon"><FileText size={15} /></span>
              <span className="fact-label">Annual Report</span>
              <span>
                <b>{holding.reportType}</b>
                {holding.reportDate ? ` · Filed ${formatDate(holding.reportDate)}` : ''}
              </span>
            </div>
          )}
          {holding.status === 'DONE' && holding.lastAnalysed && (
            <div className="fact">
              <span className="fact-icon"><Clock size={15} /></span>
              <span className="fact-label">Last Analysed</span>
              <span>{formatAge(holding.lastAnalysed)}</span>
            </div>
          )}
        </div>
      )}

      {named.length > 0 && (
        <section className="insp-section" aria-label="History">
          <h3>
            <span className="h3-mark teal" />
            History
          </h3>
          {named.map((line) => (
            <p key={line.role} className="prose">
              Named as a {line.role} in {joinYears(line.years)}
            </p>
          ))}
        </section>
      )}

      {theirs.length > 0 && (
        <section className="insp-section" aria-label="Open Alerts">
          <h3>
            <span className="h3-mark alarm" />
            Open Alerts <span className="count alarm">{theirs.length}</span>
          </h3>
          {theirs.map((alert) => (
            <button
              key={alertKey(alert)}
              type="button"
              className="mini-alert"
              data-severity={alert.headline.assessment.severity}
              onClick={() => onChoose({ kind: 'alert', key: alertKey(alert) })}
            >
              <EventMark type={alert.headline.assessment.eventType} severity={alert.headline.assessment.severity} size={30} />
              <span className="row-text">
                <span className="mini-alert-top">
                  <SeverityBars severity={alert.headline.assessment.severity} />
                  {SEVERITY_LABELS[alert.headline.assessment.severity]} · {EVENT_TYPE_LABELS[alert.headline.assessment.eventType]}
                </span>
                <span className="h-name mini-alert-title">{alert.headline.article.title}</span>
              </span>
              <ArrowRight size={15} />
            </button>
          ))}
        </section>
      )}

      <Connections title="Suppliers" list={suppliers} tone="teal" view={view} onChoose={onChoose} />
      <Connections title="Customers" list={customers} tone="lime" view={view} onChoose={onChoose} />
    </>
  );
}

/** A holding's actions on one line: Show Report, and Edit Weight and Remove, which the weight's box takes the place of. */
function HoldingActions({
  holding,
  onWeight,
  onRemove,
}: {
  holding: Holding;
  onWeight: (holding: Holding, weight: string) => Promise<string | null>;
  onRemove: (holding: Holding) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const refused = await onWeight(holding, draft);
    setSaving(false);
    setError(refused);
    if (!refused) setEditing(false);
  }

  return (
    <>
      <div className="actions one-line">
        {holding.status === 'DONE' && holding.reportLink && (
          <button type="button" className="btn small" onClick={() => openInNewTab(holding.reportLink!)}>
            <FileText size={15} />
            Show Report
          </button>
        )}
        <AnimatePresence mode="popLayout" initial={false}>
          {editing ? (
            <motion.form
              key="edit"
              className="weight-edit"
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -8 }}
              onSubmit={save}
            >
              <input
                className="input"
                aria-label={`Weight for ${holding.ticker}`}
                value={draft}
                autoFocus
                inputMode="decimal"
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Escape') {
                    setEditing(false);
                    setError(null);
                  }
                }}
              />
              <button type="submit" className="btn small primary" disabled={saving}>Save</button>
              <button
                type="button"
                className="btn small quiet"
                disabled={saving}
                onClick={() => {
                  setEditing(false);
                  setError(null);
                }}
              >
                Cancel
              </button>
            </motion.form>
          ) : (
            <motion.span key="buttons" className="contents">
              <button
                type="button"
                className="btn small"
                onClick={() => {
                  setDraft(holding.weight === null ? '' : String(holding.weight));
                  setEditing(true);
                }}
              >
                <Pencil size={15} />
                Edit Weight
              </button>
              <button type="button" className="btn small danger" onClick={() => onRemove(holding)}>
                <Trash2 size={15} />
                Remove
              </button>
            </motion.span>
          )}
        </AnimatePresence>
      </div>
      {error && <p role="alert" className="error">{error}</p>}
    </>
  );
}

function Connections({
  title,
  list,
  tone,
  view,
  onChoose,
}: {
  title: string;
  list: Connection[];
  tone: 'teal' | 'lime';
  view: WorkspaceView;
  onChoose: (chosen: Chosen) => void;
}) {
  return (
    <section className="insp-section" aria-label={title}>
      <h3>
        <span className={`h3-mark ${tone}`} />
        {title} <span className="count">{list.length}</span>
      </h3>
      {list.length === 0 ? (
        <p className="prose muted">None on the Graph</p>
      ) : (
        list.map(({ relationship, company }) => (
          <button
            key={company.id}
            type="button"
            className="link-row"
            onClick={() => onChoose({ kind: 'relationship', relationshipId: relationship.id })}
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
              {relationship.provides && (
                <span className="h-sub">
                  <Package size={12} className="faint" />
                  {titleCase(relationship.provides)}
                </span>
              )}
            </span>
            <span className="row-end">
              {relationship.reviewStatus === 'APPROVED' ? (
                <ShieldCheck size={15} className="approved-mark" aria-label="Approved" />
              ) : (
                <CircleDashed size={15} aria-label="Not Yet Reviewed" />
              )}
              <ArrowRight size={15} />
            </span>
          </button>
        ))
      )}
    </section>
  );
}
