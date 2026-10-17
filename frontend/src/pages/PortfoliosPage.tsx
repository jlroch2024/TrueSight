// The Portfolios page: every portfolio the user has, with Create, Rename and Delete. Choosing one opens it.
import { type FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { type Portfolio, portfolioApi } from '../api/portfolios';
import { paths } from '../paths';

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

export function PortfoliosPage() {
  const navigate = useNavigate();
  const [portfolios, setPortfolios] = useState<Portfolio[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [newName, setNewName] = useState('');
  const [renamingId, setRenamingId] = useState<number | null>(null);
  const [renameTo, setRenameTo] = useState('');
  const [busy, setBusy] = useState(false);

  function load() {
    return portfolioApi
      .list()
      .then(setPortfolios)
      .catch((e: unknown) => setLoadError(messageOf(e)));
  }

  useEffect(() => {
    load();
  }, []);

  // Runs one change, then reloads the list. The buttons are disabled meanwhile, so nothing is sent twice.
  async function run(action: () => Promise<unknown>): Promise<boolean> {
    setBusy(true);
    setActionError(null);
    try {
      await action();
      await load();
      return true;
    } catch (e) {
      setActionError(messageOf(e));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function create(event: FormEvent) {
    event.preventDefault();
    if (await run(() => portfolioApi.create(newName))) {
      setNewName('');
    }
  }

  async function rename(event: FormEvent, id: number) {
    event.preventDefault();
    if (await run(() => portfolioApi.rename(id, renameTo))) {
      setRenamingId(null);
    }
  }

  function remove(portfolio: Portfolio) {
    if (window.confirm(`Delete "${portfolio.name}" and all its holdings? This cannot be undone.`)) {
      run(() => portfolioApi.remove(portfolio.id));
    }
  }

  return (
    <section>
      <h1>Portfolios</h1>
      <p className="muted">Each portfolio is a set of holdings that TrueSight maps to its suppliers and customers. Create one, then open it to upload holdings.</p>

      <form className="card inline-form" onSubmit={create}>
        <label htmlFor="new-portfolio-name">New Portfolio Name</label>
        <input id="new-portfolio-name" value={newName} onChange={(e) => setNewName(e.target.value)} maxLength={100} />
        <button type="submit" disabled={busy || !newName.trim()}>
          Create Portfolio
        </button>
      </form>

      {actionError && <p className="error">{actionError}</p>}

      <div className="card">
        <h2>My Portfolios</h2>
        {loadError ? (
          <p className="error">{loadError}</p>
        ) : !portfolios ? (
          <p className="muted">Loading…</p>
        ) : portfolios.length === 0 ? (
          <p className="muted">You have no portfolios yet. Name your first one above and press Create Portfolio.</p>
        ) : (
          <ul className="portfolio-list">
            {portfolios.map((portfolio) => (
              <li key={portfolio.id}>
                {renamingId === portfolio.id ? (
                  <form className="inline-form" onSubmit={(e) => rename(e, portfolio.id)}>
                    <label htmlFor={`rename-${portfolio.id}`}>New Name</label>
                    <input
                      id={`rename-${portfolio.id}`}
                      value={renameTo}
                      onChange={(e) => setRenameTo(e.target.value)}
                      maxLength={100}
                    />
                    <button type="submit" disabled={busy || !renameTo.trim()}>
                      Save
                    </button>
                    <button type="button" className="secondary" onClick={() => setRenamingId(null)} disabled={busy}>
                      Cancel
                    </button>
                  </form>
                ) : (
                  <>
                    <button type="button" className="portfolio-open" onClick={() => navigate(paths.portfolio(portfolio.id))}>
                      {portfolio.name}
                    </button>
                    <span className="actions">
                      <button
                        type="button"
                        className="secondary"
                        disabled={busy}
                        onClick={() => {
                          setRenamingId(portfolio.id);
                          setRenameTo(portfolio.name);
                        }}
                      >
                        Rename
                      </button>
                      <button type="button" className="danger" disabled={busy} onClick={() => remove(portfolio)}>
                        Delete
                      </button>
                    </span>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
