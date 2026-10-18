// The header's portfolio controls, shown while a portfolio is open: a Portfolio list to switch, and Manage Portfolios,
// which opens the window to create, rename and delete them.
//
// Switching changes the address, because the address is what says which portfolio is open, and keeps the same kind of
// page: from a portfolio's Supply Chain, the next portfolio's Supply Chain.
import { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { type Portfolio, portfolioApi } from '../api/portfolios';
import { ManagePortfolios, samePageFor } from './ManagePortfolios';
import { Select } from './Select';

export function PortfolioPicker({ portfolioId }: { portfolioId: string }) {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const [managing, setManaging] = useState(false);
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const load = useCallback(() => {
    portfolioApi
      .list()
      .then(setPortfolios)
      .catch(() => setPortfolios([]));
  }, []);

  // Reloaded each time the open portfolio changes, so a new portfolio shows up.
  useEffect(load, [load, portfolioId]);

  const close = useCallback(() => setManaging(false), []);

  return (
    <div className="portfolio-picker">
      {portfolios.some((p) => String(p.id) === portfolioId) && (
        <Select
          id="portfolio-picker"
          label="Portfolio"
          value={portfolioId}
          options={portfolios.map((p) => ({ value: String(p.id), label: p.name }))}
          onChange={(id) => navigate(samePageFor(pathname, portfolioId, id))}
        />
      )}
      <button type="button" className="secondary" onClick={() => setManaging(true)}>
        Manage Portfolios
      </button>
      {managing && <ManagePortfolios openId={portfolioId} onClose={close} onChanged={load} />}
    </div>
  );
}
