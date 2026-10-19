// Every page's address, defined once. Use these instead of typing addresses, so navigation buttons always point at an
// existing page.
//
// The open portfolio is always the one in the address: /portfolios/7 is portfolio 7. A page reads it with
// useParams(), e.g. const { portfolioId } = useParams(). Nothing else remembers which portfolio is open.
//
// | Address                              | Page                                      | Built by story             |
// |--------------------------------------|-------------------------------------------|----------------------------|
// | /                                    | Opens the first portfolio (no Home page)  | Open on the Portfolio Page |
// | /login, /signup                      | Log In, Sign Up                           | Sign Up and Log In         |
// | /portfolios                          | Opens the first portfolio, or asks a user | One Portfolio Page         |
// |                                      | with none to create one                   |                            |
// | /portfolios/:portfolioId             | Portfolio: holdings, Upload CSV, Analyse  | See below                  |
// | /portfolios/:portfolioId/supply-chain| Supply Chain: the graph                   | See the Supply Chain...    |
//
// /portfolios/:portfolioId is built by: Manage My Portfolios (the page and its name), then Upload a Portfolio CSV adds
// holdings and upload, then the annual report story adds Analyse.
//
// There is no Portfolios page: the header's Portfolio list switches portfolios, and Manage Portfolios opens a window to
// create, rename and delete them (One Portfolio Page).
export const paths = {
  home: '/',
  logIn: '/login',
  signUp: '/signup',
  portfolios: '/portfolios',
  review: '/review',
  portfolio: (portfolioId: string | number) => `/portfolios/${portfolioId}`,
  supplyChain: (portfolioId: string | number) => `/portfolios/${portfolioId}/supply-chain`,
  alerts: (portfolioId: string | number) => `/portfolios/${portfolioId}/alerts`,
};
