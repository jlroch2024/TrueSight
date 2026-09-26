// The calls for the user's portfolios. Every page that needs portfolios uses these, so the addresses are written once.
import { api } from './client';

export type Portfolio = { id: number; name: string; createdAt: string };

export const portfolioApi = {
  list: () => api<Portfolio[]>('/portfolios'),
  create: (name: string) => api<Portfolio>('/portfolios', { method: 'POST', body: JSON.stringify({ name }) }),
  rename: (id: number, name: string) =>
    api<Portfolio>(`/portfolios/${id}`, { method: 'PUT', body: JSON.stringify({ name }) }),
  remove: (id: number) => api(`/portfolios/${id}`, { method: 'DELETE' }),
};

export type HoldingStatus = 'WAITING' | 'ANALYSING' | 'DONE' | 'FAILED' | 'NO_REPORT_FOUND';

// The company and report fields stay empty until the analysis fills them in.
export type Holding = {
  id: number;
  ticker: string;
  weight: number | null;
  companyName: string | null;
  status: HoldingStatus;
  statusReason: string | null;
  reportType: string | null;
  reportDate: string | null;
  reportLink: string | null;
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
