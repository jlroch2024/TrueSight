// The calls for the Analyst Desk's Events tab: the last 7 days' judged articles, and an analyst's decision on one.
import type { EventType } from './alerts';
import { api } from './client';
import type { Severity } from './news';
import type { CompanySummary } from './relationships';

export type AssessmentFilter = 'EVENTS' | 'NOISE' | 'DECIDED';

// eventType and severity are null for noise.
export type Judgement = { isEvent: boolean; eventType: EventType | null; severity: Severity | null };

// judgement is the one in force. aiSaid is the AI's own answer, the same as judgement until an analyst overrules it.
// decision is null until an analyst decides; decidedBy is null if the analyst's account has gone.
export type JudgedArticle = {
  id: number;
  article: { title: string; source: string | null; publishedAt: string; url: string };
  company: CompanySummary | null;
  judgement: Judgement;
  aiSaid: Judgement;
  confidence: Severity;
  summary: string | null;
  reason: string;
  decision: {
    decision: 'CONFIRMED' | 'OVERRULED';
    decidedBy: string | null;
    decidedAt: string;
    note: string | null;
  } | null;
};

export type Overrule = { isEvent: boolean; eventType: EventType | null; severity: Severity | null; note: string };

export const newsReviewApi = {
  list: (filter: AssessmentFilter) => api<JudgedArticle[]>(`/news/assessments?filter=${filter}`),
  confirmJudgement: (id: number) =>
    api<JudgedArticle>(`/news/assessments/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ decision: 'CONFIRMED' }),
    }),
  overrule: (id: number, overrule: Overrule) =>
    api<JudgedArticle>(`/news/assessments/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ decision: 'OVERRULED', ...overrule }),
    }),
};
