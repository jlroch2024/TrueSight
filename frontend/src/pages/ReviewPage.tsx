// The Review page (Analysts only): the suppliers and customers the AI found, to approve or reject. Unreviewed ones come
// first, the ones most portfolios rely on at the top. A rejection needs a note saying why. Relationships are shared, so
// every decision applies to every portfolio manager.
import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import type { ReviewStatus } from '../api/relationships';
import { type ReviewFilter, type ReviewItem, reviewApi } from '../api/reviews';
import { RadioGroup } from '../components/RadioGroup';
import { formatDateTime, openInNewTab, showInReportUrl } from '../format';
import { displayName } from './supplyChain';

const FILTERS: { value: ReviewFilter; label: string }[] = [
  { value: 'UNREVIEWED', label: 'Unreviewed' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ALL', label: 'All' },
];

const NOTE_REQUIRED = 'Please say why this is rejected.';

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

function matches(filter: ReviewFilter, status: ReviewStatus): boolean {
  return filter === 'ALL' || filter === status;
}

function ReviewCard({ item, onChanged }: { item: ReviewItem; onChanged: (updated: ReviewItem) => void }) {
  const { relationship } = item;
  // The arrow runs from supplier to customer, as on the graph.
  const [supplier, customer] =
    relationship.type === 'SUPPLIER'
      ? [relationship.counterparty, relationship.company]
      : [relationship.company, relationship.counterparty];
  const [rejecting, setRejecting] = useState(false);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function decide(decision: 'APPROVED' | 'REJECTED') {
    if (decision === 'REJECTED' && note.trim() === '') {
      setError(NOTE_REQUIRED);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const updated = await reviewApi.decide(relationship.id, decision, decision === 'REJECTED' ? note.trim() : '');
      setRejecting(false);
      setNote('');
      onChanged(updated);
    } catch (e: unknown) {
      setError(messageOf(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="card review-card">
      <h2>
        {displayName(supplier.name)} {'\u2192'} {displayName(customer.name)}
      </h2>
      <p className="muted small">
        {relationship.type === 'SUPPLIER'
          ? `${displayName(relationship.counterparty.name)} supplies ${displayName(relationship.company.name)}`
          : `${displayName(relationship.counterparty.name)} buys from ${displayName(relationship.company.name)}`}
        {relationship.provides ? `: ${relationship.provides}.` : '.'}
      </p>
      <blockquote>{relationship.evidence}</blockquote>
      <p className="muted small">
        {relationship.report.form}, Filed {relationship.report.filingDate}
      </p>
      <p className="muted small">
        Used by {item.usedByPortfolios} {item.usedByPortfolios === 1 ? 'Portfolio' : 'Portfolios'}
      </p>
      {item.reviewStatus !== 'UNREVIEWED' && (
        <p className={`small review-decision review-${item.reviewStatus.toLowerCase()}`}>
          {item.reviewStatus === 'APPROVED' ? 'Approved' : 'Rejected'}
          {item.reviewedBy ? ` by ${item.reviewedBy}` : ''}
          {item.reviewedAt ? ` on ${formatDateTime(item.reviewedAt)}` : ''}
          {item.reviewNote ? `: ${item.reviewNote}` : ''}
        </p>
      )}

      {rejecting ? (
        <div className="review-reject">
          <label htmlFor={`note-${relationship.id}`}>Why is this rejected?</label>
          <textarea
            id={`note-${relationship.id}`}
            value={note}
            maxLength={500}
            rows={3}
            onChange={(event) => setNote(event.target.value)}
          />
          <div className="actions">
            <button type="button" className="danger" disabled={busy} onClick={() => decide('REJECTED')}>
              Confirm Reject
            </button>
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() => {
                setRejecting(false);
                setNote('');
                setError(null);
              }}
            >
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <div className="actions">
          <button type="button" onClick={() => openInNewTab(showInReportUrl(relationship.report.url, relationship.evidence))}>
            Show in Report
          </button>
          <button type="button" disabled={busy} onClick={() => decide('APPROVED')}>
            Approve
          </button>
          <button type="button" className="danger" disabled={busy} onClick={() => setRejecting(true)}>
            Reject
          </button>
        </div>
      )}
      {error && <p className="error">{error}</p>}
    </li>
  );
}

export function ReviewPage() {
  const [filter, setFilter] = useState<ReviewFilter>('UNREVIEWED');
  // undefined while loading.
  const [items, setItems] = useState<ReviewItem[] | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setItems(undefined);
    setError(null);
    reviewApi
      .list(filter)
      .then((found) => {
        if (active) setItems(found);
      })
      .catch((e: unknown) => {
        if (active) setError(messageOf(e));
      });
    return () => {
      active = false;
    };
  }, [filter]);

  // A decided relationship stays in the list if the filter still matches it (All, or the decision it now has), and
  // leaves it otherwise: approving an Unreviewed one takes it off the Unreviewed list.
  function changed(updated: ReviewItem) {
    setItems((current) =>
      (current ?? [])
        .map((item) => (item.relationship.id === updated.relationship.id ? updated : item))
        .filter((item) => matches(filter, item.reviewStatus)),
    );
  }

  return (
    <section>
      <h1>Review</h1>
      <p className="muted">Your decisions apply to every portfolio manager.</p>

      <RadioGroup
        name="review-filter"
        label="Show"
        value={filter}
        options={FILTERS}
        onChange={(chosen) => setFilter(chosen as ReviewFilter)}
      />

      {error ? (
        <p className="error">{error}</p>
      ) : items === undefined ? (
        <p className="muted">Loading…</p>
      ) : items.length === 0 ? (
        <p className="muted">
          {filter === 'UNREVIEWED' || filter === 'ALL'
            ? 'Nothing to review. Analyse a portfolio to find more relationships.'
            : `No relationships are ${filter.toLowerCase()}.`}
        </p>
      ) : (
        <ul className="review-list">
          {items.map((item) => (
            <ReviewCard key={item.relationship.id} item={item} onChanged={changed} />
          ))}
        </ul>
      )}
    </section>
  );
}
