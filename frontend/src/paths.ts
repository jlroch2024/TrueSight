// Every screen's address, defined once. Use these instead of typing addresses, so navigation buttons always point at an
// existing screen.
//
// The open portfolio is always the one in the address: /portfolios/7 is portfolio 7. A screen reads it with
// useParams(), e.g. const { portfolioId } = useParams(). The workspace's open drawer tab is in the address too
// (?tab=alerts). Nothing else remembers which portfolio or tab is open.
//
// | Address                               | Screen                                                         |
// |---------------------------------------|----------------------------------------------------------------|
// | /                                     | Opens the first portfolio (no Home page)                       |
// | /login, /signup                       | Log In and Sign Up: one card that switches in place            |
// | /portfolios                           | Opens the first portfolio, or asks a user with none to create one |
// | /portfolios/:portfolioId              | The workspace: the graph, the Holdings and Alerts drawer, the Inspector |
// | /portfolios/:portfolioId?tab=alerts   | The workspace with the drawer's Alerts tab open                |
// | /portfolios/:portfolioId/supply-chain | Old address: opens the workspace                               |
// | /portfolios/:portfolioId/alerts       | Old address: opens the workspace on the Alerts tab             |
// | /review                               | The Analyst Desk (Analysts only), the open tool in ?tab=       |
//
// There is no Portfolios page: the workspace's portfolio menu switches portfolios, and Manage Portfolios opens a window
// to create, rename and delete them.

/** The workspace drawer's tabs. Holdings is the one open when the address names none. */
export type WorkspaceTab = 'holdings' | 'alerts';

export const paths = {
  home: '/',
  logIn: '/login',
  signUp: '/signup',
  portfolios: '/portfolios',
  review: '/review',
  portfolio: (portfolioId: string | number, tab: WorkspaceTab = 'holdings') =>
    `/portfolios/${portfolioId}${tab === 'alerts' ? '?tab=alerts' : ''}`,
  // Kept so saved links and habits still work: each opens the workspace.
  supplyChain: (portfolioId: string | number) => `/portfolios/${portfolioId}/supply-chain`,
  alerts: (portfolioId: string | number) => `/portfolios/${portfolioId}/alerts`,
};
