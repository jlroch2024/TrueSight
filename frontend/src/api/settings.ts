// The settings the website needs from the backend, so none of them is written into the website's code.
import { api } from './client';

// logoToken is the Logo.dev token for the supply chain graph's logos; empty when none is set.
export type Settings = { logoToken: string };

export const settingsApi = {
  get: () => api<Settings>('/settings'),
};
