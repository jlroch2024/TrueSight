// The call for a portfolio's recent news: the articles its news checks collected about its holdings and their
// suppliers and customers, newest first.
import { api } from './client';
import type { CompanySummary } from './relationships';

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH';

// The AI's judgement of one article. eventType and severity are null when it is not an event.
export type NewsAssessment = {
  isEvent: boolean;
  eventType: string | null;
  severity: Severity | null;
  confidence: Severity;
  summary: string | null;
  reason: string;
};

// isHolding: the company is one of the portfolio's holdings. assessment is null until the AI has judged the article.
export type NewsArticle = {
  id: number;
  title: string;
  source: string | null;
  publishedAt: string;
  url: string;
  company: CompanySummary;
  isHolding: boolean;
  assessment: NewsAssessment | null;
};

export const newsApi = {
  list: (portfolioId: string | number) => api<NewsArticle[]>(`/portfolios/${portfolioId}/news`),
};
