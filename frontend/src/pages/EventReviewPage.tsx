// The Analyst Desk's Events tab: the last 7 days' judged articles, for every company TrueSight follows, newest first.
// An analyst confirms the AI's judgement, or overrules it with a note. A decision applies to every portfolio at once.
// The note form is the confirmation, so there is no extra confirmation window.
import { useEffect, useState } from 'react';
import type { EventType } from '../api/alerts';
import { ApiError } from '../api/client';
import type { Severity } from '../api/news';
import { type AssessmentFilter, type JudgedArticle, type Judgement, newsReviewApi } from '../api/newsReview';
import { RadioGroup } from '../components/RadioGroup';
import { Select } from '../components/Select';
import { EVENT_TYPE_LABELS, SEVERITY_LABELS, formatDate, openInNewTab } from '../format';

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

/** The overrule form: the new judgement, which starts as the opposite of the current one, and the note. */
function OverruleForm({
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
      {unchanged && <p className="muted small">Choose a different judgement, or confirm the AI's instead.</p>}
      <div className="actions">
        <button type="submit" disabled={busy || unchanged || !note.trim()}>Save Overrule</button>
        <button type="button" className="secondary" disabled={busy} onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

function JudgedCard({
  item,
  busy,
  onConfirm,
  onOverrule,
}: {
  item: JudgedArticle;
  busy: boolean;
  onConfirm: () => void;
  onOverrule: (judgement: Judgement, note: string) => void;
}) {
  const [overruling, setOverruling] = useState(false);
  const { article, decision } = item;
  return (
    <li className="card judged-article">
      <p className="news-meta">
        {article.source ?? 'Unknown Publisher'} · {formatDate(article.publishedAt)}
        {item.company && <> · {item.company.displayName}</>}
      </p>
      <h3>{article.title}</h3>
      <p className="chips">
        <span className={item.judgement.isEvent ? 'news-tag' : 'news-tag not-event'}>
          {judgementLabel(item.judgement)}
        </span>
        <span className="news-tag not-event">{SEVERITY_LABELS[item.confidence]} Confidence</span>
        {decision && <span className="news-tag not-event">AI Said: {judgementLabel(item.aiSaid)}</span>}
      </p>
      {item.summary && <p className="alert-summary">{item.summary}</p>}
      <p>{item.reason}</p>
      {decision && (
        <p className="muted small judged-decision">
          {decision.decision === 'CONFIRMED' ? 'Confirmed' : 'Overruled'} by {decision.decidedBy ?? 'an Analyst'} on{' '}
          {formatDate(decision.decidedAt)}
          {decision.note && <>: {decision.note}</>}
        </p>
      )}

      {overruling ? (
        <OverruleForm
          item={item}
          busy={busy}
          onCancel={() => setOverruling(false)}
          onSave={(judgement, note) => onOverrule(judgement, note)}
        />
      ) : (
        <div className="actions">
          <button type="button" className="secondary" onClick={() => openInNewTab(article.url)}>Open Article</button>
          <button type="button" disabled={busy} onClick={onConfirm}>Confirm</button>
          <button type="button" className="secondary" disabled={busy} onClick={() => setOverruling(true)}>
            Overrule
          </button>
        </div>
      )}
    </li>
  );
}

export function EventReviewPage() {
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
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="review-page">
      <h2>Events</h2>
      <p className="muted">
        Confirm or overrule the AI's judgement of each article from the last 7 days. A decision changes the alerts of
        every portfolio at once.
      </p>
      <RadioGroup
        name="event-filter"
        label="Show"
        value={filter}
        options={[
          { value: 'EVENTS', label: 'Events' },
          { value: 'NOISE', label: 'Noise' },
          { value: 'DECIDED', label: 'Decided' },
        ]}
        onChange={(next) => setFilter(next as AssessmentFilter)}
      />
      {error && <p role="alert" className="error">{error}</p>}
      {loadError ? (
        <p className="error">{loadError}</p>
      ) : items === undefined ? (
        <p className="muted">Loading…</p>
      ) : items.length === 0 ? (
        <p className="muted">{EMPTY[filter]}</p>
      ) : (
        <ol className="recommendation-list">
          {items.map((item) => (
            <JudgedCard
              key={item.id}
              item={item}
              busy={busy === item.id}
              onConfirm={() => { void decide(item, () => newsReviewApi.confirmJudgement(item.id)); }}
              onOverrule={(judgement, note) => {
                void decide(item, () => newsReviewApi.overrule(item.id, { ...judgement, note }));
              }}
            />
          ))}
        </ol>
      )}
    </section>
  );
}
