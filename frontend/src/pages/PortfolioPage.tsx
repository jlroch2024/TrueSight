// One portfolio's holdings, CSV upload, and annual report analysis.
import { type ChangeEvent, useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ApiError, api } from '../api/client';
import { type Holding, type Portfolio, holdingApi, portfolioApi, portfolioChanges } from '../api/portfolios';
import { paths } from '../paths';
import { STATUS_LABELS, formatDate, formatDateTime, openInNewTab } from '../format';
import { CheckNewsButton } from '../components/CheckNewsButton';
import { useConfirm } from '../components/ConfirmDialog';
import { alertApi } from '../api/alerts';
import { portfolioSubtitle } from './portfolioSubtitle';

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

export function PortfolioPage() {
  const { portfolioId = '' } = useParams();
  const navigate = useNavigate();
  // undefined while loading; null when the user has no portfolio with this id.
  const [portfolio, setPortfolio] = useState<Portfolio | null | undefined>(undefined);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [starting, setStarting] = useState(false);
  const uploadInput = useRef<HTMLInputElement>(null);
  const { confirm, dialog } = useConfirm();

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

  // After a rename in the Manage Portfolios window, show the new name without reloading the holdings.
  useEffect(() => portfolioChanges.listen((changed) => {
    if (changed && String(changed.id) === portfolioId) {
      setPortfolio((current) => (current ? { ...current, ...changed } : current));
    }
  }), [portfolioId]);

  const running = holdings.some((holding) => holding.status === 'ANALYSING');
  const failed = holdings.some((holding) => holding.status === 'FAILED');
  const subtitle = portfolioSubtitle(holdings);

  // How many open alerts the portfolio has, once a news check has finished; the news reaches the user as alerts.
  // Not essential: if they cannot be loaded, the News Checked line says so.
  const [openAlerts, setOpenAlerts] = useState<number | null>(null);
  const [alertsCouldNotBeCounted, setAlertsCouldNotBeCounted] = useState(false);
  const newsCheckedAt = portfolio?.newsCheckedAt ?? null;
  const newsChecking = portfolio?.newsStatus === 'CHECKING';
  useEffect(() => {
    let active = true;
    setOpenAlerts(null);
    setAlertsCouldNotBeCounted(false);
    if (!newsCheckedAt || newsChecking) return;
    alertApi
      .list(portfolioId)
      .then((found) => {
        // The list holds the open alerts only, unless dismissed ones are asked for.
        if (active) setOpenAlerts((found.alerts ?? []).filter((event) => event.status !== 'DISMISSED').length);
      })
      .catch(() => { if (active) setAlertsCouldNotBeCounted(true); });
    return () => { active = false; };
  }, [portfolioId, newsCheckedAt, newsChecking]);

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
      const confirmed = await confirm({
        title: 'Replace Holdings',
        message: `This replaces the ${count} in ${portfolio.name}.`,
        confirmLabel: 'Replace Holdings',
        danger: true,
      });
      if (!confirmed) return;
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
        <p className="muted">This portfolio does not exist. <button type="button" className="inline-action" onClick={() => navigate(paths.portfolios)}>Go to Portfolios</button></p>
      </section>
    );
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <h1>{portfolio.name}</h1>
          <p className="muted">
            {subtitle.text}
            {subtitle.showSupplyChain && (
              <>
                {' '}
                <button type="button" className="inline-action" onClick={() => navigate(paths.supplyChain(portfolioId))}>
                  Open Supply Chain
                </button>
              </>
            )}
          </p>
        </div>
        <div className="page-actions">
          <CheckNewsButton
            portfolio={portfolio}
            onChange={setPortfolio}
            leading={
              <button type="button" onClick={startAnalysis} disabled={starting || running || !holdings.length}>
                {starting ? 'Starting…' : 'Analyse'}
              </button>
            }
            openAlerts={openAlerts}
            alertsCouldNotBeCounted={alertsCouldNotBeCounted}
            onSeeNews={() => navigate(paths.alerts(portfolioId))}
          />
        </div>
      </div>

      <div className="upload-row">
        <button type="button" className="file-button" onClick={() => uploadInput.current?.click()} disabled={uploading}>
          {uploading ? 'Uploading…' : 'Upload CSV'}
        </button>
        <input ref={uploadInput} id="upload-csv" aria-label="CSV file" type="file" accept=".csv,text/csv" onChange={upload} disabled={uploading} className="visually-hidden" />
        <span className="muted small">A CSV with a ticker or symbol column, and an optional weight column. Up to 5 MB.</span>
      </div>

      {error && <p role="alert" className="error">{error}</p>}
      {uploadError && <p role="alert" className="error">{uploadError}</p>}
      {failed && <p className="muted">Some holdings failed. Press Analyse to try them again.</p>}

      <div className="card">
        <h2>Holdings</h2>
        {holdings.length === 0 ? (
          <p className="muted">No holdings yet. Upload CSV adds them from a file with a ticker column.</p>
        ) : (
          <div className="table-scroll">
            <table className="holdings">
              <thead><tr><th>Company</th><th>Ticker</th><th>Weight</th><th>Status</th><th>Last Analysed</th><th>Report Type</th><th>Filing Date</th><th></th></tr></thead>
              <tbody>
                {holdings.map((h) => (
                  <tr key={h.id}>
                    <td title={h.companyName ?? undefined}>{h.companyDisplayName ?? '—'}</td>
                    <td>{h.ticker}</td>
                    <td>{h.weight === null ? '—' : `${h.weight}%`}</td>
                    <td>
                      <span className={`status status-${h.status.toLowerCase()}`}>{STATUS_LABELS[h.status]}</span>
                      {h.statusReason && <span className="status-reason">{h.statusReason}</span>}
                    </td>
                    <td>{h.status === 'DONE' && h.lastAnalysed ? formatDateTime(h.lastAnalysed) : '—'}</td>
                    <td>{h.reportType ?? '—'}</td>
                    <td>{h.reportDate ? formatDate(h.reportDate) : '—'}</td>
                    <td>{h.status === 'DONE' && h.reportLink && <button type="button" onClick={() => openInNewTab(h.reportLink!)}>Show Report</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {dialog}
    </section>
  );
}
