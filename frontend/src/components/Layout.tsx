// The frame around every page: the TrueSight name and the sidebar on the left, the page itself on the right.
// <Outlet /> is where React Router puts the page for the current address.
//
// When the address contains a portfolio (/portfolios/7/...), the sidebar also shows that portfolio's pages, from
// PORTFOLIO_NAV_ITEMS in navigation.ts, and the page has a header naming the open portfolio, with a switcher.
import { useEffect, useState } from 'react';
import { Outlet, useLocation, useMatch, useNavigate } from 'react-router';
import { api, tokenStore } from '../api/client';
import { ANALYST_NAV_ITEMS, NAV_ITEMS, PORTFOLIO_NAV_ITEMS } from '../navigation';
import { paths } from '../paths';
import { PortfolioSwitcher } from './PortfolioSwitcher';

export function Layout() {
  const [role, setRole] = useState<string | null>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const portfolioId = useMatch('/portfolios/:portfolioId/*')?.params.portfolioId;
  const portfolioItems = portfolioId ? PORTFOLIO_NAV_ITEMS : [];

  useEffect(() => {
    if (!tokenStore.get()) return;
    api<{ role: string }>('/auth/me').then((me) => setRole(me.role)).catch(() => setRole(null));
  }, []);

  // Logging out only forgets the token: the backend keeps nothing to undo.
  function logOut() {
    tokenStore.clear();
    navigate(paths.logIn, { replace: true });
  }

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <img src="/eye-logo.png" alt="" className="brand-logo" />
          <span>TrueSight</span>
        </div>
        <nav aria-label="Main">
          {NAV_ITEMS.map((item) => (
            <button key={item.path} type="button" className={`nav-link${location.pathname === item.path ? ' active' : ''}`} aria-current={location.pathname === item.path ? 'page' : undefined} onClick={() => navigate(item.path)}>
              {item.label}
            </button>
          ))}
          {role === 'ANALYST' && ANALYST_NAV_ITEMS.map((item) => (
            <button key={item.path} type="button" className={`nav-link${location.pathname === item.path ? ' active' : ''}`} aria-current={location.pathname === item.path ? 'page' : undefined} onClick={() => navigate(item.path)}>
              {item.label}
            </button>
          ))}
          {portfolioId && portfolioItems.length > 0 && (
            <div className="nav-section" aria-label="Portfolio Pages">
              {portfolioItems.map((item) => (
                <button key={item.label} type="button" className={`nav-link${location.pathname === item.path(portfolioId) ? ' active' : ''}`} aria-current={location.pathname === item.path(portfolioId) ? 'page' : undefined} onClick={() => navigate(item.path(portfolioId))}>
                  {item.label}
                </button>
              ))}
            </div>
          )}
        </nav>
        <button type="button" className="log-out" onClick={logOut}>
          Log Out
        </button>
      </aside>
      <main className="content">
        {portfolioId && <PortfolioSwitcher portfolioId={portfolioId} />}
        <Outlet />
      </main>
    </div>
  );
}
