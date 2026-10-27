// The drawer's Alerts tab: when news was last checked, then Open, Closed and All. Each card shows its event mark,
// severity, type, age, headline, the holdings it reaches as their own tiles (a dashed edge for one reached through a
// supplier's supplier) and how much of the portfolio that is. Close Alert and Restore move a card between Open and
// Closed (the stored status stays Dismissed; only the words change), each with Undo. Choosing a card shows the alert in
// the Inspector and spotlights it on the graph.
import { AnimatePresence, motion } from 'motion/react';
import { Bell, BellOff, LoaderCircle, Radar } from 'lucide-react';
import type { AlertEvent } from '../../api/alerts';
import type { Portfolio } from '../../api/portfolios';
import { EVENT_TYPE_LABELS, SEVERITY_LABELS, formatAge } from '../../format';
import { EventMark, SeverityBars } from '../EventMark';
import { Segmented } from '../Segmented';
import { Tile } from '../Tile';
import { type WorkspaceView, alertKey, byUrgency, exposureOf } from './view';

export type AlertFilter = 'open' | 'closed' | 'all';

export function AlertsTab({
  view,
  portfolio,
  filter,
  chosenKey,
  busyKey,
  checking,
  onFilter,
  onChoose,
  onClose,
  onRestore,
  onCheck,
}: {
  view: WorkspaceView;
  portfolio: Portfolio;
  filter: AlertFilter;
  chosenKey: string | null;
  // The alert whose change is being saved.
  busyKey: string | null;
  checking: boolean;
  onFilter: (filter: AlertFilter) => void;
  onChoose: (alert: AlertEvent) => void;
  onClose: (alert: AlertEvent) => void;
  onRestore: (alert: AlertEvent) => void;
  onCheck: () => void;
}) {
  const { alerts } = view;
  const open = alerts.filter((alert) => alert.status !== 'DISMISSED').length;
  const closed = alerts.length - open;
  const shown = alerts
    .filter((alert) => (filter === 'all' ? true : filter === 'open' ? alert.status !== 'DISMISSED' : alert.status === 'DISMISSED'))
    .sort(byUrgency);
  const checkedAt = portfolio.newsCheckedAt;

  return (
    <>
      <div className="toolbar">
        {checking ? (
          <span className="status-item">
            <LoaderCircle size={14} className="spin" />
            Checking News
          </span>
        ) : checkedAt ? (
          <span className="status-item">
            <span className="live-dot" />
            News Checked {formatAge(checkedAt)}
          </span>
        ) : (
          <span className="status-item">News Not Checked Yet</span>
        )}
      </div>
      {!checking && portfolio.newsStatus === 'FAILED' && portfolio.newsStatusReason && (
        <p role="alert" className="error tab-note">{portfolio.newsStatusReason}</p>
      )}
      {!checking && portfolio.newsStatus !== 'FAILED' && portfolio.newsStatusReason && (
        <p className="section-note">{portfolio.newsStatusReason}</p>
      )}
      <div className="tab-filter">
        <Segmented
          label="Show"
          value={filter}
          onChange={onFilter}
          options={[
            { value: 'open', name: `Open (${open})`, label: <>Open <span className={`count${open ? ' alarm' : ''}`}>{open}</span></> },
            { value: 'closed', name: `Closed (${closed})`, label: <>Closed <span className="count">{closed}</span></> },
            { value: 'all', name: `All (${alerts.length})`, label: <>All <span className="count">{alerts.length}</span></> },
          ]}
        />
      </div>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div
          key={filter}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
        >
          {shown.length === 0 ? (
            <div className="empty">
              <Bell size={26} />
              {checkedAt === null && !checking ? (
                <>
                  <span>News Not Checked Yet</span>
                  <button type="button" className="btn small primary" disabled={view.holdings.length === 0} onClick={onCheck}>
                    <Radar size={15} /> Check News
                  </button>
                </>
              ) : (
                <span>{filter === 'closed' ? 'No Closed Alerts' : 'No Open Alerts'}</span>
              )}
            </div>
          ) : (
            <div className="a-list" role="list" aria-label="Alerts">
              <AnimatePresence initial={false}>
                {shown.map((alert) => (
                  <AlertCard
                    key={alertKey(alert)}
                    alert={alert}
                    view={view}
                    current={chosenKey === alertKey(alert)}
                    busy={busyKey === alertKey(alert)}
                    onChoose={onChoose}
                    onClose={onClose}
                    onRestore={onRestore}
                  />
                ))}
              </AnimatePresence>
            </div>
          )}
        </motion.div>
      </AnimatePresence>
    </>
  );
}

