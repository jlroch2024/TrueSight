// Every page's address, defined once. Use these instead of typing addresses, so a link can never point at a page
// that does not exist.
//
// The open portfolio is always the one in the address: /portfolios/7 is portfolio 7. A page reads it with
// useParams(), e.g. const { portfolioId } = useParams(). Nothing else remembers which portfolio is open.
//
// | Address                              | Page                                      | Built by story             |
// |--------------------------------------|-------------------------------------------|----------------------------|
// | /                                    | Home                                      | Set Up the Project         |
// | /login, /signup                      | Log In, Sign Up                           | Sign Up and Log In         |
// | /portfolios                          | Portfolios: list, create, rename, delete  | Manage My Portfolios       |
// | /portfolios/:portfolioId             | Portfolio: holdings, Upload CSV, Analyse  | Upload a Portfolio CSV     |
// | /portfolios/:portfolioId/supply-chain| Supply Chain: the graph                   | See the Supply Chain...    |
export const paths = {
  home: '/',
  logIn: '/login',
  signUp: '/signup',
  portfolios: '/portfolios',
  portfolio: (portfolioId: string | number) => `/portfolios/${portfolioId}`,
  supplyChain: (portfolioId: string | number) => `/portfolios/${portfolioId}/supply-chain`,
};
