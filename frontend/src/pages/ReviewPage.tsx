// The Review page (Analysts only): the suppliers and customers the AI found, to approve or reject. Unreviewed ones come
// first, the ones most portfolios rely on at the top. A rejection needs a note saying why. Relationships are shared, so
// every decision applies to every portfolio manager.
//
// Every relationship is loaded once; the filter, and the count beside each of its options, are worked out in the page.
import { type FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import {
  relationshipApi,
  type CompanyOption,
  type RelationshipType,
  type ReviewStatus,
} from '../api/relationships';
import { type ReviewFilter, type ReviewItem, reviewApi } from '../api/reviews';
import { RadioGroup } from '../components/RadioGroup';
import { Select } from '../components/Select';
import { formatDate, formatDateTime, openInNewTab, showInReportUrl } from '../format';
import { displayName } from './supplyChain';

const NOTHING_TO_REVIEW = 'Nothing to review. Analyse a portfolio to find more relationships.';

// Each filter, and what its empty list says to do next.
const FILTERS: { value: ReviewFilter; label: string; empty: string }[] = [
  { value: 'UNREVIEWED', label: 'Unreviewed', empty: NOTHING_TO_REVIEW },
  { value: 'APPROVED', label: 'Approved', empty: 'None approved yet. Approve one from Unreviewed.' },
  { value: 'REJECTED', label: 'Rejected', empty: 'None rejected yet.' },
  { value: 'ALL', label: 'All', empty: NOTHING_TO_REVIEW },
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
  const [editing, setEditing] = useState(false);
  const [provides, setProvides] = useState(relationship.provides ?? '');
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

  async function saveEdit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const updated = await relationshipApi.edit<ReviewItem>(relationship.id, provides);
      setEditing(false);
      onChanged(updated);
    } catch (e: unknown) {
      setError(messageOf(e));
    } finally {
      setBusy(false);
    }
  }

  // Kept within reach while a rejection note is written, so the analyst can check the report as they explain.
  const showInReport = (
    <button type="button" onClick={() => openInNewTab(showInReportUrl(relationship.report.url, relationship.evidence))}>
      Show in Report
    </button>
  );

  return (
    <li className="card review-card">
      <h2>
        {displayName(supplier.name)} {'\u2192'} {displayName(customer.name)}
      </h2>
      {editing ? (
        <form className="inline-form" onSubmit={saveEdit}>
          <label htmlFor={`provides-${relationship.id}`}>What Is Provided?</label>
          <input
            id={`provides-${relationship.id}`}
            value={provides}
            maxLength={500}
            autoFocus
            onChange={(event) => setProvides(event.target.value)}
          />
          <button type="submit" disabled={busy}>Save</button>
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={() => {
              setEditing(false);
              setProvides(relationship.provides ?? '');
              setError(null);
            }}
          >
            Cancel
          </button>
        </form>
      ) : (
        <p className="muted small">
          {relationship.type === 'SUPPLIER'
            ? `${displayName(relationship.counterparty.name)} supplies ${displayName(relationship.company.name)}`
            : `${displayName(relationship.counterparty.name)} buys from ${displayName(relationship.company.name)}`}
          {relationship.provides ? `: ${relationship.provides}.` : '.'}
        </p>
      )}
      <blockquote>{relationship.evidence}</blockquote>
      <p className="muted small">
        {relationship.report.form}, Filed {formatDate(relationship.report.filingDate)}
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
            autoFocus
            onChange={(event) => setNote(event.target.value)}
          />
          <div className="actions">
            {showInReport}
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
        // Only the decisions that would change something: an Approved card cannot be approved again.
        <div className="actions">
          {showInReport}
          <button type="button" className="secondary" disabled={busy} onClick={() => setEditing(true)}>
            Edit
          </button>
          {item.reviewStatus !== 'APPROVED' && (
            <button type="button" disabled={busy} onClick={() => decide('APPROVED')}>
              Approve
            </button>
          )}
          {item.reviewStatus !== 'REJECTED' && (
            <button type="button" className="danger" disabled={busy} onClick={() => setRejecting(true)}>
              Reject
            </button>
          )}
        </div>
      )}
      {error && <p className="error">{error}</p>}
    </li>
  );
}

