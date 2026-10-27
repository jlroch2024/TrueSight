import { useEffect, useState } from 'react';
import { type Recommendation, recommendationApi } from '../api/recommendations';

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : 'Recommendations could not be loaded.';
}

export function RecommendationsPage() {
  const [items, setItems] = useState<Recommendation[]>([]);
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [notes, setNotes] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    try {
      const pending = await recommendationApi.list();
      setItems(pending);
      setDrafts(Object.fromEntries(pending.map((item) => [item.id, item.editedText ?? item.text])));
    } catch (cause) {
      setError(messageOf(cause));
    }
  }

  useEffect(() => { void load(); }, []);

  async function decide(item: Recommendation, decision: 'APPROVED' | 'WITHHELD') {
    setBusy(item.id);
    setError(null);
    try {
      await recommendationApi.decide(item.id, decision, drafts[item.id] ?? item.text, notes[item.id] ?? '');
      setItems((current) => current.filter((candidate) => candidate.id !== item.id));
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="review-page">
      <h2>Recommendations</h2>
      {error && <p className="error" role="alert">{error}</p>}
      {items.length === 0 ? <p className="muted">No pending recommendations.</p> : (
        <ol className="recommendation-list">
          {items.map((item) => (
            <li className="card" key={item.id}>
              <h3>{item.event?.title ?? 'Event details unavailable'}</h3>
              {item.event && <>
                <p className="news-meta">{item.event.source ?? 'Unknown Publisher'} · {new Date(item.event.publishedAt).toLocaleDateString()}</p>
                {item.event.summary && <p>{item.event.summary}</p>}
                <p><a href={item.event.url} target="_blank" rel="noreferrer">Open Article</a></p>
              </>}
              <label>
                Suggested next step
                <textarea value={drafts[item.id] ?? item.text} onChange={(event) =>
                  setDrafts((current) => ({ ...current, [item.id]: event.target.value }))} />
              </label>
              <label>
                Note (required to withhold)
                <textarea value={notes[item.id] ?? ''} onChange={(event) =>
                  setNotes((current) => ({ ...current, [item.id]: event.target.value }))} />
              </label>
              <div className="actions">
                <button type="button" disabled={busy === item.id} onClick={() => void decide(item, 'APPROVED')}>Approve</button>
                <button type="button" className="danger" disabled={busy === item.id || !(notes[item.id] ?? '').trim()}
                  onClick={() => void decide(item, 'WITHHELD')}>Withhold</button>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
