// One portfolio's page, at /portfolios/:portfolioId. For now it shows the portfolio's name. Upload a Portfolio CSV
// adds the holdings table and the upload button here, then the annual report story adds Analyse.
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { type Portfolio, portfolioApi } from '../api/portfolios';
import { paths } from '../paths';

export function PortfolioPage() {
  const { portfolioId } = useParams();
  // undefined while loading; null when the user has no portfolio with this id.
  const [portfolio, setPortfolio] = useState<Portfolio | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setPortfolio(undefined);
    setError(null);
    portfolioApi
      .list()
      .then((all) => setPortfolio(all.find((p) => String(p.id) === portfolioId) ?? null))
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Something went wrong.'));
  }, [portfolioId]);

  if (error) {
    return <p className="error">{error}</p>;
  }
  if (portfolio === undefined) {
    return <p className="muted">Loading…</p>;
  }
  if (portfolio === null) {
    return (
      <section>
        <h1>Portfolio Not Found</h1>
        <p className="muted">
          This portfolio does not exist. <Link to={paths.portfolios}>Go to Portfolios</Link>
        </p>
      </section>
    );
  }
  return (
    <section>
      <h1>{portfolio.name}</h1>
    </section>
  );
}
