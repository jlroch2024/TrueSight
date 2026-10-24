import { api, apiDownload } from './client';

export type AiPurpose =
  | 'FIND_RELATIONSHIPS'
  | 'JUDGE_NEWS'
  | 'SECOND_CHECK'
  | 'RECOMMEND'
  | 'CRITICALITY'
  | 'COMMON_NAMES';
export type AiOutcome = 'ANSWERED' | 'UNREADABLE' | 'FAILED';

export type AiCall = {
  id: number;
  startedAt: string;
  purpose: AiPurpose;
  model: string;
  outcome: AiOutcome;
  durationMs: number;
  kept: number | null;
  dropped: number | null;
};

export type AiCallDetail = AiCall & {
  prompt: string;
  response: string | null;
  error: string | null;
  reportId: number | null;
  reportUrl: string | null;
};

export type AiCallPage = {
  calls: AiCall[];
  page: number;
  totalPages: number;
  total: number;
};

export type AiCallFilters = {
  purpose: AiPurpose | '';
  outcome: AiOutcome | '';
  from: string;
  to: string;
};

export const PURPOSE_LABELS: Record<AiPurpose, string> = {
  FIND_RELATIONSHIPS: 'Finding Relationships',
  JUDGE_NEWS: 'Judging News',
  SECOND_CHECK: 'Second Check',
  RECOMMEND: 'Recommending',
  CRITICALITY: 'Criticality',
  COMMON_NAMES: 'Common Names',
};

export const OUTCOME_LABELS: Record<AiOutcome, string> = {
  ANSWERED: 'Answered',
  UNREADABLE: 'Unreadable',
  FAILED: 'Failed',
};

function query(filters: AiCallFilters, page?: number): string {
  const params = new URLSearchParams();
  if (filters.purpose) params.set('purpose', filters.purpose);
  if (filters.outcome) params.set('outcome', filters.outcome);
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  if (page !== undefined) params.set('page', String(page));
  const text = params.toString();
  return text ? `?${text}` : '';
}

export const aiCallApi = {
  list: (filters: AiCallFilters, page: number) => api<AiCallPage>(`/ai-calls${query(filters, page)}`),
  get: (id: number) => api<AiCallDetail>(`/ai-calls/${id}`),
  exportCsv: (filters: AiCallFilters) =>
    apiDownload(`/ai-calls/export${query(filters)}`, 'ai-calls.csv'),
};
