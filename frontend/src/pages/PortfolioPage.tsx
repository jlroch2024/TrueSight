// One portfolio's holdings, CSV upload, and annual report analysis.
import { type ChangeEvent, useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError, api } from '../api/client';
import { type Holding, type HoldingStatus, type Portfolio, holdingApi, portfolioApi } from '../api/portfolios';
import { paths } from '../paths';

const STATUS_LABELS: Record<HoldingStatus, string> = {
  WAITING: 'Waiting',
  ANALYSING: 'Analysing',
  DONE: 'Done',
  FAILED: 'Failed',
  NO_REPORT_FOUND: 'No Report Found',
};

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

export function PortfolioPage() {
  const { portfolioId = '' } = useParams();
  // undefined while loading; null when the user has no portfolio with this id.
  const [portfolio, setPortfolio] = useState<Portfolio | null | undefined>(undefined);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [starting, setStarting] = useState(false);

  const loadHoldings = useCallback(async () => {
    try {
      setHoldings(await holdingApi.list(portfolioId));
      setError(null);
    } catch (e) {
      setError(messageOf(e));
    }
  }, [portfolioId]);

  useEffect(() => {
    let active = true;
    setPortfolio(undefined);
    setHoldings([]);
    setError(null);
    setUploadError(null);
    portfolioApi
      .list()
      .then(async (all) => {
        const found = all.find((p) => String(p.id) === portfolioId) ?? null;
        if (found) {
          const initialHoldings = await holdingApi.list(portfolioId);
          if (active) setHoldings(initialHoldings);
        }
        if (active) setPortfolio(found);
      })
      .catch((e: unknown) => { if (active) setError(messageOf(e)); });
    return () => { active = false; };
  }, [portfolioId]);

  const running = holdings.some((holding) => holding.status === 'ANALYSING');
  const failed = holdings.some((holding) => holding.status === 'FAILED');

  useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(() => { void loadHoldings(); }, 1500);
    return () => window.clearInterval(timer);
  }, [running, loadHoldings]);

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target;
    const file = input.files?.[0];
    // Cleared straight away, so choosing the same file again still counts as a new choice.
    input.value = '';
    if (!file || !portfolio) return;
    if (holdings.length > 0) {
      const count = holdings.length === 1 ? '1 holding' : `${holdings.length} holdings`;
      if (!window.confirm(`This replaces the ${count} in ${portfolio.name}.`)) return;
    }
    setUploading(true);
    setUploadError(null);
    try {
      setHoldings(await holdingApi.upload(portfolioId, file));
    } catch (e) {
      setUploadError(messageOf(e));
    } finally {
      setUploading(false);
    }
  }

  async function startAnalysis() {
    setStarting(true);
    setError(null);
    try {
      await api<void>(`/portfolios/${portfolioId}/analysis`, { method: 'POST' });
      await loadHoldings();
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setStarting(false);
    }
  }

  if (error && portfolio === undefined) return <p className="error">{error}</p>;
  if (portfolio === undefined) return <p className="muted">Loading…</p>;
  if (portfolio === null) {
    return (
      <section>
        <h1>Portfolio Not Found</h1>
        <p className="muted">This portfolio does not exist. <Link to={paths.portfolios}>Go to Portfolios</Link></p>
      </section>
    );
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <h1>{portfolio.name}</h1>
          <p className="muted">Annual reports for each company in this portfolio.</p>
        </div>
        <button type="button" onClick={startAnalysis} disabled={starting || running || failed || !holdings.length}>
          {starting ? 'Starting…' : 'Analyse'}
        </button>
      </div>

      <div className="upload-row">
        <label htmlFor="upload-csv" className={uploading ? 'file-button disabled' : 'file-button'}>
          {uploading ? 'Uploading…' : 'Upload CSV'}
        </label>
        <input id="upload-csv" type="file" accept=".csv,text/csv" onChange={upload} disabled={uploading} className="visually-hidden" />
        <span className="muted small">A CSV with a ticker or symbol column, and optionally a weight column. Up to 5 MB.</span>
      </div>

      {error && <p role="alert" className="error">{error}</p>}
      {uploadError && <p role="alert" className="error">{uploadError}</p>}
      {failed && <p className="error">Analyse is unavailable while a holding has Failed status.</p>}

      <div className="card">
        <h2>Holdings</h2>
        {holdings.length === 0 ? (
          <p className="muted">This portfolio has no holdings yet. Upload a CSV to add them.</p>
        ) : (
          <table className="holdings">
            <thead><tr><th>Company</th><th>Ticker</th><th>Weight</th><th>Status</th><th>Report Type</th><th>Filing Date</th><th></th></tr></thead>
            <tbody>
              {holdings.map((h) => (
                <tr key={h.id}>
                  <td>{h.companyName ?? '—'}</td>
                  <td>{h.ticker}</td>
                  <td>{h.weight === null ? '—' : `${h.weight}%`}</td>
                  <td>
                    <span className={`status status-${h.status.toLowerCase()}`}>{STATUS_LABELS[h.status]}</span>
                    {h.statusReason && <span className="status-reason">{h.statusReason}</span>}
                  </td>
                  <td>{h.reportType ?? '—'}</td>
                  <td>{h.reportDate ?? '—'}</td>
                  <td>{h.reportLink && <a href={h.reportLink} target="_blank" rel="noreferrer">Show in Report</a>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
