import { api } from './client';
import type { Severity } from './news';
import type { ReviewStatus } from './relationships';

export type AlertStatus = 'NEW' | 'ACKNOWLEDGED' | 'DISMISSED';

export type ReachedHolding = {
  holdingId: number;
  ticker: string;
  weight: number | null;
  link: string | null;
  reviewStatus: ReviewStatus | null;
};

export type Alert = {
  id: number;
  status: AlertStatus;
  exposure: number;
  createdAt: string;
  article: {
    title: string;
    source: string | null;
    publishedAt: string;
    url: string;
  };
  assessment: {
    eventType: string;
    severity: Severity;
    confidence: Severity;
    summary: string | null;
    reason: string;
  };
  holdings: ReachedHolding[];
};

export type AlertsResponse = {
  articlesChecked: number;
  noiseCount: number;
  alerts: Alert[];
};

export const alertApi = {
  list: (portfolioId: string | number, includeDismissed = false) =>
    api<AlertsResponse>(
      `/portfolios/${portfolioId}/alerts${includeDismissed ? '?includeDismissed=true' : ''}`,
    ),
  setStatus: (portfolioId: string | number, alertId: number, status: AlertStatus) =>
    api<Alert>(`/portfolios/${portfolioId}/alerts/${alertId}`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    }),
};
