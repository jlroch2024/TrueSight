// The drawer's Holdings tab: the most serious open alert first, then one row per holding (its tile, with its chain
// colour ring and alert beacon, its name, ticker, supplier and customer counts or its status, and its weight with a
// meter), Add Holding, and the companies several holdings depend on. Choosing a row shows the company in the
// Inspector; pointing at one lights it on the graph.
import { AnimatePresence, motion } from 'motion/react';
import { ArrowRight, Check, Inbox, LoaderCircle, Plus, Upload } from 'lucide-react';
import { type CSSProperties, type FormEvent, useEffect, useRef, useState } from 'react';
import type { AlertEvent } from '../../api/alerts';
import type { Holding } from '../../api/portfolios';
import { EVENT_TYPE_LABELS, SEVERITY_LABELS } from '../../format';
import { connectionsOf, sharedSummary } from '../../pages/supplyChain';
import { Count } from '../Count';
import { EventMark, SeverityBars } from '../EventMark';
import { PANEL_SPRING, plural } from '../motion';
import { Tile } from '../Tile';
import { State } from './status';
import { type WorkspaceView, exposureOf } from './view';

/** Loading rows: placeholders with a soft light passing over them. */
export function LoadingRows() {
  return (
    <div className="h-list" aria-busy="true" aria-label="Loading">
      {Array.from({ length: 7 }, (_, index) => (
        <div key={index} className="h-row" style={{ opacity: 1 - index * 0.11 }}>
          <span className="tile shimmer" style={{ '--size': '36px' } as CSSProperties} />
          <span className="shimmer-lines">
            <span className="shimmer" style={{ height: 11, width: `${60 + ((index * 17) % 30)}%` }} />
            <span className="shimmer" style={{ height: 9, width: '40%' }} />
          </span>
          <span className="shimmer" style={{ height: 11, width: 30 }} />
        </div>
      ))}
    </div>
  );
}

export function HoldingsTab({
  view,
  chosenNode,
  worst,
  onWorst,
  onPeek,
  onChoose,
  onAdd,
  onUpload,
  uploading,
}: {
  view: WorkspaceView;
  chosenNode: string | null;
  // The most serious open alert, shown first.
  worst: AlertEvent | null;
  onWorst: () => void;
  onPeek: (nodeId: string | null) => void;
  onChoose: (nodeId: string) => void;
  // Adds a holding; answers the message to show if it was refused.
  onAdd: (ticker: string, weight: string) => Promise<string | null>;
  onUpload: () => void;
  uploading: boolean;
}) {
  const { holdings, shared, relationships } = view;
  const [adding, setAdding] = useState(false);
  // A list that goes away never hears the pointer leave it: its preview on the graph is let go here instead.
  useEffect(() => () => onPeek(null), [onPeek]);
  const total = holdings.reduce((sum, holding) => sum + (holding.weight ?? 0), 0);
  const biggest = Math.max(1, ...holdings.map((holding) => holding.weight ?? 0));
  const tickerWidth = Math.ceil(Math.max(30, ...holdings.map((holding) => tickerTextWidth(holding.ticker)))) + 4;
  const failed = holdings.some((holding) => holding.status === 'FAILED');
  const openCount = view.open.length;

  if (holdings.length === 0) {
    return (
      <div className="empty">
        <Inbox size={28} />
        <span>No Holdings Yet</span>
        <button type="button" className="btn small primary" disabled={uploading} onClick={onUpload}>
          <Upload size={15} /> Upload CSV
        </button>
        <AddForm onAdd={onAdd} />
      </div>
    );
  }

  return (
    <>
      {worst && (
        <button type="button" className="attention" data-severity={worst.headline.assessment.severity} onClick={onWorst}>
          <EventMark type={worst.headline.assessment.eventType} severity={worst.headline.assessment.severity} size={36} />
          <span className="attention-text">
            <span className="attention-title">
              <SeverityBars severity={worst.headline.assessment.severity} />
              {SEVERITY_LABELS[worst.headline.assessment.severity]} · {EVENT_TYPE_LABELS[worst.headline.assessment.eventType]}
            </span>
            <span className="attention-line">
              Reaches <b>{[...new Set(worst.holdings.map((reached) => reached.ticker))].join(', ')}</b>
              {openCount > 1 && <span className="faint"> · {plural(openCount - 1, 'Other Alert', 'Other Alerts')}</span>}
            </span>
          </span>
          <span className="attention-share">
            <strong className="num">{exposureOf(worst, view).value}{exposureOf(worst, view).suffix}</strong>
            <small>{view.weighted ? 'Exposed' : exposureOf(worst, view).label}</small>
          </span>
        </button>
      )}
      <div className="toolbar">
        <span className="toolbar-stat">
          <strong className="num"><Count value={holdings.length} /></strong> {holdings.length === 1 ? 'Holding' : 'Holdings'}
          {total > 0 && (
            <>
              <span className="dot-sep" />
              <strong className="num"><Count value={Number(total.toFixed(1))} suffix="%" /></strong> Weighted
            </>
          )}
        </span>
        <button type="button" className="btn small quiet" aria-expanded={adding} onClick={() => setAdding((value) => !value)}>
          <motion.span animate={{ rotate: adding ? 45 : 0 }} className="plus-turn"><Plus size={15} /></motion.span>
          {adding ? 'Close' : 'Add Holding'}
        </button>
      </div>
      <AnimatePresence initial={false}>
        {adding && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={PANEL_SPRING}
            className="add-reveal"
          >
            <AddForm onAdd={onAdd} />
          </motion.div>
        )}
      </AnimatePresence>
      {failed && <p className="section-note">Some holdings failed. Press Analyse to try them again.</p>}
      <div className="h-list" role="list" aria-label="Holdings" style={{ '--ticker-w': `${tickerWidth}px` } as CSSProperties}>
        <AnimatePresence initial={false}>
          {holdings.map((holding, index) => {
            const nodeId = view.nodeIdOf(holding);
            const onGraph = view.nodeIdByHolding.has(holding.id);
            return (
              <HoldingRow
                key={holding.id}
                index={index}
                holding={holding}
                colour={view.colourOf.get(nodeId)}
                beacon={view.alertDots.get(nodeId) ?? null}
                links={onGraph ? connectionsOf(nodeId, relationships) : null}
                share={(holding.weight ?? 0) / biggest}
                current={nodeId === chosenNode}
                onPeek={() => onPeek(onGraph ? nodeId : null)}
                onLeave={() => onPeek(null)}
                onChoose={() => onChoose(nodeId)}
              />
            );
          })}
        </AnimatePresence>
      </div>

      {shared.length > 0 && (
        <>
          <div className="section-label">
            Shared Suppliers and Customers
            <span className="count amber">{shared.length}</span>
          </div>
          <p className="section-note">Companies you do not hold that several of your holdings depend on.</p>
          <div className="h-list">
            {shared.map((company) => (
              <button
                key={company.id}
                type="button"
                className="shared-row"
                onPointerEnter={() => onPeek(company.id)}
                onPointerLeave={() => onPeek(null)}
                onClick={() => onChoose(company.id)}
              >
                <Tile name={company.name} ticker={company.ticker} size={34} shared={Math.max(company.suppliesHoldings, company.buysFromHoldings)} />
                <span className="row-text">
                  <span className="h-name">{company.displayName}</span>
                  <span className="h-sub">{sharedSummary(company)}</span>
                </span>
                <ArrowRight size={15} className="faint" />
              </button>
            ))}
          </div>
        </>
      )}
    </>
  );
}

