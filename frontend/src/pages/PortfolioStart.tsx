// The address /portfolios. There is no separate Portfolios page: this opens the user's first portfolio (the first in
// their list, which is sorted by name). A user with none, such as somebody who has just signed up, is asked to create
// one here, and creating it opens it. Everything else about portfolios is in the workspace's Manage Portfolios window.
import { type FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { portfolioApi } from '../api/portfolios';
import { PageFrame } from '../components/PageFrame';
import { paths } from '../paths';

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

export function PortfolioStart() {
  const navigate = useNavigate();
  // null while loading; true once we know the user has no portfolio.
  const [none, setNone] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [newName, setNewName] = useState('');
  const [busy, setBusy] = useState(false);
  // Counts the attempts: pressing Retry adds one, which loads the portfolios again.
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    portfolioApi
      .list()
      .then((portfolios) => {
        if (cancelled) return;
        const first = portfolios[0];
        if (first) navigate(paths.portfolio(first.id), { replace: true });
        else setNone(true);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(messageOf(e));
      });
    return () => {
      cancelled = true;
    };
  }, [navigate, attempt]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await portfolioApi.create(newName);
      navigate(paths.portfolio(created.id), { replace: true });
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setBusy(false);
    }
  }

  if (none === null) {
    return (
      <PageFrame>
        {error ? (
          <>
            <p className="error">{error}</p>
            <div className="actions">
              <button type="button" className="btn primary" onClick={() => setAttempt((n) => n + 1)}>
                Retry
              </button>
            </div>
          </>
        ) : (
          <p className="muted">Opening Your Portfolio…</p>
        )}
      </PageFrame>
    );
  }
  return (
    <PageFrame>
      <h1>Create Your First Portfolio</h1>
      <p>
        You have no portfolios yet. A portfolio is a set of holdings that TrueSight maps to its suppliers and customers.
        Name your first one to start.
      </p>
      <form className="first-portfolio" onSubmit={create}>
        <div className="field">
          <label htmlFor="first-portfolio-name">New Portfolio Name</label>
          <input
            id="first-portfolio-name"
            className="input"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            maxLength={100}
          />
        </div>
        <button type="submit" className="btn primary" disabled={busy || !newName.trim()}>
          Create Portfolio
        </button>
      </form>
      {error && <p className="error">{error}</p>}
    </PageFrame>
  );
}
