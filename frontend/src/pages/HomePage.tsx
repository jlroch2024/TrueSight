// The first page. For now it only checks the backend is running, which proves the two halves of the project can
// talk to each other. It is also the example page: load data with api(), and show loading, errors and results.
import { useEffect, useState } from 'react';
import { ApiError, api } from '../api/client';
import { Link } from 'react-router';
import { paths } from '../paths';

type Health = { status: string };

export function HomePage() {
  const [health, setHealth] = useState<Health | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Health>('/health')
      .then(setHealth)
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Something went wrong.'));
  }, []);

  return (
    <section>
      <h1>TrueSight</h1>
      <p className="muted">Supply-chain risk for your portfolio, from companies' own annual reports.</p>
      <div className="card">
        <h2>Backend Status</h2>
        {error ? (
          <p className="error">{error}</p>
        ) : health ? (
          <p>Running</p>
        ) : (
          <p className="muted">Checking…</p>
        )}
      </div>
      <p><Link to={paths.portfolio(1)}>Open Demo Portfolio</Link></p>
    </section>
  );
}
