// The calls for "who is logged in?", and for changing my own profile.
import { api } from './client';

/** A user's access level: everybody is a Portfolio Manager unless their email is listed as an Analyst. */
export type Role = 'PORTFOLIO_MANAGER' | 'ANALYST';

// displayName is the name the user chose to be shown instead of their email; null until they set one.
export type Me = { email: string; role: Role; displayName: string | null };

/** What to call the user: their display name once set, otherwise their email. */
export function nameShown(me: Pick<Me, 'email' | 'displayName'>): string {
  return me.displayName?.trim() ? me.displayName : me.email;
}

export const authApi = {
  me: () => api<Me>('/auth/me'),
  // Answers with the account as it now is.
  setDisplayName: (displayName: string) =>
    api<Me>('/me', { method: 'PUT', body: JSON.stringify({ displayName }) }),
  // Answers with nothing (204): the user stays logged in. A wrong current password is a 400 with its message.
  changePassword: (currentPassword: string, newPassword: string) =>
    api<void>('/me/password', { method: 'PUT', body: JSON.stringify({ currentPassword, newPassword }) }),
};
