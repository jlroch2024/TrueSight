// The Analyst Desk's Recommendations tool: each suggested next step the AI wrote for an event, waiting for an analyst.
// Approve sends it, as written or as edited, to the portfolio managers whose alerts it belongs to; Reject withholds it
// and needs a note saying why. Research support, never investment advice.
import { AnimatePresence, motion } from 'motion/react';
import { Check, Lightbulb, Pencil, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { type Recommendation, recommendationApi } from '../api/recommendations';
import { EventMark, SeverityBars } from '../components/EventMark';
import { EVENT_TYPE_LABELS, SEVERITY_LABELS, formatAge, openInNewTab } from '../format';

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : 'Recommendations could not be loaded.';
}

export function RecommendationsPage({ onChanged }: { onChanged?: () => void }) {
  // undefined while loading.
  const [items, setItems] = useState<Recommendation[] | undefined>(undefined);
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [notes, setNotes] = useState<Record<number, string>>({});
  // The suggestions open for editing, by id.
  const [editing, setEditing] = useState<Record<number, boolean>>({});
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    try {
      const pending = await recommendationApi.list();
      setItems(pending);
      setDrafts(Object.fromEntries(pending.map((item) => [item.id, item.editedText ?? item.text])));
    } catch (cause) {
      setItems([]);
      setError(messageOf(cause));
    }
  }

  useEffect(() => { void load(); }, []);

  async function decide(item: Recommendation, decision: 'APPROVED' | 'WITHHELD') {
    setBusy(item.id);
    setError(null);
    try {
      await recommendationApi.decide(item.id, decision, drafts[item.id] ?? item.text, notes[item.id] ?? '');
      setItems((current) => current?.filter((candidate) => candidate.id !== item.id));
      onChanged?.();
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      {error && <p className="error" role="alert">{error}</p>}
      {items === undefined ? (
        <p className="muted">Loading…</p>
      ) : items.length === 0 ? (
        <div className="glass ticks empty">
          <Lightbulb size={26} className="lime-ink" />
          No pending recommendations.
        </div>
      ) : (
        <ol className="review-list">
          <AnimatePresence initial={false}>
            {items.map((item) => (
              <motion.li
                key={item.id}
                layout
                className="review-card glass ticks"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, x: 72, height: 0, marginBottom: 0, paddingTop: 0, paddingBottom: 0 }}
                transition={{ type: 'spring', stiffness: 380, damping: 36 }}
              >
                <div className="event-top">
                  {item.event && <EventMark type={item.event.eventType} severity={item.event.severity} size={42} />}
                  <div className="row-text">
                    {item.event && (
                      <div className="a-top">
                        <SeverityBars severity={item.event.severity} />
                        <span className="a-sev">{SEVERITY_LABELS[item.event.severity]}</span>
                        <span className="a-type">{EVENT_TYPE_LABELS[item.event.eventType]}</span>
                        <span className="a-meta num">· {item.event.source ?? 'Unknown Publisher'} · {formatAge(item.event.publishedAt)}</span>
                      </div>
                    )}
                    <h3 className="review-headline">{item.event?.title ?? 'Event details unavailable'}</h3>
                  </div>
                </div>
                {item.event?.summary && <p className="prose">{item.event.summary}</p>}
                {/* The AI's suggestion, as it wrote it; Edit opens it in a box, for an analyst who would word it better. */}
                <div className="field">
                  <div className="field-head">
                    {editing[item.id] ? (
                      <label htmlFor={`suggestion-${item.id}`}>Suggested Next Step</label>
                    ) : (
                      <span className="field-title">Suggested Next Step</span>
                    )}
                    <button
                      type="button"
                      className="btn small quiet"
                      onClick={() => setEditing((current) => ({ ...current, [item.id]: !current[item.id] }))}
                    >
                      {editing[item.id] ? <Check size={15} /> : <Pencil size={15} />}
                      {editing[item.id] ? 'Done' : 'Edit'}
                    </button>
                  </div>
                  {editing[item.id] ? (
                    <textarea
                      id={`suggestion-${item.id}`}
                      value={drafts[item.id] ?? item.text}
                      autoFocus
                      onChange={(event) => setDrafts((current) => ({ ...current, [item.id]: event.target.value }))}
                    />
                  ) : (
                    <div className="suggestion">
                      <p>{drafts[item.id] ?? item.text}</p>
                      {(drafts[item.id] ?? item.text) !== item.text && <small>Edited</small>}
                    </div>
                  )}
                </div>
                <div className="field">
                  <label htmlFor={`note-${item.id}`}>Note (Required to Reject)</label>
                  <textarea
                    id={`note-${item.id}`}
                    value={notes[item.id] ?? ''}
                    onChange={(event) => setNotes((current) => ({ ...current, [item.id]: event.target.value }))}
                  />
                </div>
                <div className="review-foot">
                  <span className="grow" />
                  {item.event && (
                    <button type="button" className="btn small quiet" onClick={() => openInNewTab(item.event!.url)}>Open Article</button>
                  )}
                  <button
                    type="button"
                    className="btn small danger"
                    disabled={busy === item.id || !(notes[item.id] ?? '').trim()}
                    onClick={() => void decide(item, 'WITHHELD')}
                  >
                    <X size={15} />
                    Reject
                  </button>
                  <button type="button" className="btn small primary" disabled={busy === item.id} onClick={() => void decide(item, 'APPROVED')}>
                    <Check size={15} />
                    Approve
                  </button>
                </div>
              </motion.li>
            ))}
          </AnimatePresence>
        </ol>
      )}
    </>
  );
}
