import { type ReactNode, useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { type Portfolio, portfolioApi } from '../api/portfolios';
import { formatDateTime } from '../format';

type Props = {
  portfolio: Portfolio;
  onChange: (portfolio: Portfolio) => void;
  // How many open alerts the portfolio has, shown after the time of the last check ("· 3 Open Alerts"); leave out to
  // show only the time.
  openAlerts?: number | null;
  // Set when the alerts could not be loaded: the line then ends "· Alerts Could Not Be Counted".
  alertsCouldNotBeCounted?: boolean;
  // Shows a See News button after a check, which opens the news as alerts.
  onSeeNews?: () => void;
  // Buttons placed first in the same row, such as the Portfolio page's Analyse, so they all line up.
  leading?: ReactNode;
  // "main": a full-size main button. "compact": a small outlined button with the status beside it.
  variant?: 'main' | 'compact';
};

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong.';
}

export function CheckNewsButton({
  portfolio,
  onChange,
  openAlerts,
  alertsCouldNotBeCounted = false,
  onSeeNews,
  leading,
  variant = 'main',
}: Props) {
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (portfolio.newsStatus !== 'CHECKING') return;
    const timer = window.setInterval(() => {
      portfolioApi.list()
        .then((all) => {
          const updated = all.find((item) => item.id === portfolio.id);
          if (updated) onChange(updated);
        })
        .catch((cause: unknown) => setError(messageOf(cause)));
    }, 2000);
    return () => window.clearInterval(timer);
  }, [portfolio.id, portfolio.newsStatus, onChange]);

  async function start() {
    setStarting(true);
    setError(null);
    try {
      await portfolioApi.newsCheck(portfolio.id);
      onChange({ ...portfolio, newsStatus: 'CHECKING', newsStatusReason: null });
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setStarting(false);
    }
  }

  const checking = portfolio.newsStatus === 'CHECKING';
  return (
    <div className={`news-check ${variant}`}>
      <div className="news-check-buttons">
        {leading}
        <button
          type="button"
          className={variant === 'compact' ? 'secondary small-button' : undefined}
          onClick={start}
          disabled={starting || checking}
        >
          {starting ? 'Starting…' : checking ? 'Checking News…' : 'Check News'}
        </button>
        {!checking && portfolio.newsCheckedAt && onSeeNews && (
          <button type="button" className="secondary" onClick={onSeeNews}>
            See News
          </button>
        )}
      </div>
      {!checking && portfolio.newsStatus === 'FAILED' && portfolio.newsStatusReason && (
        <span role="alert" className="error small">{portfolio.newsStatusReason}</span>
      )}
      {!checking && portfolio.newsStatus !== 'FAILED' && portfolio.newsCheckedAt && (
        <span className="muted small">
          News Checked {formatDateTime(portfolio.newsCheckedAt)}
          {alertsCouldNotBeCounted
            ? ' · Alerts Could Not Be Counted'
            : typeof openAlerts === 'number' && ` · ${openAlerts} Open ${openAlerts === 1 ? 'Alert' : 'Alerts'}`}
        </span>
      )}
      {!checking && portfolio.newsStatus !== 'FAILED' && portfolio.newsStatusReason && (
        <span className="muted small">{portfolio.newsStatusReason}</span>
      )}
      {error && <span role="alert" className="error small">{error}</span>}
    </div>
  );
}
