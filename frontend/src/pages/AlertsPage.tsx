import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { type AlertEvent, type AlertStatus, type AlertsResponse, type EventType, alertApi } from '../api/alerts';
import { ApiError } from '../api/client';
import type { Portfolio } from '../api/portfolios';
import { portfolioApi } from '../api/portfolios';
import { CheckNewsButton } from '../components/CheckNewsButton';
import { RadioGroup } from '../components/RadioGroup';
import { formatDate, openInNewTab } from '../format';
import { displayName } from './supplyChain';

const SEVERITY_LABELS = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' } as const;
const CONFIDENCE_LABELS = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' } as const;
const STATUS_LABELS: Record<AlertStatus, string> = { NEW: 'Open', DISMISSED: 'Dismissed' };
const EVENT_TYPE_LABELS: Record<EventType, string> = {
  PRODUCTION_STOP: 'Production Stop',
  SUPPLY_SHORTAGE: 'Supply Shortage',
  LABOUR_ACTION: 'Labour Action',
  NATURAL_DISASTER: 'Natural Disaster',
  TRADE_RESTRICTION: 'Trade Restriction',
  FINANCIAL_DISTRESS: 'Financial Distress',
  LEGAL_OR_REGULATORY: 'Legal or Regulatory',
  CYBER_ATTACK: 'Cyber Attack',
  OTHER_DISRUPTION: 'Other Disruption',
};

const UNDO_MS = 8_000;
type Filter = 'OPEN' | 'DISMISSED' | 'ALL';
type UndoEvent = { portfolioId: string; alertIds: number[] };

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong.';
}

