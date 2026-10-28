// The Analyst Desk's Relationships tool: the suppliers and customers the AI found, to approve or reject. Unreviewed
// ones come first, the ones a second AI doubts at the top (tagged, with its reason), then the ones most portfolios rely
// on; they are grouped by the report each claim came from. Each claim reads as a picture (supplier, the link as the
// graph draws it, customer, and what is supplied) above the report's own sentence. A rejection needs a note saying why.
// Relationships are shared, so every decision applies to every portfolio manager. A decided claim slides towards its
// pile (right when approved, left when rejected) and the space it took closes.
//
// Every relationship is loaded once; the filter, and the count beside each of its options, are worked out in the page.
import { AnimatePresence, motion } from 'motion/react';
import { Check, CheckCheck, ExternalLink, Pencil, Plus, ShieldAlert, X } from 'lucide-react';
import { type FormEvent, useEffect, useMemo, useState } from 'react';
import { ApiError } from '../api/client';
import { type CompanyOption, type RelationshipType, type ReviewStatus, relationshipApi } from '../api/relationships';
import { type ReviewFilter, type ReviewItem, reviewApi } from '../api/reviews';
import { FlowLink } from '../components/FlowLink';
import { LogoSettingsContext, Tile } from '../components/Tile';
import { RadioGroup } from '../components/RadioGroup';
import { Count } from '../components/Count';
import { Segmented } from '../components/Segmented';
import { Select } from '../components/Select';
import { useLogoSettings } from '../components/useLogoSettings';
import { formatDate, formatDateTime, openInNewTab, showInReportUrl, titleCase } from '../format';
import { plural } from '../components/motion';

const NOTHING_TO_REVIEW = 'Nothing to review. Analyse a portfolio to find more relationships.';

// Each filter, and what its empty list says.
const FILTERS: { value: ReviewFilter; label: string; empty: string }[] = [
  { value: 'UNREVIEWED', label: 'Unreviewed', empty: NOTHING_TO_REVIEW },
  { value: 'APPROVED', label: 'Approved', empty: 'None approved yet. Approve one from Unreviewed.' },
  { value: 'REJECTED', label: 'Rejected', empty: 'None rejected yet.' },
  { value: 'ALL', label: 'All', empty: NOTHING_TO_REVIEW },
];

const NOTE_REQUIRED = 'Please say why this is rejected.';
// The claims' link, in the Relationships tool's own colour.
const LINK_COLOUR = '#2dd4bf';
// A claim leaves towards its pile and fades, then the space it took closes, so the claims below rise into place.
const LEAVE_EASE = [0.4, 0, 1, 1] as const;
const CLOSE = { duration: 0.3, delay: 0.16, ease: [0.65, 0, 0.35, 1] } as const;

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

function matches(filter: ReviewFilter, status: ReviewStatus): boolean {
  return filter === 'ALL' || filter === status;
}

