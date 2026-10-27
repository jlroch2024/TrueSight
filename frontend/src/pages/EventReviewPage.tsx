// The Analyst Desk's Events tool: the last 7 days' judged articles, for every company TrueSight follows, newest first.
// An analyst approves the AI's judgement, or rejects it and says what it should be, with a note; the backend stores
// these as Confirmed and Overruled. A decision applies to every portfolio at once. The note form is the confirmation, so
// there is no extra confirmation window.
import { AnimatePresence, motion } from 'motion/react';
import { Bot, Check, Newspaper, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import type { EventType } from '../api/alerts';
import { ApiError } from '../api/client';
import type { Severity } from '../api/news';
import { type AssessmentFilter, type JudgedArticle, type Judgement, newsReviewApi } from '../api/newsReview';
import { Confidence, EventMark, SeverityBars } from '../components/EventMark';
import { Segmented } from '../components/Segmented';
import { Select } from '../components/Select';
import { EVENT_TYPE_LABELS, SEVERITY_LABELS, formatAge, formatDate, openInNewTab } from '../format';

const EMPTY: Record<AssessmentFilter, string> = {
  EVENTS: 'No undecided events from the last 7 days.',
  NOISE: 'No undecided noise from the last 7 days.',
  DECIDED: 'No articles from the last 7 days have been decided yet.',
};

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong.';
}

/** "Production Stop · High", or "Noise". */
export function judgementLabel(judgement: Judgement): string {
  if (!judgement.isEvent || !judgement.eventType || !judgement.severity) return 'Noise';
  return `${EVENT_TYPE_LABELS[judgement.eventType]} · ${SEVERITY_LABELS[judgement.severity]}`;
}

const EVENT_TYPE_OPTIONS = (Object.keys(EVENT_TYPE_LABELS) as EventType[])
  .map((value) => ({ value, label: EVENT_TYPE_LABELS[value] }));
const SEVERITY_OPTIONS = (['LOW', 'MEDIUM', 'HIGH'] as Severity[])
  .map((value) => ({ value, label: SEVERITY_LABELS[value] }));
const KIND_OPTIONS = [
  { value: 'EVENT', label: 'Event' },
  { value: 'NOISE', label: 'Noise' },
];

