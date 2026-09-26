// Guards every page except Log In and Sign Up: with no token in the browser, it sends the user to Log In instead of
// showing the page. The backend refuses such requests anyway; this just avoids showing a page that cannot load.
import { Navigate, Outlet } from 'react-router';
import { tokenStore } from '../api/client';
import { paths } from '../paths';

export function RequireLogIn() {
  if (!tokenStore.get()) {
    return <Navigate to={paths.logIn} replace />;
  }
  return <Outlet />;
}
