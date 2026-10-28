// Every screen behind the login: it loads who is logged in once, for the account menu and the Analyst Desk button,
// opens the Profile window, and shows the screen for the address (<Outlet />, where React Router puts it). There is no
// sidebar: a portfolio is one screen, the workspace, with its own top bar; the Analyst Desk is reached from that top
// bar, by analysts only.
import { AnimatePresence } from 'motion/react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Outlet, useNavigate } from 'react-router';
import { type Me, authApi } from '../api/auth';
import { tokenStore } from '../api/client';
import { paths } from '../paths';
import { type Account, AccountContext } from './account';
import { ProfileWindow } from './ProfileWindow';

export function Layout() {
  const [me, setMe] = useState<Me | null>(null);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [profileOpen, setProfileOpen] = useState(false);
  // What had the keyboard before the Profile window opened, to give it back.
  const returnTo = useRef<HTMLElement | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!tokenStore.get()) return;
    let active = true;
    setFailed(false);
    authApi
      .me()
      .then((loaded) => {
        if (active) setMe(loaded);
      })
      .catch(() => {
        if (active) {
          setMe(null);
          setFailed(true);
        }
      });
    return () => {
      active = false;
    };
  }, [attempt]);

  const closeProfile = useCallback(() => {
    setProfileOpen(false);
    returnTo.current?.focus();
  }, []);

  const account = useMemo<Account>(
    () => ({
      me,
      failed,
      retry: () => setAttempt((count) => count + 1),
      openProfile: (element) => {
        returnTo.current = element;
        setProfileOpen(true);
      },
      // Logging out only forgets the token: the backend keeps nothing to undo.
      logOut: () => {
        tokenStore.clear();
        navigate(paths.logIn, { replace: true });
      },
    }),
    [me, failed, navigate],
  );

  return (
    <AccountContext.Provider value={account}>
      <Outlet />
      {/* Kept while it leaves, so it closes as smoothly as it opens. */}
      <AnimatePresence>
        {profileOpen && me && <ProfileWindow me={me} onChange={setMe} onClose={closeProfile} />}
      </AnimatePresence>
    </AccountContext.Provider>
  );
}