/**
 * How many suppliers and customers a holding has, on two lines: the top one level with the holding's name, the bottom
 * one with its ticker. A zero stays, faint, so every row lines up; a holding with neither says so on the top line.
 */
function LinkLines({ suppliers, customers }: { suppliers: number; customers: number }) {
  if (suppliers === 0 && customers === 0) {
    return (
      <span className="link-lines">
        <span className="link-line faint">Nothing Found</span>
      </span>
    );
  }
  const line = (kind: 'supplier' | 'customer', count: number, one: string, many: string) => (
    <span className={`link-line ${kind}${count === 0 ? ' zero' : ''}`}>
      <span className="link-number num">{count}</span> <span className="link-word">{count === 1 ? one : many}</span>
    </span>
  );
  return (
    <span className="link-lines">
      {line('supplier', suppliers, 'Supplier', 'Suppliers')}
      {line('customer', customers, 'Customer', 'Customers')}
    </span>
  );
}

/**
 * One holding: its tile (with a beacon when an alert reaches it), name, and a meter of its weight. While it is analysed,
 * the meter scans; when done, it fills to the holding's share, and a tick lights briefly. A new weight counts to its
 * value and the meter glides with it.
 */
export function HoldingRow({
  holding,
  colour,
  beacon,
  links,
  share,
  current,
  index,
  onPeek,
  onLeave,
  onChoose,
}: {
  holding: Holding;
  colour?: string;
  beacon: AlertEvent['headline']['assessment']['severity'] | null;
  links: ReturnType<typeof connectionsOf> | null;
  share: number;
  current: boolean;
  index: number;
  onPeek: () => void;
  onLeave: () => void;
  onChoose: () => void;
}) {
  const before = useRef(holding.status);
  const [justDone, setJustDone] = useState(false);
  useEffect(() => {
    const was = before.current;
    before.current = holding.status;
    if (was !== 'DONE' && holding.status === 'DONE') {
      setJustDone(true);
      const timer = window.setTimeout(() => setJustDone(false), 1500);
      return () => window.clearTimeout(timer);
    }
  }, [holding.status]);
  const working = holding.status === 'ANALYSING';
  const done = holding.status === 'DONE';
  const subKey = done && links ? 'links' : holding.status;

  return (
    <motion.button
      layout="position"
      type="button"
      role="listitem"
      className={`h-row${working ? ' working' : ''}${justDone ? ' just-done' : ''}`}
      aria-current={current}
      aria-label={`${holding.companyDisplayName ?? holding.ticker} (${holding.ticker})`}
      style={{ '--chain': colour ?? 'var(--faint)' } as CSSProperties}
      initial={{ opacity: 0, x: -10 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -24, height: 0, minHeight: 0, paddingTop: 0, paddingBottom: 0, transition: { duration: 0.32, ease: [0.22, 1, 0.36, 1] } }}
      transition={{ delay: index * 0.022, type: 'spring', stiffness: 380, damping: 34 }}
      onPointerEnter={onPeek}
      onPointerLeave={onLeave}
      onFocus={onPeek}
      onBlur={onLeave}
      onClick={onChoose}
    >
      <Tile name={holding.companyName ?? holding.ticker} ticker={holding.ticker} size={38} holding colour={colour} beacon={beacon} />
      <span className="row-text">
        <span className="h-name">{holding.companyDisplayName ?? holding.ticker}</span>
        <span className="h-sub">
          <span className="h-ticker">{holding.ticker}</span>
          {/* While it is not analysed, its status; once it is, its suppliers and customers take the place beside its
              weight. */}
          <span className="h-sub-swap">
            <AnimatePresence mode="popLayout" initial={false}>
              {subKey !== 'links' && (
                <motion.span
                  key={subKey}
                  className="h-sub-item"
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                >
                  <State status={holding.status} />
                </motion.span>
              )}
            </AnimatePresence>
          </span>
        </span>
      </span>
      <span className="h-links">
        <AnimatePresence initial={false}>
          {subKey === 'links' && links && (
            <motion.span
              key="links"
              className="h-sub-item"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ type: 'spring', stiffness: 420, damping: 34 }}
            >
              <LinkLines suppliers={links.suppliers.length} customers={links.customers.length} />
            </motion.span>
          )}
        </AnimatePresence>
      </span>
      <span className="h-end">
        <span className="h-weight">
          {holding.weight === null ? '—' : <Count value={holding.weight} decimals={Number.isInteger(holding.weight) ? 0 : 1} suffix="%" />}
          <AnimatePresence>
            {justDone && (
              <motion.span
                className="h-tick"
                initial={{ scale: 0, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                exit={{ scale: 0.6, opacity: 0 }}
                transition={{ type: 'spring', stiffness: 600, damping: 22 }}
              >
                <Check size={12} strokeWidth={3} />
              </motion.span>
            )}
          </AnimatePresence>
        </span>
        <span className={`h-meter${working ? ' working' : ''}`} aria-hidden="true">
          <span className="scan" />
          <span className="fill" style={{ width: done ? `${Math.max(6, share * 100)}%` : '0%' }} />
        </span>
      </span>
    </motion.button>
  );
}

