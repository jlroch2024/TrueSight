// The calls for the user's portfolios. Every page that needs portfolios uses these, so the addresses are written once.
import { api } from './client';

export type NewsStatus = 'IDLE' | 'CHECKING' | 'FAILED';

export type Portfolio = {
  id: number;
  name: string;
  createdAt: string;
  newsStatus: NewsStatus;
  newsStatusReason: string | null;
  newsCheckedAt: string | null;
};

export const portfolioApi = {
  list: () => api<Portfolio[]>('/portfolios'),
  create: (name: string) => api<Portfolio>('/portfolios', { method: 'POST', body: JSON.stringify({ name }) }),
  rename: (id: number, name: string) =>
    api<Portfolio>(`/portfolios/${id}`, { method: 'PUT', body: JSON.stringify({ name }) }),
  remove: (id: number) => api(`/portfolios/${id}`, { method: 'DELETE' }),
  newsCheck: (id: number | string) => api<void>(`/portfolios/${id}/news-check`, { method: 'POST' }),
};

// Sent in the browser after a portfolio is created, renamed or deleted, so everything showing portfolio names (the
// header's list, the open portfolio's heading) loads them again.
const PORTFOLIOS_CHANGED = 'truesight:portfolios-changed';

export const portfolioChanges = {
  announce: (changed?: Portfolio) =>
    window.dispatchEvent(new CustomEvent(PORTFOLIOS_CHANGED, { detail: changed ?? null })),
  /** Calls listener after every change; returns the function that stops listening. */
  listen: (listener: (changed?: Portfolio) => void) => {
    const handle = (event: Event) =>
      listener((event as CustomEvent<Portfolio | null>).detail ?? undefined);
    window.addEventListener(PORTFOLIOS_CHANGED, handle);
    return () => window.removeEventListener(PORTFOLIOS_CHANGED, handle);
  },
};

export type HoldingStatus = 'WAITING' | 'ANALYSING' | 'DONE' | 'FAILED' | 'NO_REPORT_FOUND';

// The company and report fields stay empty until the analysis fills them in.
export type Holding = {
  id: number;
  ticker: string;
  weight: number | null;
  companyName: string | null;
  // How every page shows the company; null until the holding is matched to one, like companyName.
  companyDisplayName: string | null;
  status: HoldingStatus;
  statusReason: string | null;
  reportType: string | null;
  reportDate: string | null;
  reportLink: string | null;
  lastAnalysed: string | null;
};

export const holdingApi = {
  list: (portfolioId: string) => api<Holding[]>(`/portfolios/${portfolioId}/holdings`),
  // Replaces every holding in the portfolio with the file's.
  upload: (portfolioId: string, file: File) => {
    const body = new FormData();
    body.append('file', file);
    return api<Holding[]>(`/portfolios/${portfolioId}/holdings/upload`, { method: 'POST', body });
  },
  // Adds one holding, saved as Waiting; its analysis starts at once. weight is null for none.
  add: (portfolioId: string, ticker: string, weight: number | null) =>
    api<Holding>(`/portfolios/${portfolioId}/holdings`, { method: 'POST', body: JSON.stringify({ ticker, weight }) }),
  // Changes one holding's weight (0 to 100), or null for none.
  updateWeight: (portfolioId: string, holdingId: number, weight: number | null) =>
    api<Holding>(`/portfolios/${portfolioId}/holdings/${holdingId}`, {
      method: 'PUT',
      body: JSON.stringify({ weight }),
    }),
  // Removes one holding, and its place in the portfolio's alerts.
  remove: (portfolioId: string, holdingId: number) =>
    api<void>(`/portfolios/${portfolioId}/holdings/${holdingId}`, { method: 'DELETE' }),
};