function AlertCard({
  event,
  weighted,
  holdingCount,
  busy,
  onStatus,
}: {
  event: AlertEvent;
  weighted: boolean;
  holdingCount: number;
  busy: boolean;
  onStatus: (status: AlertStatus) => void;
}) {
  const { article, assessment } = event.headline;
  const dismissed = event.status === 'DISMISSED';
  return (
    <li className={`card alert-card${dismissed ? ' dismissed' : ''}`}>
      <div className="alert-card-heading">
        <div>
          <p className="news-meta">
            <span>{article.source ? `${article.source} · ` : ''}{formatDate(article.publishedAt)}</span>
          </p>
          <h2>{article.title}</h2>
        </div>
        <span className="alert-tags">
          {dismissed && <span className="news-tag not-event">Dismissed</span>}
          <span className={`news-tag severity-${assessment.severity.toLowerCase()}`}>
            {SEVERITY_LABELS[assessment.severity]}
          </span>
        </span>
      </div>

      <div className="alert-facts">
        <p><span className="eyebrow">Event Type</span>{EVENT_TYPE_LABELS[assessment.eventType]}</p>
        <p>
          <span className="eyebrow">Exposure</span>
          {weighted ? `${event.exposure}%` : `${event.exposure} of ${holdingCount} Holdings`}
        </p>
        <p><span className="eyebrow">Confidence</span>{CONFIDENCE_LABELS[assessment.confidence]}</p>
        <p><span className="eyebrow">Status</span>{STATUS_LABELS[event.status]}</p>
      </div>

      {assessment.summary && <p className="alert-summary">{assessment.summary}</p>}
      <p>{assessment.reason}</p>
      <p className="muted small">Based on the Headline Only</p>

      {event.sources.length > 1 && (
        <div className="alert-sources">
          <h3>Also Reported By</h3>
          <ul>
            {event.sources.map((source, index) => (
              <li key={`${source.url}-${index}`}>
                <span>{source.source ?? 'Unknown Publisher'} · {formatDate(source.publishedAt)}</span>
                <span>{source.title}</span>
                <button type="button" className="text-button" onClick={() => openInNewTab(source.url)}>
                  Open Article
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="alert-holdings">
        <h3>Holdings Reached</h3>
        <ul className={weighted ? undefined : 'unweighted'}>
          {event.holdings.map((holding) => (
            <li key={`${holding.holdingId}-${holding.relationshipId ?? 'article'}`}>
              <strong>{holding.ticker}</strong>
              {weighted && <span>{holding.weight === null ? 'Weight Not Provided' : `${holding.weight}%`}</span>}
              <span>
                {holding.supplierName && holding.customerName
                  ? `${displayName(holding.supplierName)} supplies ${displayName(holding.customerName)}`
                  : 'Named in the Article'}
              </span>
              {holding.reviewStatus === 'UNREVIEWED' && (
                <span className="muted small">Not Yet Reviewed by an Analyst</span>
              )}
            </li>
          ))}
        </ul>
      </div>

      <div className="actions">
        <button type="button" onClick={() => openInNewTab(article.url)}>Open Article</button>
        {dismissed ? (
          <button type="button" className="secondary" disabled={busy} onClick={() => onStatus('NEW')}>
            Restore
          </button>
        ) : (
          <button type="button" className="danger" disabled={busy} onClick={() => onStatus('DISMISSED')}>
            Dismiss
          </button>
        )}
      </div>
    </li>
  );
}

export function AlertsPage() {
  const { portfolioId = '' } = useParams();
  const [portfolio, setPortfolio] = useState<Portfolio | null | undefined>(undefined);
  const [response, setResponse] = useState<AlertsResponse | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<Filter>('OPEN');
  const [busyAlertIds, setBusyAlertIds] = useState<number[]>([]);
  const [undoEvent, setUndoEvent] = useState<UndoEvent | null>(null);

  useEffect(() => setUndoEvent(null), [portfolioId]);

  useEffect(() => {
    let active = true;
    setPortfolio(undefined);
    portfolioApi.list()
      .then((all) => {
        if (active) setPortfolio(all.find((item) => String(item.id) === portfolioId) ?? null);
      })
      .catch((cause: unknown) => {
        if (active) setError(messageOf(cause));
      });
    return () => { active = false; };
  }, [portfolioId]);

  const loadAlerts = useCallback(async () => {
    setError(null);
    try {
      setResponse(await alertApi.list(portfolioId, true));
    } catch (cause) {
      setError(messageOf(cause));
    }
  }, [portfolioId]);

  const newsCheckedAt = portfolio?.newsCheckedAt ?? null;
  const checking = portfolio?.newsStatus === 'CHECKING';
  useEffect(() => {
    if (checking) return;
    setResponse(undefined);
    void loadAlerts();
  }, [checking, newsCheckedAt, loadAlerts]);

  useEffect(() => {
    if (undoEvent === null) return;
    const timer = setTimeout(() => setUndoEvent(null), UNDO_MS);
    return () => clearTimeout(timer);
  }, [undoEvent]);

  async function changeStatus(event: AlertEvent, status: AlertStatus) {
    setBusyAlertIds(event.alertIds);
    setError(null);
    try {
      const updated = await alertApi.setEventStatus(portfolioId, event.alertIds, status);
      setResponse((current) => current && ({
        ...current,
        alerts: current.alerts
          .map((item) => item.alertIds.some((id) => updated.alertIds.includes(id)) ? updated : item),
      }));
      setUndoEvent(status === 'DISMISSED' ? { portfolioId, alertIds: event.alertIds } : null);
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusyAlertIds([]);
    }
  }

  if (error && response === undefined) return <p className="error">{error}</p>;
  if (portfolio === undefined || response === undefined) return <p className="muted">Loading…</p>;
  if (portfolio === null) return <p className="error">Portfolio not found.</p>;

  const openCount = response.alerts.filter((event) => event.status !== 'DISMISSED').length;
  const dismissedCount = response.alerts.length - openCount;
  const shown = response.alerts.filter((event) =>
    filter === 'ALL' || (filter === 'DISMISSED') === (event.status === 'DISMISSED'));
  const undoBusy = undoEvent !== null && undoEvent.alertIds.some((id) => busyAlertIds.includes(id));

  return (
    <section>
      <div className="page-heading">
        <div>
          <h1>Alerts</h1>
          <p className="muted">Supply-chain events that may affect this portfolio.</p>
        </div>
        <CheckNewsButton portfolio={portfolio} onChange={setPortfolio} />
      </div>

      <div className="alerts-toolbar">
        <RadioGroup
          name="alert-filter"
          label="Show"
          value={filter}
          options={[
            { value: 'OPEN', label: `Open (${openCount})` },
            { value: 'DISMISSED', label: `Dismissed (${dismissedCount})` },
            { value: 'ALL', label: `All (${response.alerts.length})` },
          ]}
          onChange={(next) => setFilter(next as Filter)}
        />
      </div>

      {undoEvent?.portfolioId === portfolioId && (
        <p role="status" className="alert-undo">
          Alert Dismissed.{' '}
          <button type="button" className="text-button" disabled={undoBusy} onClick={() => {
            const event = response.alerts.find((item) =>
              item.alertIds.some((id) => undoEvent.alertIds.includes(id)));
            if (event) void changeStatus(event, 'NEW');
          }}>
            Undo
          </button>
        </p>
      )}
      {error && <p role="alert" className="error">{error}</p>}
      {shown.length === 0 ? (
        <p className="muted">
          {filter === 'DISMISSED'
            ? 'No alerts have been dismissed.'
            : filter === 'OPEN' && dismissedCount > 0
              ? 'No open alerts. Dismissed alerts are under Dismissed.'
              : portfolio.newsCheckedAt
                ? 'No events found in the last 7 days.'
                : 'News has not been checked yet.'}
        </p>
      ) : (
        <ul className="alert-list">
          {shown.map((event) => (
            <AlertCard
              key={event.alertIds.join('-')}
              event={event}
              weighted={response.weighted}
              holdingCount={response.holdingCount}
              busy={event.alertIds.some((id) => busyAlertIds.includes(id))}
              onStatus={(status) => { void changeStatus(event, status); }}
            />
          ))}
        </ul>
      )}
    </section>
  );
}