function ReviewCard({ item, onChanged }: { item: ReviewItem; onChanged: (updated: ReviewItem) => void }) {
  const { relationship } = item;
  // The link runs from supplier to customer, as on the graph.
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
    <button type="button" className="btn small" onClick={() => openInNewTab(showInReportUrl(relationship.report.url, relationship.evidence))}>
      <ExternalLink size={15} />
      Show in Report
    </button>
  );

  return (
    <motion.li
      className="review-card glass ticks"
      variants={{
        enter: { opacity: 0, y: 12 },
        shown: { opacity: 1, y: 0, x: 0 },
        leave: (decision: ReviewStatus) => ({
          opacity: 0,
          x: decision === 'REJECTED' ? -72 : 72,
          height: 0,
          marginBottom: 0,
          paddingTop: 0,
          paddingBottom: 0,
          transition: {
            opacity: { duration: 0.22, ease: LEAVE_EASE },
            x: { duration: 0.26, ease: LEAVE_EASE },
            height: CLOSE,
            marginBottom: CLOSE,
            paddingTop: CLOSE,
            paddingBottom: CLOSE,
          },
        }),
      }}
      initial="enter"
      animate="shown"
      exit="leave"
      transition={{ type: 'spring', stiffness: 380, damping: 36 }}
    >
      <h3 className="visually-hidden">
        {supplier.displayName} {'→'} {customer.displayName}
      </h3>
      <div className="claim" aria-hidden="true">
        <span className="claim-tile supplier">
          <Tile name={supplier.name} ticker={supplier.ticker} size={36} />
        </span>
        <b className="claim-name supplier">{supplier.displayName}</b>
        <span className="claim-link">
          <FlowLink colour={LINK_COLOUR} />
        </span>
        <small className="claim-role supplier">Supplier</small>
        <small className="claim-role customer">Customer</small>
        <b className="claim-name customer">{customer.displayName}</b>
        <span className="claim-tile customer">
          <Tile name={customer.name} ticker={customer.ticker} size={36} />
        </span>
        {relationship.provides && !editing && (
          <span className="claim-chip">
            <span className="wire-chip" title={titleCase(relationship.provides)}>
              <span>{titleCase(relationship.provides)}</span>
            </span>
          </span>
        )}
      </div>
      <p className="visually-hidden">
        {relationship.type === 'SUPPLIER'
          ? `${relationship.counterparty.displayName} supplies ${relationship.company.displayName}`
          : `${relationship.counterparty.displayName} buys from ${relationship.company.displayName}`}
        {relationship.provides ? `: ${relationship.provides}.` : '.'}
      </p>
      {item.secondCheck === 'DOUBTS' && (
        <p className="second-check">
          <span className="chip amber"><ShieldAlert size={13} /> Second Check Doubts</span>
          {item.secondCheckReason && <span>{item.secondCheckReason}</span>}
        </p>
      )}
      {editing && (
        <form className="inline-form" onSubmit={saveEdit}>
          <label htmlFor={`provides-${relationship.id}`}>What Is Provided?</label>
          <input
            id={`provides-${relationship.id}`}
            value={provides}
            maxLength={500}
            autoFocus
            onChange={(event) => setProvides(event.target.value)}
          />
          <button type="submit" className="btn small primary" disabled={busy}>Save</button>
          <button
            type="button"
            className="btn small quiet"
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
      )}
      <blockquote className="evidence">{relationship.evidence}</blockquote>
      <p className="review-meta">
        <span>{relationship.report.form}, Filed {formatDate(relationship.report.filingDate)}</span>
        <span>Used by {plural(item.usedByPortfolios, 'Portfolio', 'Portfolios')}</span>
      </p>
      {item.reviewStatus !== 'UNREVIEWED' && (
        <p className={`review-note ${item.reviewStatus.toLowerCase()}`}>
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
          <div className="review-foot">
            {showInReport}
            <span className="grow" />
            <button
              type="button"
              className="btn small quiet"
              disabled={busy}
              onClick={() => {
                setRejecting(false);
                setNote('');
                setError(null);
              }}
            >
              Cancel
            </button>
            <button type="button" className="btn small danger solid" disabled={busy} onClick={() => decide('REJECTED')}>
              <X size={15} />
              Confirm Reject
            </button>
          </div>
        </div>
      ) : (
        // Only the decisions that would change something: an Approved claim cannot be approved again.
        <div className="review-foot">
          {showInReport}
          <span className="quote-found" title="Found Word for Word in the Report">
            <Check size={13} strokeWidth={2.6} /> Quote Found in Report
          </span>
          <span className="grow" />
          <button type="button" className="btn small quiet" disabled={busy} onClick={() => setEditing(true)}>
            <Pencil size={15} />
            Edit
          </button>
          {item.reviewStatus !== 'REJECTED' && (
            <button type="button" className="btn small danger" disabled={busy} onClick={() => setRejecting(true)}>
              <X size={15} />
              Reject
            </button>
          )}
          {item.reviewStatus !== 'APPROVED' && (
            <button type="button" className="btn small primary" disabled={busy} onClick={() => decide('APPROVED')}>
              <Check size={15} />
              Approve
            </button>
          )}
        </div>
      )}
      {error && <p role="alert" className="error">{error}</p>}
    </motion.li>
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
    <form className="review-card glass ticks add-relationship" onSubmit={add}>
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
            options={companies.map((company) => ({ value: String(company.id), label: company.displayName }))}
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
          <div className="review-foot">
            <span className="grow" />
            <button type="button" className="btn small quiet" disabled={busy} onClick={onCancel}>Cancel</button>
            <button type="submit" className="btn small primary" disabled={busy}>Add Relationship</button>
          </div>
        </>
      ) : null}
    </form>
  );
}