/** Rejecting: the judgement it should be, which starts as the opposite of the current one, and the note. */
function RejectForm({
  item,
  busy,
  onSave,
  onCancel,
}: {
  item: JudgedArticle;
  busy: boolean;
  onSave: (judgement: Judgement, note: string) => void;
  onCancel: () => void;
}) {
  const current = item.judgement;
  const [kind, setKind] = useState(current.isEvent ? 'NOISE' : 'EVENT');
  const [eventType, setEventType] = useState<EventType>(current.eventType ?? 'PRODUCTION_STOP');
  const [severity, setSeverity] = useState<Severity>(current.severity ?? 'MEDIUM');
  const [note, setNote] = useState('');

  const next: Judgement = kind === 'EVENT'
    ? { isEvent: true, eventType, severity }
    : { isEvent: false, eventType: null, severity: null };
  const unchanged = next.isEvent === current.isEvent && next.eventType === current.eventType
    && next.severity === current.severity;

  return (
    <form
      className="overrule-form"
      onSubmit={(event) => {
        event.preventDefault();
        onSave(next, note.trim());
      }}
    >
      <div className="overrule-choices">
        <Select id={`overrule-kind-${item.id}`} label="Make It" value={kind} options={KIND_OPTIONS} onChange={setKind} />
        {kind === 'EVENT' && (
          <>
            <Select
              id={`overrule-type-${item.id}`}
              label="Event Type"
              value={eventType}
              options={EVENT_TYPE_OPTIONS}
              onChange={(value) => setEventType(value as EventType)}
            />
            <Select
              id={`overrule-severity-${item.id}`}
              label="Severity"
              value={severity}
              options={SEVERITY_OPTIONS}
              onChange={(value) => setSeverity(value as Severity)}
            />
          </>
        )}
      </div>
      <label htmlFor={`overrule-note-${item.id}`}>Note</label>
      <textarea
        id={`overrule-note-${item.id}`}
        value={note}
        maxLength={500}
        onChange={(event) => setNote(event.target.value)}
      />
      {unchanged && <p className="muted small">Choose a different judgement, or approve the AI's instead.</p>}
      <div className="review-foot">
        <span className="grow" />
        <button type="button" className="btn small quiet" disabled={busy} onClick={onCancel}>Cancel</button>
        <button type="submit" className="btn small danger solid" disabled={busy || unchanged || !note.trim()}>
          <X size={15} />
          Save Rejection
        </button>
      </div>
    </form>
  );
}

function JudgedCard({
  item,
  busy,
  onApprove,
  onReject,
}: {
  item: JudgedArticle;
  busy: boolean;
  onApprove: () => void;
  onReject: (judgement: Judgement, note: string) => void;
}) {
  const [rejecting, setRejecting] = useState(false);
  const { article, decision, judgement } = item;
  const event = judgement.isEvent && judgement.eventType && judgement.severity ? judgement : null;
  const approved = decision?.decision === 'CONFIRMED';
  return (
    <motion.li
      layout
      className="review-card glass ticks event-card"
      data-severity={event?.severity ?? undefined}
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: 72, height: 0, marginBottom: 0, paddingTop: 0, paddingBottom: 0 }}
      transition={{ type: 'spring', stiffness: 380, damping: 36 }}
    >
      <div className="event-top">
        {event ? (
          <EventMark type={event.eventType!} severity={event.severity!} size={42} />
        ) : (
          <span className="noise-mark" aria-hidden="true"><Newspaper size={20} /></span>
        )}
        <div className="row-text">
          <div className="a-top">
            {event && <SeverityBars severity={event.severity!} />}
            <span className="a-sev">{judgementLabel(judgement)}</span>
            <span className="grow" />
            <span className="confidence-label">
              <Confidence level={item.confidence} /> {SEVERITY_LABELS[item.confidence]} Confidence
            </span>
          </div>
          <h3 className="review-headline">{article.title}</h3>
        </div>
      </div>
      {decision && <p className="review-meta">AI Said: {judgementLabel(item.aiSaid)}</p>}
      {item.summary && <p className="prose">{item.summary}</p>}
      <p className="prose">
        <Bot size={14} className="muted inline-icon" />
        {item.reason}
      </p>
      {decision && (
        <p className={`review-note ${approved ? 'approved' : 'rejected'}`}>
          {approved ? 'Approved' : 'Rejected'} by {decision.decidedBy ?? 'an Analyst'} on {formatDate(decision.decidedAt)}
          {decision.note && <>: {decision.note}</>}
        </p>
      )}

      {rejecting ? (
        <RejectForm item={item} busy={busy} onCancel={() => setRejecting(false)} onSave={onReject} />
      ) : (
        <div className="review-foot">
          <span className="review-meta">
            {article.source ?? 'Unknown Publisher'} · {formatAge(article.publishedAt)}
            {item.company && <> · {item.company.displayName}</>}
          </span>
          <span className="grow" />
          <button type="button" className="btn small quiet" onClick={() => openInNewTab(article.url)}>Open Article</button>
          <button type="button" className="btn small danger" disabled={busy} onClick={() => setRejecting(true)}>
            <X size={15} />
            Reject
          </button>
          <button type="button" className="btn small primary" disabled={busy} onClick={onApprove}>
            <Check size={15} />
            Approve
          </button>
        </div>
      )}
    </motion.li>
  );
}

export function EventReviewPage({ onChanged }: { onChanged?: () => void }) {
  const [filter, setFilter] = useState<AssessmentFilter>('EVENTS');
  const [items, setItems] = useState<JudgedArticle[] | undefined>(undefined);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    setItems(undefined);
    setLoadError(null);
    newsReviewApi.list(filter)
      .then((found) => { if (active) setItems(found); })
      .catch((cause: unknown) => { if (active) setLoadError(messageOf(cause)); });
    return () => { active = false; };
  }, [filter]);

  // A decided article leaves Events and Noise for Decided; on Decided it is shown again with the new decision.
  async function decide(item: JudgedArticle, send: () => Promise<JudgedArticle>) {
    setBusy(item.id);
    setError(null);
    try {
      const updated = await send();
      setItems((current) => current && (filter === 'DECIDED'
        ? current.map((candidate) => candidate.id === updated.id ? updated : candidate)
        : current.filter((candidate) => candidate.id !== updated.id)));
      onChanged?.();
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <div className="queue-head">
        <div className="queue-filter narrow">
          <Segmented
            label="Show"
            value={filter}
            onChange={setFilter}
            options={[
              { value: 'EVENTS', label: 'Events' },
              { value: 'NOISE', label: 'Noise' },
              { value: 'DECIDED', label: 'Decided' },
            ]}
          />
        </div>
      </div>
      {error && <p role="alert" className="error">{error}</p>}
      {loadError ? (
        <p className="error">{loadError}</p>
      ) : items === undefined ? (
        <p className="muted">Loading…</p>
      ) : items.length === 0 ? (
        <div className="glass ticks empty">{EMPTY[filter]}</div>
      ) : (
        <ol className="review-list">
          <AnimatePresence initial={false}>
            {items.map((item) => (
              <JudgedCard
                key={item.id}
                item={item}
                busy={busy === item.id}
                onApprove={() => { void decide(item, () => newsReviewApi.confirmJudgement(item.id)); }}
                onReject={(judgement, note) => {
                  void decide(item, () => newsReviewApi.overrule(item.id, { ...judgement, note }));
                }}
              />
            ))}
          </AnimatePresence>
        </ol>
      )}
    </>
  );
}