/**
 * One alert, built for the eye to take in at a glance: what kind of event and how serious (its mark and bars, in its
 * colour), the headline, the holdings it reaches as their own tiles, and, standing out on the right, how much of the
 * portfolio it reaches, with a cell for each holding.
 */
function AlertCard({
  alert,
  view,
  current,
  busy,
  onChoose,
  onClose,
  onRestore,
}: {
  alert: AlertEvent;
  view: WorkspaceView;
  current: boolean;
  busy: boolean;
  onChoose: (alert: AlertEvent) => void;
  onClose: (alert: AlertEvent) => void;
  onRestore: (alert: AlertEvent) => void;
}) {
  const { article, assessment } = alert.headline;
  const closed = alert.status === 'DISMISSED';
  const reachedIds = new Set(alert.holdings.map((reached) => reached.holdingId));
  const exposure = exposureOf(alert, view);
  return (
    <motion.div
      layout
      role="listitem"
      aria-label={article.title}
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -28, height: 0, marginBottom: -8, transition: { duration: 0.28, ease: [0.22, 1, 0.36, 1] } }}
      transition={{ type: 'spring', stiffness: 400, damping: 36 }}
    >
      <div
        role="button"
        tabIndex={0}
        className={`a-card${closed ? ' dismissed' : ''}`}
        data-severity={assessment.severity}
        aria-current={current}
        aria-label={article.title}
        onClick={() => onChoose(alert)}
        onKeyDown={(event) => {
          if (event.target !== event.currentTarget || (event.key !== 'Enter' && event.key !== ' ')) return;
          event.preventDefault();
          onChoose(alert);
        }}
      >
        <div className="a-main">
          <EventMark type={assessment.eventType} severity={assessment.severity} size={38} />
          <div className="row-text">
            <div className="a-top">
              <SeverityBars severity={assessment.severity} />
              <span className="a-sev">{SEVERITY_LABELS[assessment.severity]}</span>
              <span className="a-type">{EVENT_TYPE_LABELS[assessment.eventType]}</span>
              <span className="a-meta num">· {formatAge(article.publishedAt)}</span>
            </div>
            <p className="a-title">{article.title}</p>
          </div>
          {closed ? (
            <button
              type="button"
              className="icon-btn small"
              aria-label="Restore"
              title="Restore"
              disabled={busy}
              onClick={(event) => {
                event.stopPropagation();
                onRestore(alert);
              }}
            >
              <Bell size={15} />
            </button>
          ) : (
            <button
              type="button"
              className="icon-btn small"
              aria-label="Close Alert"
              title="Close Alert"
              disabled={busy}
              onClick={(event) => {
                event.stopPropagation();
                onClose(alert);
              }}
            >
              <BellOff size={15} />
            </button>
          )}
        </div>
        <div className="a-foot">
          <span className="a-reach">
            {alert.holdings.map((reached) => {
              const holding = view.holdings.find((item) => item.id === reached.holdingId);
              const nodeId = holding ? view.nodeIdOf(holding) : '';
              return (
                <span
                  key={`${reached.holdingId}-${reached.relationshipId}-${reached.secondRelationshipId}`}
                  className={`reach-tile${reached.indirect ? ' indirect' : ''}`}
                  title={`${reached.ticker} · ${reached.indirect ? 'Reached Through a Supplier’s Supplier' : 'Reached Directly'}`}
                >
                  <Tile name={holding?.companyName ?? reached.ticker} ticker={reached.ticker} size={28} holding colour={view.colourOf.get(nodeId)} />
                </span>
              );
            })}
          </span>
          <span className="expo-mini" title={`${exposure.value}${exposure.suffix} ${exposure.label}`}>
            <span className="expo-cells" aria-hidden="true">
              {view.holdings.map((holding) => (
                <i key={holding.id} className={reachedIds.has(holding.id) ? 'on' : ''} />
              ))}
            </span>
            <strong className="num">
              {exposure.value}
              {exposure.suffix && <small>{exposure.suffix}</small>}
            </strong>
            <span className="expo-label">{exposure.label}</span>
          </span>
        </div>
      </div>
    </motion.div>
  );
}