function AddRelationshipForm({
  onAdded,
  onCancel,
}: {
  onAdded: (added: ReviewItem) => void;
  onCancel: () => void;
}) {
  const [companies, setCompanies] = useState<CompanyOption[]>();
  const [companyId, setCompanyId] = useState('');
  const [counterpartyName, setCounterpartyName] = useState('');
  const [type, setType] = useState<RelationshipType>('SUPPLIER');
  const [provides, setProvides] = useState('');
  const [sentence, setSentence] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    relationshipApi.companiesWithReport()
      .then((found) => {
        if (!active) return;
        setCompanies(found);
        setCompanyId(found[0] ? String(found[0].id) : '');
      })
      .catch((e: unknown) => {
        if (active) setError(messageOf(e));
      });
    return () => {
      active = false;
    };
  }, []);

  async function add(event: FormEvent) {
    event.preventDefault();
    if (!companyId) return;
    setBusy(true);
    setError(null);
    try {
      const added = await relationshipApi.add<ReviewItem>({
        companyId: Number(companyId),
        counterpartyName: counterpartyName.trim(),
        type,
        provides: provides.trim(),
        sentence: sentence.trim(),
      });
      onAdded(added);
    } catch (e: unknown) {
      setError(messageOf(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card inline-form" onSubmit={add}>
      <h2>Add Relationship</h2>
      {error && <p className="error">{error}</p>}
      {companies === undefined && !error ? (
        <p className="muted">Loading Companies…</p>
      ) : companies?.length === 0 ? (
        <p className="muted">No companies have a stored annual report.</p>
      ) : companies ? (
        <>
          <Select
            id="relationship-company"
            label="Company Whose Report Names It"
            value={companyId}
            options={companies.map((company) => ({ value: String(company.id), label: displayName(company.name) }))}
            onChange={setCompanyId}
          />
          <label htmlFor="counterparty-name">Other Company</label>
          <input
            id="counterparty-name"
            value={counterpartyName}
            maxLength={300}
            required
            onChange={(event) => setCounterpartyName(event.target.value)}
          />
          <RadioGroup
            name="relationship-type"
            label="Relationship"
            value={type}
            options={[
              { value: 'SUPPLIER', label: 'Supplier' },
              { value: 'CUSTOMER', label: 'Customer' },
            ]}
            onChange={(chosen) => setType(chosen as RelationshipType)}
          />
          <label htmlFor="new-provides">What Is Provided?</label>
          <input
            id="new-provides"
            value={provides}
            maxLength={500}
            onChange={(event) => setProvides(event.target.value)}
          />
          <label htmlFor="relationship-sentence">Sentence From the Annual Report</label>
          <textarea
            id="relationship-sentence"
            value={sentence}
            rows={4}
            required
            onChange={(event) => setSentence(event.target.value)}
          />
          <div className="actions">
            <button type="submit" disabled={busy}>Add Relationship</button>
            <button type="button" className="secondary" disabled={busy} onClick={onCancel}>Cancel</button>
          </div>
        </>
      ) : null}
    </form>
  );
}

export function ReviewPage() {
  const [filter, setFilter] = useState<ReviewFilter>('UNREVIEWED');
  // Every relationship, whatever its review status; undefined while loading.
  const [all, setAll] = useState<ReviewItem[] | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    let active = true;
    reviewApi
      .list('ALL')
      .then((found) => {
        if (active) setAll(found);
      })
      .catch((e: unknown) => {
        if (active) setError(messageOf(e));
      });
    return () => {
      active = false;
    };
  }, []);

  // A decided relationship takes its new status in the full list. The shown list and the counts follow from it, so
  // approving an Unreviewed one takes it off the Unreviewed list and adds one to Approved.
  function changed(updated: ReviewItem) {
    setAll((current) =>
      (current ?? []).map((item) => (item.relationship.id === updated.relationship.id ? updated : item)),
    );
  }

  const items = all?.filter((item) => matches(filter, item.reviewStatus));
  const options = FILTERS.map(({ value, label }) => ({
    value,
    label: all === undefined ? label : `${label} (${all.filter((item) => matches(value, item.reviewStatus)).length})`,
  }));

  return (
    <section>
      <h1>Review</h1>
      <p className="muted">Your decisions apply to every portfolio manager.</p>

      {adding ? (
        <AddRelationshipForm
          onAdded={(added) => {
            setAll((current) => [...(current ?? []), added]);
            setAdding(false);
          }}
          onCancel={() => setAdding(false)}
        />
      ) : (
        <button type="button" onClick={() => setAdding(true)}>Add Relationship</button>
      )}

      <RadioGroup
        name="review-filter"
        label="Show"
        value={filter}
        options={options}
        onChange={(chosen) => setFilter(chosen as ReviewFilter)}
      />

      {error ? (
        <p className="error">{error}</p>
      ) : items === undefined ? (
        <p className="muted">Loading…</p>
      ) : items.length === 0 ? (
        <p className="muted">{FILTERS.find((option) => option.value === filter)!.empty}</p>
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
