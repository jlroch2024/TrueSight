// The frame around every page: the TrueSight name and the sidebar on the left, the page itself on the right.
// <Outlet /> is where React Router puts the page for the current address.
//
// When the address contains a portfolio (/portfolios/7/...), the sidebar also shows that portfolio's pages, from
// PORTFOLIO_NAV_ITEMS in navigation.ts.
import { NavLink, Outlet, useMatch } from 'react-router';
import { NAV_ITEMS, PORTFOLIO_NAV_ITEMS } from '../navigation';

export function Layout() {
  const portfolioId = useMatch('/portfolios/:portfolioId/*')?.params.portfolioId;
  const portfolioItems = portfolioId ? PORTFOLIO_NAV_ITEMS : [];

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <img src="/eye-logo.png" alt="" className="brand-logo" />
          <span>TrueSight</span>
        </div>
        <nav aria-label="Main">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.path} to={item.path} end className="nav-link">
              {item.label}
            </NavLink>
          ))}
          {portfolioId && portfolioItems.length > 0 && (
            <div className="nav-section" aria-label="Open Portfolio">
              {portfolioItems.map((item) => (
                <NavLink key={item.label} to={item.path(portfolioId)} end className="nav-link">
                  {item.label}
                </NavLink>
              ))}
            </div>
          )}
        </nav>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