export function ReviewPage({ onChanged }: { onChanged?: () => void }) {
  const [filter, setFilter] = useState<ReviewFilter>('UNREVIEWED');
  // Every relationship, whatever its review status; undefined while loading.
  const [all, setAll] = useState<ReviewItem[] | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  // The last decision, so a claim leaves towards its pile.
  const [lastDecision, setLastDecision] = useState<ReviewStatus>('APPROVED');

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

  const names = useMemo(
    () =>
      (all ?? [])
        .flatMap((item) => [item.relationship.company, item.relationship.counterparty])
        .filter((company) => !company.ticker)
        .map((company) => company.name),
    [all],
  );
  const logoSettings = useLogoSettings(names);

  // A decided relationship takes its new status in the full list. The shown list and the counts follow from it, so
  // approving an Unreviewed one takes it off the Unreviewed list and adds one to Approved.
  function changed(updated: ReviewItem) {
    setLastDecision(updated.reviewStatus);
    setAll((current) =>
      (current ?? []).map((item) => (item.relationship.id === updated.relationship.id ? updated : item)),
    );
    onChanged?.();
  }

  const items = all?.filter((item) => matches(filter, item.reviewStatus));
  const count = (value: ReviewFilter) => all?.filter((item) => matches(value, item.reviewStatus)).length ?? 0;
  const reviewed = all ? all.length - count('UNREVIEWED') : 0;
  // Grouped by the report each claim came from, in the order the queue has them.
  const groups = useMemo(() => {
    const byReport = new Map<number, ReviewItem[]>();
    for (const item of items ?? []) {
      const key = item.relationship.company.id;
      byReport.set(key, [...(byReport.get(key) ?? []), item]);
    }
    return [...byReport.values()];
  }, [items]);

  return (
    <LogoSettingsContext.Provider value={logoSettings}>
      <div className="queue-head">
        <div className="queue-progress">
          <span className="queue-figure">
            <strong className="num"><Count value={reviewed} /></strong> of {all?.length ?? 0} Reviewed
          </span>
          <span className="queue-bar" aria-hidden="true">
            <motion.i className="approved" animate={{ width: `${all?.length ? (count('APPROVED') / all.length) * 100 : 0}%` }} />
            <motion.i className="rejected" animate={{ width: `${all?.length ? (count('REJECTED') / all.length) * 100 : 0}%` }} />
          </span>
        </div>
        <div className="queue-filter">
          <Segmented
            label="Show"
            value={filter}
            onChange={setFilter}
            options={FILTERS.map(({ value, label }) => ({
              value,
              name: all === undefined ? label : `${label} (${count(value)})`,
              label: (
                <>
                  {label}
                  {all !== undefined && (
                    <span className={`count${value === 'APPROVED' ? ' teal' : value === 'REJECTED' ? ' alarm' : ''}`}>{count(value)}</span>
                  )}
                </>
              ),
            }))}
          />
        </div>
        {!adding && (
          <button type="button" className="btn small" onClick={() => setAdding(true)}>
            <Plus size={15} />
            Add Relationship
          </button>
        )}
      </div>

      {adding && (
        <AddRelationshipForm
          onAdded={(added) => {
            setAll((current) => [...(current ?? []), added]);
            setAdding(false);
            onChanged?.();
          }}
          onCancel={() => setAdding(false)}
        />
      )}

      {error ? (
        <p className="error">{error}</p>
      ) : items === undefined ? (
        <p className="muted">Loading…</p>
      ) : items.length === 0 ? (
        <div className="glass ticks empty">
          <CheckCheck size={28} className="teal-ink" />
          {FILTERS.find((option) => option.value === filter)!.empty}
        </div>
      ) : (
        <AnimatePresence initial={false}>
          {groups.map((group) => {
            const first = group[0]!.relationship;
            return (
              <motion.div
                key={first.company.id}
                className="report-group"
                exit={{
                  opacity: 0,
                  height: 0,
                  marginBottom: 0,
                  transition: { opacity: { duration: 0.2, ease: LEAVE_EASE }, height: CLOSE, marginBottom: CLOSE },
                }}
              >
                <div className="report-head">
                  <Tile name={first.company.name} ticker={first.company.ticker} size={28} />
                  <span>
                    <b>{first.company.displayName}’s {first.report.form}</b>
                    <span className="muted"> · Filed {formatDate(first.report.filingDate)}</span>
                  </span>
                  <span className="count">{plural(group.length, 'Claim', 'Claims')}</span>
                </div>
                <ul className="review-list">
                  <AnimatePresence initial={false} custom={lastDecision}>
                    {group.map((item) => (
                      <ReviewCard key={item.relationship.id} item={item} onChanged={changed} />
                    ))}
                  </AnimatePresence>
                </ul>
              </motion.div>
            );
          })}
        </AnimatePresence>
      )}
    </LogoSettingsContext.Provider>
  );
}
