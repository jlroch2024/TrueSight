// One portfolio's page, at /portfolios/:portfolioId: its name, its holdings, and Upload CSV. The annual report story
// adds Analyse here.
import { type ChangeEvent, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
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

  useEffect(() => {
    setPortfolio(undefined);
    setHoldings([]);
    setError(null);
    setUploadError(null);
    portfolioApi
      .list()
      .then(async (all) => {
        const found = all.find((p) => String(p.id) === portfolioId) ?? null;
        if (found) {
          setHoldings(await holdingApi.list(portfolioId));
        }
        setPortfolio(found);
      })
      .catch((e: unknown) => setError(messageOf(e)));
  }, [portfolioId]);

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target;
    const file = input.files?.[0];
    // Cleared straight away, so choosing the same file again still counts as a new choice.
    input.value = '';
    if (!file || !portfolio) {
      return;
    }
    if (holdings.length > 0) {
      const count = holdings.length === 1 ? '1 holding' : `${holdings.length} holdings`;
      if (!window.confirm(`This replaces the ${count} in ${portfolio.name}.`)) {
        return;
      }
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

  if (error) {
    return <p className="error">{error}</p>;
  }
  if (portfolio === undefined) {
    return <p className="muted">Loading…</p>;
  }
  if (portfolio === null) {
    return (
      <section>
        <h1>Portfolio Not Found</h1>
        <p className="muted">
          This portfolio does not exist. <Link to={paths.portfolios}>Go to Portfolios</Link>
        </p>
      </section>
    );
  }
  return (
    <section>
      <h1>{portfolio.name}</h1>

      <div className="upload-row">
        <label htmlFor="upload-csv" className={uploading ? 'file-button disabled' : 'file-button'}>
          {uploading ? 'Uploading…' : 'Upload CSV'}
        </label>
        <input
          id="upload-csv"
          type="file"
          accept=".csv,text/csv"
          onChange={upload}
          disabled={uploading}
          className="visually-hidden"
        />
        <span className="muted small">
          A CSV with a ticker or symbol column, and optionally a weight column. Up to 5 MB.
        </span>
      </div>
      {uploadError && <p className="error">{uploadError}</p>}

      <div className="card">
        <h2>Holdings</h2>
        {holdings.length === 0 ? (
          <p className="muted">This portfolio has no holdings yet. Upload a CSV to add them.</p>
        ) : (
          <table className="holdings">
            <thead>
              <tr>
                <th>Ticker</th>
                <th>Weight</th>
                <th>Company</th>
                <th>Status</th>
                <th>Annual Report</th>
              </tr>
            </thead>
            <tbody>
              {holdings.map((h) => (
                <tr key={h.id}>
                  <td>{h.ticker}</td>
                  <td>{h.weight === null ? '—' : `${h.weight}%`}</td>
                  <td>{h.companyName ?? '—'}</td>
                  <td title={h.statusReason ?? undefined}>{STATUS_LABELS[h.status]}</td>
                  <td>
                    {h.reportLink ? (
                      <a href={h.reportLink} target="_blank" rel="noreferrer">
                        {h.reportType} {h.reportDate}
                      </a>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
