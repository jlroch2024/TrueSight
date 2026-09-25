// Which page shows for which address.
//
// Adding a page is two lines: a <Route> here, using the address in paths.ts, and an entry in navigation.ts so it
// appears in the sidebar. Pages that are not built yet are simply not listed: the sidebar shows only pages that work.
// Log In and Sign Up go outside <Layout>, so they have no sidebar.
import { Route, Routes } from 'react-router';
import { Layout } from './components/Layout';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
