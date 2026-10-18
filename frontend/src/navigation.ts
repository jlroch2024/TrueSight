// The sidebar's navigation buttons, in order. Labels are in Title Case. Only pages that work are listed.
//
// NAV_ITEMS are always shown. A story that adds a page for everybody adds one line here, and its <Route> in App.tsx.
// Portfolios have no page of their own: they are switched and managed from the header (PortfolioPicker).
//
// PORTFOLIO_NAV_ITEMS are shown only while a portfolio is open (its id is in the address), and navigate to that
// portfolio's pages. A story that adds a portfolio page (e.g. Supply Chain) adds one line here instead.
import { paths } from './paths';

export type NavItem = { label: string; path: string };
export type PortfolioNavItem = { label: string; path: (portfolioId: string) => string };

export const NAV_ITEMS: NavItem[] = [];

export const PORTFOLIO_NAV_ITEMS: PortfolioNavItem[] = [
  { label: 'Portfolio', path: paths.portfolio },
  { label: 'Supply Chain', path: paths.supplyChain },
];

// Analyst pages are registered here as they become available. Do not add buttons for unfinished pages.
export const ANALYST_NAV_ITEMS: NavItem[] = [{ label: 'Review', path: paths.review }];