/** Add Holding: a ticker and an optional weight. The holding is analysed straight away. */
function AddForm({ onAdd }: { onAdd: (ticker: string, weight: string) => Promise<string | null> }) {
  const [ticker, setTicker] = useState('');
  const [weight, setWeight] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSending(true);
    setError(null);
    const refused = await onAdd(ticker, weight);
    setSending(false);
    setError(refused);
    if (!refused) {
      setTicker('');
      setWeight('');
    }
  }

  return (
    <form className="add-form" onSubmit={submit}>
      <div className="field">
        <label htmlFor="new-ticker">Ticker</label>
        <input id="new-ticker" className="input" value={ticker} maxLength={20} placeholder="MSFT" autoComplete="off" onChange={(event) => setTicker(event.target.value)} />
      </div>
      <div className="field">
        <label htmlFor="new-weight">Weight (%)</label>
        <input id="new-weight" className="input" value={weight} inputMode="decimal" placeholder="Optional" autoComplete="off" onChange={(event) => setWeight(event.target.value)} />
      </div>
      <button type="submit" className="btn primary" disabled={sending || !ticker.trim()}>
        {sending ? <LoaderCircle size={15} className="spin" /> : null}
        {sending ? 'Adding' : 'Add Holding'}
      </button>
      {error && <p className="add-error" role="alert">{error}</p>}
    </form>
  );
}

let measuring: CanvasRenderingContext2D | null = null;

/** How wide a ticker is drawn in a holding's row (600 weight, 13px), so every row's ticker column lines up. */
function tickerTextWidth(ticker: string): number {
  measuring ??= document.createElement('canvas').getContext('2d');
  if (!measuring) return ticker.length * 8.5;
  measuring.font = '600 13px "IBM Plex Sans Variable", system-ui, sans-serif';
  return measuring.measureText(ticker).width;
}
