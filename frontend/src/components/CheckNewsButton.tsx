import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { type Portfolio, portfolioApi } from '../api/portfolios';
import { formatDateTime } from '../format';

type Props = {
  portfolio: Portfolio;
  onChange: (portfolio: Portfolio) => void;
};

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong.';
}

export function CheckNewsButton({ portfolio, onChange }: Props) {
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
    <div className="news-check">
      <button type="button" onClick={start} disabled={starting || checking}>
        {starting ? 'Starting…' : checking ? 'Checking news…' : 'Check News'}
      </button>
      {!checking && portfolio.newsStatus === 'FAILED' && portfolio.newsStatusReason && (
        <span role="alert" className="error small">{portfolio.newsStatusReason}</span>
      )}
      {!checking && portfolio.newsStatus !== 'FAILED' && portfolio.newsCheckedAt && (
        <span className="muted small">News checked {formatDateTime(portfolio.newsCheckedAt)}</span>
      )}
      {error && <span role="alert" className="error small">{error}</span>}
    </div>
  );
}
