import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { ApiError, api } from '../api/client';

type Holding = {
  id: number;
  ticker: string;
  companyName: string;
  status: 'WAITING' | 'ANALYSING' | 'DONE' | 'FAILED' | 'NO_REPORT_FOUND';
  statusReason: string | null;
  reportType: string | null;
  filingDate: string | null;
  reportUrl: string | null;
};

const statusLabels: Record<Holding['status'], string> = {
  WAITING: 'Waiting',
  ANALYSING: 'Analysing',
  DONE: 'Done',
  FAILED: 'Failed',
  NO_REPORT_FOUND: 'No Report Found',
};

export function PortfolioPage() {
  const { portfolioId = '' } = useParams();
  const [holdings, setHoldings] = useState<Holding[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);

  const loadHoldings = useCallback(async () => {
    try {
      const data = await api<Holding[]>(`/portfolios/${portfolioId}/holdings`);
      setHoldings(data);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong.');
    }
  }, [portfolioId]);

  useEffect(() => { void loadHoldings(); }, [loadHoldings]);

  const running = holdings?.some((holding) => holding.status === 'ANALYSING') ?? false;
  useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(() => { void loadHoldings(); }, 1500);
    return () => window.clearInterval(timer);
  }, [running, loadHoldings]);

  const failed = holdings?.some((holding) => holding.status === 'FAILED') ?? false;

  async function startAnalysis() {
    setStarting(true);
    setError(null);
    try {
      await api<void>(`/portfolios/${portfolioId}/analysis`, { method: 'POST' });
      await loadHoldings();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong.');
    } finally {
      setStarting(false);
    }
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <h1>Portfolio {portfolioId}</h1>
          <p className="muted">Annual reports for each company in this portfolio.</p>
        </div>
        <button type="button" onClick={startAnalysis} disabled={starting || running || failed || !holdings?.length}>
          {starting ? 'Starting…' : 'Analyse'}
        </button>
      </div>

      {error && <p role="alert" className="error">{error}</p>}
      {holdings === null && !error ? <p className="muted">Loading holdings…</p> : null}
      {holdings?.length === 0 && <div className="card"><p>No holdings yet. Upload a CSV to add companies to this portfolio.</p></div>}
      {holdings && holdings.length > 0 && (
        <div className="card table-card">
          <table>
            <thead><tr><th>Company</th><th>Status</th><th>Report</th><th>Filing Date</th><th></th></tr></thead>
            <tbody>
              {holdings.map((holding) => (
                <tr key={holding.id}>
                  <td><strong>{holding.companyName}</strong><span className="muted ticker">{holding.ticker}</span></td>
                  <td>
                    <span className={`status status-${holding.status.toLowerCase()}`}>{statusLabels[holding.status]}</span>
                    {holding.statusReason && <span className="status-reason">{holding.statusReason}</span>}
                  </td>
                  <td>{holding.reportType ?? '—'}</td>
                  <td>{holding.filingDate ?? '—'}</td>
                  <td>{holding.reportUrl && <a href={holding.reportUrl} target="_blank" rel="noreferrer">Show in Report</a>}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {failed && <p className="error">Analyse is unavailable while a holding has Failed status.</p>}
        </div>
      )}
    </section>
  );
}
