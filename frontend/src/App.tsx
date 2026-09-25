// Which page shows for which address.
//
// Adding a page is two lines: a <Route> here, using the address in paths.ts, and an entry in navigation.ts so it
// appears in the sidebar. Pages that are not built yet are simply not listed: the sidebar shows only pages that work.
//
// Log In and Sign Up are outside <Layout>, so they have no sidebar. Every other page is inside <RequireLogIn>, which
// sends anybody without a login to the Log In page.
import { useEffect } from 'react';
import { Route, Routes, useNavigate } from 'react-router';
import { setLoggedOutHandler } from './api/client';
import { Layout } from './components/Layout';
import { RequireLogIn } from './components/RequireLogIn';
import { HomePage } from './pages/HomePage';
import { LogInPage } from './pages/LogInPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { SignUpPage } from './pages/SignUpPage';
import { paths } from './paths';

export function App() {
  const navigate = useNavigate();

  // When a login expires, the next request answers 401: go to the Log In page.
  useEffect(() => {
    setLoggedOutHandler(() => navigate(paths.logIn, { replace: true }));
  }, [navigate]);

  return (
    <Routes>
      <Route path={paths.logIn} element={<LogInPage />} />
      <Route path={paths.signUp} element={<SignUpPage />} />
      <Route element={<RequireLogIn />}>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
