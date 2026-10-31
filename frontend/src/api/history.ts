// The SEC Filing Timeline: each holding's earlier annual reports, read on request, and how the suppliers and
// customers they name changed year by year.
import { api } from './client';
import type { RelationshipType } from './relationships';

// IDLE: nothing is being read. READING: earlier reports are being read now. FAILED: reading stopped (reason says why);
// what was read is kept.
export type HistoryStatus = 'IDLE' | 'READING' | 'FAILED';

// key is the same in every year (company, counterparty and type), so a relationship can be followed from year to year.
// change says whether the year before also named it. reportUrl and filingDate are the report it came from.
export type HistoryEntry = {
  key: string;
  companyId: number;
  counterpartyId: number;
  type: RelationshipType;
  provides: string | null;
  evidence: string;
  reportUrl: string;
  filingDate: string;
  change: 'NEW' | 'KEPT';
};

// gone is the keys the year before had that this year does not.
export type HistoryYear = { year: number; relationships: HistoryEntry[]; gone: string[] };

// progress is how many earlier reports are read out of how many are being read (null when nothing is). unread is how
// many earlier reports are still to read. years runs from the earliest report's year to the latest's: "Today" is the
// current graph.
export type History = {
  status: HistoryStatus;
  reason: string | null;
  progress: { done: number; total: number } | null;
  unread: number;
  years: HistoryYear[];
};

export const historyApi = {
  get: (portfolioId: string) => api<History>(`/portfolios/${portfolioId}/history`),
  load: (portfolioId: string) => api<void>(`/portfolios/${portfolioId}/history`, { method: 'POST' }),
};
