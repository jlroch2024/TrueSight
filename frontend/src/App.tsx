// Which page shows for which address.
//
// Adding a page is two lines: a <Route> here, and an entry in navigation.ts so it appears in the sidebar. Pages
// that are not built yet are simply not listed: the sidebar shows only pages that work.
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
