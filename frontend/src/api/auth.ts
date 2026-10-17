// The call for "who is logged in?", and what it answers.
import { api } from './client';

/** A user's access level: everybody is a Portfolio Manager unless their email is listed as an Analyst. */
export type Role = 'PORTFOLIO_MANAGER' | 'ANALYST';

export type Me = { email: string; role: Role };

export const authApi = {
  me: () => api<Me>('/auth/me'),
};
