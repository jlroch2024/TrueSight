// Where the screens are reached from. There is no sidebar: everything about a portfolio is in its workspace, in slots
// a story fills rather than pages it adds (see AGENTS.md): the drawer's Holdings and Alerts tabs, the Inspector for
// detail, and the top bar's actions. The Analyst Desk is reached from the workspace's top bar, by analysts only; its
// tools are tabs inside it (AnalystDeskPage), never new pages.
//
// The lists stay so a later screen everyone sees (NAV_ITEMS) or one belonging to the open portfolio
// (PORTFOLIO_NAV_ITEMS) has a place to be named. Both are empty: the workspace has no portfolio pages to list.
import { paths } from './paths';

export type NavItem = { label: string; path: string };
export type PortfolioNavItem = { label: string; path: (portfolioId: string) => string };

export const NAV_ITEMS: NavItem[] = [];

export const PORTFOLIO_NAV_ITEMS: PortfolioNavItem[] = [];

// The Analyst Desk: the top bar's button for analysts.
export const ANALYST_NAV_ITEMS: NavItem[] = [{ label: 'Analyst Desk', path: paths.review }];
