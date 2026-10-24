import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  type AiCallDetail,
  type AiCallFilters,
  type AiOutcome,
  type AiPurpose,
  OUTCOME_LABELS,
  PURPOSE_LABELS,
  aiCallApi,
} from '../api/aiCalls';
import { ApiError } from '../api/client';
import { Select } from '../components/Select';
import { formatDateTime, openInNewTab } from '../format';

const EMPTY_FILTERS: AiCallFilters = { purpose: '', outcome: '', from: '', to: '' };
const PURPOSE_OPTIONS = [
  { value: '', label: 'Every Purpose' },
  ...Object.entries(PURPOSE_LABELS).map(([value, label]) => ({ value, label })),
];
const OUTCOME_OPTIONS = [
  { value: '', label: 'Every Outcome' },
  ...Object.entries(OUTCOME_LABELS).map(([value, label]) => ({ value, label })),
];

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong.';
}

function duration(milliseconds: number): string {
  return milliseconds < 1000 ? `${milliseconds} ms` : `${(milliseconds / 1000).toFixed(1)} s`;
}

function AiCallWindow({ callId, onClose }: { callId: number; onClose: () => void }) {
  const [call, setCall] = useState<AiCallDetail>();
  const [error, setError] = useState<string | null>(null);
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    let active = true;
    const before = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    closeRef.current?.focus();
    aiCallApi
      .get(callId)
      .then((found) => {
        if (active) setCall(found);
      })
      .catch((reason: unknown) => {
        if (active) setError(messageOf(reason));
      });
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      active = false;
      document.removeEventListener('keydown', closeOnEscape);
      if (before?.isConnected) before.focus();
    };
  }, [callId, onClose]);

  return createPortal(
    <div className="window-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div className="window ai-call-window" role="dialog" aria-modal="true" aria-labelledby="ai-call-title">
        <div className="page-heading">
          <h2 id="ai-call-title">AI Call</h2>
          <button ref={closeRef} type="button" className="secondary" onClick={onClose}>
            Close
          </button>
        </div>
        {error ? (
          <p className="error">{error}</p>
        ) : call === undefined ? (
          <p className="muted">Loading…</p>
        ) : (
          <>
            <dl className="facts ai-call-facts">
              <div><dt>When</dt><dd>{formatDateTime(call.startedAt)}</dd></div>
              <div><dt>What For</dt><dd>{PURPOSE_LABELS[call.purpose]}</dd></div>
              <div><dt>Model</dt><dd>{call.model}</dd></div>
              <div><dt>Outcome</dt><dd>{OUTCOME_LABELS[call.outcome]}</dd></div>
              <div><dt>How Long</dt><dd>{duration(call.durationMs)}</dd></div>
              <div><dt>Kept</dt><dd>{call.kept ?? '—'}</dd></div>
              <div><dt>Dropped</dt><dd>{call.dropped ?? '—'}</dd></div>
            </dl>
            <section>
              <div className="page-heading">
                <h3>Prompt</h3>
                {call.reportUrl && (
                  <button type="button" onClick={() => openInNewTab(call.reportUrl!)}>
                    Show Report
                  </button>
                )}
              </div>
              <pre>{call.prompt}</pre>
            </section>
            <section>
              <h3>Gemini's Answer</h3>
              {call.response ? <pre>{call.response}</pre> : <p className="muted">No answer arrived.</p>}
              {call.error && <p className="error">{call.error}</p>}
            </section>
          </>
        )}
      </div>
    </div>,
    document.body,
  );
}

export function AiCallsPage() {
  const [filters, setFilters] = useState<AiCallFilters>(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Awaited<ReturnType<typeof aiCallApi.list>>>();
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    setResult(undefined);
    setError(null);
    aiCallApi
      .list(filters, page)
      .then((found) => {
        if (active) setResult(found);
      })
      .catch((reason: unknown) => {
        if (active) setError(messageOf(reason));
      });
    return () => {
      active = false;
    };
  }, [filters, page]);

  function filter<K extends keyof AiCallFilters>(name: K, value: AiCallFilters[K]) {
    setPage(0);
    setFilters((current) => ({ ...current, [name]: value }));
  }

  async function exportCsv() {
    setExporting(true);
    setError(null);
    try {
      await aiCallApi.exportCsv(filters);
    } catch (reason: unknown) {
      setError(messageOf(reason));
    } finally {
      setExporting(false);
    }
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <h1>AI Calls</h1>
          <p className="muted">Inspect what Gemini was asked and exactly what it answered.</p>
        </div>
        <button type="button" disabled={exporting} onClick={exportCsv}>
          {exporting ? 'Exporting…' : 'Export CSV'}
        </button>
      </div>

      <div className="ai-call-filters">
        <Select
          id="ai-call-purpose"
          label="Purpose"
          value={filters.purpose}
          options={PURPOSE_OPTIONS}
          onChange={(value) => filter('purpose', value as AiPurpose | '')}
        />
        <Select
          id="ai-call-outcome"
          label="Outcome"
          value={filters.outcome}
          options={OUTCOME_OPTIONS}
          onChange={(value) => filter('outcome', value as AiOutcome | '')}
        />
        <label>
          From
          <input type="date" value={filters.from} onChange={(event) => filter('from', event.target.value)} />
        </label>
        <label>
          To
          <input type="date" value={filters.to} onChange={(event) => filter('to', event.target.value)} />
        </label>
      </div>

      {error ? (
        <p className="error">{error}</p>
      ) : result === undefined ? (
        <p className="muted">Loading…</p>
      ) : result.calls.length === 0 ? (
        <p className="muted">No AI calls match these filters.</p>
      ) : (
        <>
          <div className="table-scroll card">
            <table className="ai-calls">
              <thead>
                <tr>
                  <th>When</th>
                  <th>What For</th>
                  <th>Model</th>
                  <th>Outcome</th>
                  <th>How Long</th>
                  <th>Kept</th>
                  <th>Dropped</th>
                  <th><span className="visually-hidden">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {result.calls.map((call) => (
                  <tr key={call.id}>
                    <td>{formatDateTime(call.startedAt)}</td>
                    <td>{PURPOSE_LABELS[call.purpose]}</td>
                    <td>{call.model}</td>
                    <td>{OUTCOME_LABELS[call.outcome]}</td>
                    <td>{duration(call.durationMs)}</td>
                    <td>{call.kept ?? '—'}</td>
                    <td>{call.dropped ?? '—'}</td>
                    <td><button type="button" onClick={() => setOpenId(call.id)}>Show</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="ai-call-pager">
            <button type="button" className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>Page {page + 1} of {result.totalPages}</span>
            <button
              type="button"
              className="secondary"
              disabled={page + 1 >= result.totalPages}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </div>
        </>
      )}
      {openId !== null && <AiCallWindow callId={openId} onClose={() => setOpenId(null)} />}
    </section>
  );
}
