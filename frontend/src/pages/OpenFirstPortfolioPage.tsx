// The address "/", where logging in and signing up land. There is no Home page: TrueSight opens the user's first
// portfolio (the first in their list, which is sorted by name), so they start where the work is. A user with no
// portfolio yet, such as somebody who has just signed up, goes to the Portfolios page to create their first one.
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { portfolioApi } from '../api/portfolios';
import { paths } from '../paths';

export function OpenFirstPortfolioPage() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    portfolioApi
      .list()
      .then((portfolios) => {
        if (cancelled) return;
        const first = portfolios[0];
        navigate(first ? paths.portfolio(first.id) : paths.portfolios, { replace: true });
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof ApiError ? e.message : 'Something went wrong.');
      });
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  if (error) {
    return (
      <section>
        <h1>Your Portfolios</h1>
        <p className="error">{error}</p>
        <button type="button" onClick={() => navigate(paths.portfolios)}>Open My Portfolios</button>
      </section>
    );
  }
  return (
    <section>
      <p className="muted">Opening your portfolio…</p>
    </section>
  );
}
