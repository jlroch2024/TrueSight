// The frame around every page: the TrueSight name and the sidebar on the left, the page itself on the right.
// <Outlet /> is where React Router puts the page for the current address.
import { NavLink, Outlet } from 'react-router';
import { NAV_ITEMS } from '../navigation';

export function Layout() {
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
        </nav>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
