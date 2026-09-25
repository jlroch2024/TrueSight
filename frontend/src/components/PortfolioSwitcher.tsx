// The header shown while a portfolio is open: its name, and a list to move to another portfolio.
// Switching changes the address to the chosen portfolio's page, because the address is what says which one is open.
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { type Portfolio, portfolioApi } from '../api/portfolios';
import { paths } from '../paths';

export function PortfolioSwitcher({ portfolioId }: { portfolioId: string }) {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const navigate = useNavigate();

  // Reloaded each time the open portfolio changes, so a rename or a new portfolio shows up.
  useEffect(() => {
    portfolioApi
      .list()
      .then(setPortfolios)
      .catch(() => setPortfolios([]));
  }, [portfolioId]);

  if (!portfolios.some((p) => String(p.id) === portfolioId)) {
    return null;
  }

  return (
    <div className="portfolio-header">
      <label htmlFor="portfolio-switcher">Open Portfolio</label>
      <select
        id="portfolio-switcher"
        value={portfolioId}
        onChange={(e) => navigate(paths.portfolio(e.target.value))}
      >
        {portfolios.map((p) => (
          <option key={p.id} value={String(p.id)}>
            {p.name}
          </option>
        ))}
      </select>
    </div>
  );
}
