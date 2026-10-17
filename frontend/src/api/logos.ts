// The call for the website domains of companies without a ticker, so the graph can load their logos from Logo.dev.
import { api } from './client';

export const logoApi = {
  // Each name that has a known domain, with its domain; names without one are left out.
  domains: (names: string[]) =>
    api<Record<string, string>>(`/logos/domains?${names.map((name) => `names=${encodeURIComponent(name)}`).join('&')}`),
};
