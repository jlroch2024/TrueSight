// The sidebar's links, in order. Labels are in Title Case. Only pages that work are listed.
//
// NAV_ITEMS are always shown. A story that adds a page for everybody (e.g. Portfolios) adds one line here, and its
// <Route> in App.tsx.
//
// PORTFOLIO_NAV_ITEMS are shown only while a portfolio is open (its id is in the address), and link to that
// portfolio's pages. A story that adds a portfolio page (e.g. Supply Chain) adds one line here instead.
import { paths } from './paths';

export type NavItem = { label: string; path: string };
export type PortfolioNavItem = { label: string; path: (portfolioId: string) => string };

export const NAV_ITEMS: NavItem[] = [
  { label: 'Home', path: paths.home },
  { label: 'Portfolios', path: paths.portfolios },
];

export const PORTFOLIO_NAV_ITEMS: PortfolioNavItem[] = [
  { label: 'Open Portfolio', path: paths.portfolio },
  { label: 'Supply Chain', path: paths.supplyChain },
];
