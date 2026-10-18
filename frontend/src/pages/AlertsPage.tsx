import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { type Alert, type AlertStatus, alertApi } from '../api/alerts';
import { ApiError } from '../api/client';
import { type NewsArticle, newsApi } from '../api/news';
import { type Portfolio, portfolioApi } from '../api/portfolios';
import { CheckNewsButton } from '../components/CheckNewsButton';
import { Checkbox } from '../components/Checkbox';
import { formatDate, openInNewTab } from '../format';

const SEVERITY_LABELS = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' } as const;

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong.';
}

function words(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function AlertCard({
  alert,
  busy,
  onStatus,
}: {
  alert: Alert;
  busy: boolean;
  onStatus: (status: AlertStatus) => void;
}) {
  const severity = alert.assessment.severity;
  return (
    <li className="card alert-card">
      <div className="alert-card-heading">
        <div>
          <p className="news-meta">
            <span>{alert.article.source ? `${alert.article.source} · ` : ''}{formatDate(alert.article.publishedAt)}</span>
          </p>
          <h2>{alert.article.title}</h2>
        </div>
        <span className={`news-tag severity-${severity.toLowerCase()}`}>{SEVERITY_LABELS[severity]}</span>
      </div>

      <div className="alert-facts">
        <p><span className="eyebrow">Event Type</span>{words(alert.assessment.eventType)}</p>
        <p><span className="eyebrow">Exposure</span>{alert.exposure}%</p>
        <p><span className="eyebrow">Confidence</span>{words(alert.assessment.confidence)}</p>
        <p><span className="eyebrow">Status</span>{words(alert.status)}</p>
      </div>

      {alert.assessment.summary && <p className="alert-summary">{alert.assessment.summary}</p>}
      <p>{alert.assessment.reason}</p>
      <p className="muted small">Based on the headline only</p>

      <div className="alert-holdings">
        <h3>Holdings Reached</h3>
        <ul>
          {alert.holdings.map((holding) => (
            <li key={holding.holdingId}>
              <strong>{holding.ticker}</strong>
              <span>{holding.weight === null ? 'Weight not provided' : `${holding.weight}%`}</span>
              <span>{holding.link ?? 'The event is about this company'}</span>
              {holding.reviewStatus === 'UNREVIEWED' && (
                <span className="muted small">Not yet reviewed by an analyst</span>
              )}
            </li>
          ))}
        </ul>
      </div>

      <div className="actions">
        <button type="button" onClick={() => openInNewTab(alert.article.url)}>Open Article</button>
        {alert.status === 'DISMISSED' ? (
          <button type="button" className="secondary" disabled={busy} onClick={() => onStatus('NEW')}>
            Restore
          </button>
        ) : (
          <>
            {alert.status !== 'ACKNOWLEDGED' && (
              <button type="button" className="secondary" disabled={busy} onClick={() => onStatus('ACKNOWLEDGED')}>
                Acknowledge
              </button>
            )}
            <button type="button" className="danger" disabled={busy} onClick={() => onStatus('DISMISSED')}>
              Dismiss
            </button>
          </>
        )}
      </div>
    </li>
  );
}

function FilteredNews({ articles }: { articles: NewsArticle[] }) {
  return (
    <ul className="filtered-news-list">
      {articles.map((article) => (
        <li key={article.id} className="card">
          <p className="news-meta">
            {article.source ? `${article.source} · ` : ''}{formatDate(article.publishedAt)}
            <span>{article.company.name}</span>
          </p>
          <h3>{article.title}</h3>
          <p>{article.assessment?.reason}</p>
          <button type="button" className="text-button" onClick={() => openInNewTab(article.url)}>
            Open Article
          </button>
        </li>
      ))}
    </ul>
  );
}

export function AlertsPage() {
  const { portfolioId = '' } = useParams();
  const [portfolio, setPortfolio] = useState<Portfolio | null | undefined>(undefined);
  const [response, setResponse] = useState<Awaited<ReturnType<typeof alertApi.list>> | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [includeDismissed, setIncludeDismissed] = useState(false);
  const [busyAlert, setBusyAlert] = useState<number | null>(null);
  const [showNoise, setShowNoise] = useState(false);
  const [noise, setNoise] = useState<NewsArticle[] | undefined>(undefined);
  const [noiseError, setNoiseError] = useState<string | null>(null);

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
      setResponse(await alertApi.list(portfolioId, includeDismissed));
    } catch (cause) {
      setError(messageOf(cause));
    }
  }, [portfolioId, includeDismissed]);

  const newsCheckedAt = portfolio?.newsCheckedAt ?? null;
  const checking = portfolio?.newsStatus === 'CHECKING';
  useEffect(() => {
    if (checking) return;
    setResponse(undefined);
    void loadAlerts();
  }, [checking, newsCheckedAt, loadAlerts]);

  async function changeStatus(alert: Alert, status: AlertStatus) {
    setBusyAlert(alert.id);
    setError(null);
    try {
      const updated = await alertApi.setStatus(portfolioId, alert.id, status);
      setResponse((current) => current && ({
        ...current,
        alerts: current.alerts
          .map((item) => item.id === updated.id ? updated : item)
          .filter((item) => includeDismissed || item.status !== 'DISMISSED'),
      }));
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusyAlert(null);
    }
  }

  async function toggleNoise() {
    if (showNoise) {
      setShowNoise(false);
      return;
    }
    setShowNoise(true);
    if (noise !== undefined) return;
    setNoiseError(null);
    try {
      setNoise(await newsApi.list(portfolioId, true));
    } catch (cause) {
      setNoiseError(messageOf(cause));
    }
  }

  if (error && response === undefined) return <p className="error">{error}</p>;
  if (portfolio === undefined || response === undefined) return <p className="muted">Loading…</p>;
  if (portfolio === null) return <p className="error">Portfolio not found.</p>;

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
        <Checkbox
          id="show-dismissed"
          label="Show Dismissed"
          checked={includeDismissed}
          onChange={setIncludeDismissed}
        />
      </div>

      {error && <p role="alert" className="error">{error}</p>}
      {response.alerts.length === 0 ? (
        <p className="muted">
          {portfolio.newsCheckedAt
            ? 'No events found in the last 7 days.'
            : 'News has not been checked yet.'}
        </p>
      ) : (
        <ul className="alert-list">
          {response.alerts.map((alert) => (
            <AlertCard
              key={alert.id}
              alert={alert}
              busy={busyAlert === alert.id}
              onStatus={(status) => { void changeStatus(alert, status); }}
            />
          ))}
        </ul>
      )}

      <section className="filtered-news">
        <p>
          Checked {response.articlesChecked} {response.articlesChecked === 1 ? 'article' : 'articles'}.{' '}
          {response.noiseCount} were not supply-chain events.
        </p>
        <button type="button" className="secondary" onClick={() => { void toggleNoise(); }}>
          {showNoise ? 'Hide Filtered News' : 'Show Filtered News'}
        </button>
        {showNoise && (
          noiseError ? <p role="alert" className="error">{noiseError}</p>
            : noise === undefined ? <p className="muted">Loading…</p>
              : <FilteredNews articles={noise} />
        )}
      </section>
    </section>
  );
}
