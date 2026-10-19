// The header's portfolio controls, on every page: a Portfolio list to switch, and Manage Portfolios, which opens the
// window to create, rename and delete them.
//
// Switching changes the address, because the address is what says which portfolio is open, and keeps the same kind of
// page: from a portfolio's Supply Chain, the next portfolio's Supply Chain. On a page with no portfolio open, such as
// Review, the list says "Choose a Portfolio", and choosing one opens it.
import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { type Portfolio, portfolioApi, portfolioChanges } from '../api/portfolios';
import { ManagePortfolios, samePageFor } from './ManagePortfolios';
import { Select } from './Select';

const NONE = '';

export function PortfolioPicker({ portfolioId }: { portfolioId?: string }) {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const [managing, setManaging] = useState(false);
  const manageButton = useRef<HTMLButtonElement>(null);
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const load = useCallback(() => {
    portfolioApi
      .list()
      .then(setPortfolios)
      .catch(() => setPortfolios([]));
  }, []);

  // Reloaded when the open portfolio changes, and after any change in the Manage Portfolios window.
  useEffect(load, [load, portfolioId]);
  useEffect(() => portfolioChanges.listen(load), [load]);

  // Closing the window puts the keyboard back on the button that opened it.
  const close = useCallback(() => {
    setManaging(false);
    manageButton.current?.focus();
  }, []);

  const open = portfolioId && portfolios.some((p) => String(p.id) === portfolioId) ? portfolioId : undefined;
  const options = portfolios.map((p) => ({ value: String(p.id), label: p.name }));

  return (
    <div className="portfolio-picker">
      {portfolios.length > 0 && (
        <Select
          id="portfolio-picker"
          label="Portfolio"
          value={open ?? NONE}
          options={open ? options : [{ value: NONE, label: 'Choose a Portfolio' }, ...options]}
          onChange={(id) => {
            if (id !== NONE) navigate(samePageFor(pathname, open, id));
          }}
        />
      )}
      <button ref={manageButton} type="button" className="secondary manage-portfolios" onClick={() => setManaging(true)}>
        Manage Portfolios
      </button>
      {managing && <ManagePortfolios openId={open} onClose={close} />}
    </div>
  );
}
