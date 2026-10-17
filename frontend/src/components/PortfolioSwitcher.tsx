// The header shown while a portfolio is open: its name, and a list to move to another portfolio.
// Switching changes the address to the chosen portfolio's page, because the address is what says which one is open.
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { type Portfolio, portfolioApi } from '../api/portfolios';
import { paths } from '../paths';
import { Select } from './Select';

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
      <Select
        id="portfolio-switcher"
        label="Open Portfolio"
        value={portfolioId}
        options={portfolios.map((p) => ({ value: String(p.id), label: p.name }))}
        onChange={(id) => navigate(paths.portfolio(id))}
      />
    </div>
  );
}
