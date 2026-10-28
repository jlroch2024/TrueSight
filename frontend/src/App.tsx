// Which screen shows for which address.
//
// A portfolio is one screen, the workspace. A story adds to it by filling one of its slots (the drawer's tabs, the
// Inspector, the top bar), never by adding a page; an analyst tool is a tab of the Analyst Desk. See AGENTS.md.
//
// Log In and Sign Up are outside <Layout>: one card that switches between the two in place (AuthPage). Every other
// screen is inside <RequireLogIn>, which sends anybody without a login to the Log In page.
import { useEffect } from 'react';
import { Navigate, Route, Routes, useNavigate, useParams } from 'react-router';
import { setLoggedOutHandler } from './api/client';
import { Layout } from './components/Layout';
import { RequireLogIn } from './components/RequireLogIn';
import { AnalystDeskPage } from './pages/AnalystDeskPage';
import { AuthPage } from './pages/AuthPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { OpenFirstPortfolioPage } from './pages/OpenFirstPortfolioPage';
import { PortfolioStart } from './pages/PortfolioStart';
import { WorkspacePage } from './pages/WorkspacePage';
import { type WorkspaceTab, paths } from './paths';

/** An old portfolio address (Supply Chain, Alerts): opens the workspace, on the tab it stood for. */
function OldPortfolioAddress({ tab }: { tab: WorkspaceTab }) {
  const { portfolioId = '' } = useParams();
  return <Navigate to={paths.portfolio(portfolioId, tab)} replace />;
}

export function App() {
  const navigate = useNavigate();

  // When a login expires, the next request answers 401: go to the Log In page.
  useEffect(() => {
    setLoggedOutHandler(() => navigate(paths.logIn, { replace: true }));
  }, [navigate]);

  return (
    <Routes>
      <Route path={paths.logIn} element={<AuthPage />} />
      <Route path={paths.signUp} element={<AuthPage />} />
      <Route element={<RequireLogIn />}>
        <Route element={<Layout />}>
          <Route index element={<OpenFirstPortfolioPage />} />
          <Route path={paths.portfolios} element={<PortfolioStart />} />
          <Route path={paths.portfolio(':portfolioId')} element={<WorkspacePage />} />
          <Route path={paths.supplyChain(':portfolioId')} element={<OldPortfolioAddress tab="holdings" />} />
          <Route path={paths.alerts(':portfolioId')} element={<OldPortfolioAddress tab="alerts" />} />
          <Route path={paths.review} element={<AnalystDeskPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
