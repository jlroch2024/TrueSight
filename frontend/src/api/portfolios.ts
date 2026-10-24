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
  announce: () => window.dispatchEvent(new Event(PORTFOLIOS_CHANGED)),
  /** Calls listener after every change; returns the function that stops listening. */
  listen: (listener: () => void) => {
    window.addEventListener(PORTFOLIOS_CHANGED, listener);
    return () => window.removeEventListener(PORTFOLIOS_CHANGED, listener);
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
};
