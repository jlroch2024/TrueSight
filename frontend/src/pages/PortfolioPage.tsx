// One portfolio's holdings, CSV upload, annual report analysis, and adding, re-weighting or removing one holding.
import { type ChangeEvent, type FormEvent, type KeyboardEvent, useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ApiError, api } from '../api/client';
import { type Holding, type Portfolio, holdingApi, portfolioApi, portfolioChanges } from '../api/portfolios';
import { paths } from '../paths';
import { STATUS_LABELS, formatDate, formatDateTime, openInNewTab } from '../format';
import { CheckNewsButton } from '../components/CheckNewsButton';
import { useConfirm } from '../components/ConfirmDialog';
import { newsApi } from '../api/news';
import { portfolioSubtitle } from './portfolioSubtitle';

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

const WEIGHT_MESSAGE = 'The weight must be a number between 0 and 100.';

/**
 * What a weight box holds: blank means no weight (null), "12.5" and "12.5%" are numbers, and anything else is
 * undefined. The range, 0 to 100, is checked by the backend, which says so if it is broken.
 */
function parseWeight(text: string): number | null | undefined {
  const cleaned = text.trim().replace(/%$/, '').trim();
  if (cleaned === '') return null;
  const value = Number(cleaned);
  return Number.isFinite(value) ? value : undefined;
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
  // Add Holding: its two boxes, and whether a request is running.
  const [newTicker, setNewTicker] = useState('');
  const [newWeight, setNewWeight] = useState('');
  const [adding, setAdding] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);
  // The one row whose weight is being edited in place, its box, and what went wrong with an edit or a removal.
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editWeight, setEditWeight] = useState('');
  const [savingEdit, setSavingEdit] = useState(false);
  const [rowError, setRowError] = useState<string | null>(null);

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
    setAddError(null);
    setRowError(null);
    setEditingId(null);
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

  // How many articles the last news check found, once one has finished. Not essential: on failure it is left out.
  const [articleCount, setArticleCount] = useState<number | null>(null);
  const newsCheckedAt = portfolio?.newsCheckedAt ?? null;
  const newsChecking = portfolio?.newsStatus === 'CHECKING';
  useEffect(() => {
    let active = true;
    setArticleCount(null);
    if (!newsCheckedAt || newsChecking) return;
    newsApi
      .list(portfolioId)
      .then((articles) => { if (active) setArticleCount(articles.length); })
      .catch(() => {});
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

  async function addHolding(event: FormEvent) {
    event.preventDefault();
    const weight = parseWeight(newWeight);
    if (weight === undefined) {
      setAddError(WEIGHT_MESSAGE);
      return;
    }
    setAdding(true);
    setAddError(null);
    try {
      const added = await holdingApi.add(portfolioId, newTicker.trim(), weight);
      setNewTicker('');
      setNewWeight('');
      // It shows as Waiting at once. The backend starts its analysis straight away, so look again: the table then
      // shows it Analysing, and keeps refreshing (below) until it has a result.
      setHoldings((current) => [...current, added].sort((a, b) => a.ticker.localeCompare(b.ticker)));
      await loadHoldings();
    } catch (e) {
      setAddError(messageOf(e));
    } finally {
      setAdding(false);
    }
  }

  function startEditing(holding: Holding) {
    setRowError(null);
    setEditingId(holding.id);
    setEditWeight(holding.weight === null ? '' : String(holding.weight));
  }

  async function saveWeight(holding: Holding) {
    const weight = parseWeight(editWeight);
    if (weight === undefined) {
      setRowError(WEIGHT_MESSAGE);
      return;
    }
    setSavingEdit(true);
    setRowError(null);
    try {
      const updated = await holdingApi.updateWeight(portfolioId, holding.id, weight);
      setHoldings((current) => current.map((row) => (row.id === updated.id ? updated : row)));
      setEditingId(null);
    } catch (e) {
      setRowError(messageOf(e));
    } finally {
      setSavingEdit(false);
    }
  }

  function weightKeys(event: KeyboardEvent<HTMLInputElement>, holding: Holding) {
    if (event.key === 'Enter') {
      event.preventDefault();
      void saveWeight(holding);
    } else if (event.key === 'Escape') {
      setEditingId(null);
      setRowError(null);
    }
  }

  async function removeHolding(holding: Holding) {
    const confirmed = await confirm({
      title: 'Remove Holding',
      message: `This removes ${holding.ticker} from ${portfolio?.name ?? 'this portfolio'}, and from its alerts.`,
      confirmLabel: 'Remove Holding',
      danger: true,
    });
    if (!confirmed) return;
    setRowError(null);
    try {
      await holdingApi.remove(portfolioId, holding.id);
      setHoldings((current) => current.filter((row) => row.id !== holding.id));
      if (editingId === holding.id) setEditingId(null);
    } catch (e) {
      setRowError(messageOf(e));
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
            articleCount={articleCount}
            onSeeNews={() => navigate({ pathname: paths.supplyChain(portfolioId), search: '?panel=news' })}
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

      <form className="inline-form add-holding" onSubmit={addHolding}>
        <label htmlFor="new-ticker">Ticker</label>
        <input id="new-ticker" value={newTicker} maxLength={20} autoComplete="off" onChange={(event) => setNewTicker(event.target.value)} />
        <label htmlFor="new-weight">Weight (%)</label>
        <input id="new-weight" value={newWeight} inputMode="decimal" autoComplete="off" onChange={(event) => setNewWeight(event.target.value)} />
        <button type="submit" disabled={adding || newTicker.trim() === ''}>{adding ? 'Adding…' : 'Add Holding'}</button>
      </form>
      {addError && <p role="alert" className="error">{addError}</p>}

      {error && <p role="alert" className="error">{error}</p>}
      {rowError && <p role="alert" className="error">{rowError}</p>}
      {uploadError && <p role="alert" className="error">{uploadError}</p>}
      {failed && <p className="muted">Some holdings failed. Press Analyse to try them again.</p>}

      <div className="card">
        <h2>Holdings</h2>
        {holdings.length === 0 ? (
          <p className="muted">No holdings yet. Upload CSV adds them from a file with a ticker column.</p>
        ) : (
          <div className="table-scroll">
            <table className="holdings">
              <thead><tr><th>Company</th><th>Ticker</th><th>Weight</th><th>Status</th><th>Last Analysed</th><th>Report Type</th><th>Filing Date</th><th></th><th></th></tr></thead>
              <tbody>
                {holdings.map((h) => (
                  <tr key={h.id}>
                    <td title={h.companyName ?? undefined}>{h.companyDisplayName ?? '—'}</td>
                    <td>{h.ticker}</td>
                    <td>
                      {editingId === h.id ? (
                        <input
                          className="weight-input"
                          aria-label={`Weight for ${h.ticker}`}
                          value={editWeight}
                          inputMode="decimal"
                          autoFocus
                          onChange={(event) => setEditWeight(event.target.value)}
                          onKeyDown={(event) => weightKeys(event, h)}
                        />
                      ) : (
                        h.weight === null ? '—' : `${h.weight}%`
                      )}
                    </td>
                    <td>
                      <span className={`status status-${h.status.toLowerCase()}`}>{STATUS_LABELS[h.status]}</span>
                      {h.statusReason && <span className="status-reason">{h.statusReason}</span>}
                    </td>
                    <td>{h.status === 'DONE' && h.lastAnalysed ? formatDateTime(h.lastAnalysed) : '—'}</td>
                    <td>{h.reportType ?? '—'}</td>
                    <td>{h.reportDate ? formatDate(h.reportDate) : '—'}</td>
                    <td>{h.status === 'DONE' && h.reportLink && <button type="button" onClick={() => openInNewTab(h.reportLink!)}>Show Report</button>}</td>
                    <td>
                      {editingId === h.id ? (
                        <div className="row-actions">
                          <button type="button" onClick={() => void saveWeight(h)} disabled={savingEdit}>Save</button>
                          <button type="button" className="secondary" onClick={() => { setEditingId(null); setRowError(null); }} disabled={savingEdit}>Cancel</button>
                        </div>
                      ) : (
                        <div className="row-actions">
                          <button type="button" className="secondary" onClick={() => startEditing(h)}>Edit</button>
                          <button type="button" className="danger" onClick={() => void removeHolding(h)}>Remove</button>
                        </div>
                      )}
                    </td>
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
