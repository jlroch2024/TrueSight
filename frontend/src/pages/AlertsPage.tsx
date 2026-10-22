import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { type Alert, type AlertStatus, type AlertsResponse, type EventType, alertApi } from '../api/alerts';
import { ApiError } from '../api/client';
import { type Portfolio, portfolioApi } from '../api/portfolios';
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

// How long "Alert Dismissed. Undo" stays after a Dismiss.
const UNDO_MS = 8_000;

type Filter = 'OPEN' | 'DISMISSED' | 'ALL';

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong.';
}

function AlertCard({
  alert,
  weighted,
  holdingCount,
  busy,
  onStatus,
}: {
  alert: Alert;
  weighted: boolean;
  holdingCount: number;
  busy: boolean;
  onStatus: (status: AlertStatus) => void;
}) {
  const severity = alert.assessment.severity;
  const dismissed = alert.status === 'DISMISSED';
  return (
    <li className={`card alert-card${dismissed ? ' dismissed' : ''}`}>
      <div className="alert-card-heading">
        <div>
          <p className="news-meta">
            <span>{alert.article.source ? `${alert.article.source} · ` : ''}{formatDate(alert.article.publishedAt)}</span>
          </p>
          <h2>{alert.article.title}</h2>
        </div>
        <span className="alert-tags">
          {dismissed && <span className="news-tag not-event">Dismissed</span>}
          <span className={`news-tag severity-${severity.toLowerCase()}`}>{SEVERITY_LABELS[severity]}</span>
        </span>
      </div>

      <div className="alert-facts">
        <p><span className="eyebrow">Event Type</span>{EVENT_TYPE_LABELS[alert.assessment.eventType]}</p>
        <p>
          <span className="eyebrow">Exposure</span>
          {weighted ? `${alert.exposure}%` : `${alert.exposure} of ${holdingCount} Holdings`}
        </p>
        <p><span className="eyebrow">Confidence</span>{CONFIDENCE_LABELS[alert.assessment.confidence]}</p>
        <p><span className="eyebrow">Status</span>{STATUS_LABELS[alert.status]}</p>
      </div>

      {alert.assessment.summary && <p className="alert-summary">{alert.assessment.summary}</p>}
      <p>{alert.assessment.reason}</p>
      <p className="muted small">Based on the Headline Only</p>

      <div className="alert-holdings">
        <h3>Holdings Reached</h3>
        <ul className={weighted ? undefined : 'unweighted'}>
          {alert.holdings.map((holding) => (
            <li key={holding.holdingId}>
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
        <button type="button" onClick={() => openInNewTab(alert.article.url)}>Open Article</button>
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
  const [busyAlert, setBusyAlert] = useState<number | null>(null);
  // The alert just dismissed, while its Undo is offered.
  const [undoAlert, setUndoAlert] = useState<number | null>(null);

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

  // Every alert, dismissed ones too, loaded once; the filter only chooses which to show.
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
    if (undoAlert === null) return;
    const timer = setTimeout(() => setUndoAlert(null), UNDO_MS);
    return () => clearTimeout(timer);
  }, [undoAlert]);

  async function changeStatus(alertId: number, status: AlertStatus) {
    setBusyAlert(alertId);
    setError(null);
    try {
      const updated = await alertApi.setStatus(portfolioId, alertId, status);
      setResponse((current) => current && ({
        ...current,
        alerts: current.alerts.map((item) => item.id === updated.id ? updated : item),
      }));
      setUndoAlert(status === 'DISMISSED' ? alertId : null);
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusyAlert(null);
    }
  }

  if (error && response === undefined) return <p className="error">{error}</p>;
  if (portfolio === undefined || response === undefined) return <p className="muted">Loading…</p>;
  if (portfolio === null) return <p className="error">Portfolio not found.</p>;

  const openCount = response.alerts.filter((alert) => alert.status !== 'DISMISSED').length;
  const dismissedCount = response.alerts.length - openCount;
  const shown = response.alerts.filter((alert) =>
    filter === 'ALL' || (filter === 'DISMISSED') === (alert.status === 'DISMISSED'));

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

      {undoAlert !== null && (
        <p role="status" className="alert-undo">
          Alert Dismissed.{' '}
          <button type="button" className="text-button" onClick={() => { void changeStatus(undoAlert, 'NEW'); }}>
            Undo
          </button>
        </p>
      )}
      {error && <p role="alert" className="error">{error}</p>}
      {shown.length === 0 ? (
        <p className="muted">
          {filter === 'DISMISSED'
            ? 'No alerts have been dismissed.'
            : portfolio.newsCheckedAt
              ? 'No events found in the last 7 days.'
              : 'News has not been checked yet.'}
        </p>
      ) : (
        <ul className="alert-list">
          {shown.map((alert) => (
            <AlertCard
              key={alert.id}
              alert={alert}
              weighted={response.weighted}
              holdingCount={response.holdingCount}
              busy={busyAlert === alert.id}
              onStatus={(status) => { void changeStatus(alert.id, status); }}
            />
          ))}
        </ul>
      )}
    </section>
  );
}
