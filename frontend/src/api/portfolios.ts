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
