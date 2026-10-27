// Who is logged in, for every screen inside <Layout>: Layout loads the account once and puts it here, and the account
// menu (AccountMenu) and the Analyst Desk button read it.
import { createContext, useContext } from 'react';
import type { Me } from '../api/auth';

export type Account = {
  // null until loaded, and when it could not be loaded (failed is then true).
  me: Me | null;
  failed: boolean;
  retry: () => void;
  // Opens the Profile window; the keyboard goes back to returnTo when it closes.
  openProfile: (returnTo: HTMLElement | null) => void;
  logOut: () => void;
};

export const AccountContext = createContext<Account>({
  me: null,
  failed: false,
  retry: () => {},
  openProfile: () => {},
  logOut: () => {},
});

export const useAccount = () => useContext(AccountContext);

/** Up to two letters for the account's square: from the display name's words, or the start of the email. */
export function initialsFor(me: Pick<Me, 'email' | 'displayName'>): string {
  const words = (me.displayName ?? '').trim().split(/\s+/).filter(Boolean);
  const letters = words.length > 1 ? `${words[0]![0]}${words[1]![0]}` : (words[0] ?? me.email ?? '').slice(0, 2);
  return letters.toUpperCase();
}
