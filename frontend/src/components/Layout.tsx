// Shared authenticated frame: the TrueSight header, navigation sidebar and current page.
// <Outlet /> is where React Router puts the page for the current address.
//
// The header shows who is logged in: their email, with "Analyst" under it for an Analyst. The sidebar also lists the
// Analyst-only pages (ANALYST_NAV_ITEMS), but only for an Analyst.
//
// When the address contains a portfolio (/portfolios/7/...), the sidebar also shows that portfolio's pages, from
// PORTFOLIO_NAV_ITEMS in navigation.ts. The header has the Portfolio list and Manage Portfolios (PortfolioPicker) on
// every page, so an Analyst on Review can always get back to a portfolio.
import { useEffect, useState } from 'react';
import { Outlet, useLocation, useMatch, useNavigate } from 'react-router';
import { authApi, type Me } from '../api/auth';
import { tokenStore } from '../api/client';
import { ANALYST_NAV_ITEMS, NAV_ITEMS, PORTFOLIO_NAV_ITEMS } from '../navigation';
import { paths } from '../paths';
import { PortfolioPicker } from './PortfolioPicker';

export function Layout() {
  const [me, setMe] = useState<Me | null>(null);
  const [accountError, setAccountError] = useState(false);
  const [accountAttempt, setAccountAttempt] = useState(0);
  const navigate = useNavigate();
  const location = useLocation();
  const portfolioId = useMatch('/portfolios/:portfolioId/*')?.params.portfolioId;
  const portfolioItems = portfolioId ? PORTFOLIO_NAV_ITEMS : [];

  useEffect(() => {
    if (!tokenStore.get()) return;
    let active = true;
    setAccountError(false);
    authApi.me()
      .then((loaded) => {
        if (active) setMe(loaded);
      })
      .catch(() => {
        if (active) {
          setMe(null);
          setAccountError(true);
        }
      });
    return () => {
      active = false;
    };
  }, [accountAttempt]);

  // Logging out only forgets the token: the backend keeps nothing to undo.
  function logOut() {
    tokenStore.clear();
    navigate(paths.logIn, { replace: true });
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-tile"><img src="/eye-logo.png" alt="" className="brand-logo" /></span>
          <span>TrueSight</span>
        </div>
        <PortfolioPicker portfolioId={portfolioId} />
        <div className="header-account">
          {(me || accountError) && (
            <div className="account-identity">
              {me ? (
                <>
                  <span className="account-email">{me.email}</span>
                  {me.role === 'ANALYST' && <span className="account-role">Analyst</span>}
                </>
              ) : (
                <>
                  <span className="muted small">Account Could Not Load</span>
                  <button
                    type="button"
                    className="secondary small-button"
                    onClick={() => setAccountAttempt((attempt) => attempt + 1)}
                  >
                    Retry
                  </button>
                </>
              )}
            </div>
          )}
          <button type="button" className="log-out" onClick={logOut}>Log Out</button>
        </div>
      </header>
      <div className="layout">
        <aside className="sidebar">
          <nav aria-label="Main">
            {NAV_ITEMS.map((item) => (
              <button
                key={item.path}
                type="button"
                className={`nav-link${location.pathname === item.path ? ' active' : ''}`}
                aria-current={location.pathname === item.path ? 'page' : undefined}
                onClick={() => navigate(item.path)}
              >
                {item.label}
              </button>
            ))}
            {me?.role === 'ANALYST' && ANALYST_NAV_ITEMS.map((item) => (
              <button
                key={item.path}
                type="button"
                className={`nav-link${location.pathname === item.path ? ' active' : ''}`}
                aria-current={location.pathname === item.path ? 'page' : undefined}
                onClick={() => navigate(item.path)}
              >
                {item.label}
              </button>
            ))}
            {portfolioId && portfolioItems.length > 0 && (
              <div className="nav-section" aria-label="Portfolio Pages">
                {portfolioItems.map((item) => (
                  <button
                    key={item.label}
                    type="button"
                    className={`nav-link${location.pathname === item.path(portfolioId) ? ' active' : ''}`}
                    aria-current={location.pathname === item.path(portfolioId) ? 'page' : undefined}
                    onClick={() => navigate(item.path(portfolioId))}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            )}
          </nav>
        </aside>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
