import { api } from './client';
import type { Severity } from './news';
import type { ReviewStatus } from './relationships';

// An alert is open (NEW) until it is dismissed.
export type AlertStatus = 'NEW' | 'DISMISSED';

export type EventType =
  | 'PRODUCTION_STOP'
  | 'SUPPLY_SHORTAGE'
  | 'LABOUR_ACTION'
  | 'NATURAL_DISASTER'
  | 'TRADE_RESTRICTION'
  | 'FINANCIAL_DISTRESS'
  | 'LEGAL_OR_REGULATORY'
  | 'CYBER_ATTACK'
  | 'OTHER_DISRUPTION';

export type ReachedHolding = {
  holdingId: number;
  ticker: string;
  weight: number | null;
  // The stored company names; both null when the event is about the holding itself.
  supplierName: string | null;
  customerName: string | null;
  reviewStatus: ReviewStatus | null;
  // The relationship the event reaches the holding through; null when the event is about the holding itself.
  relationshipId: number | null;
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
    eventType: EventType;
    severity: Severity;
    confidence: Severity;
    summary: string | null;
    reason: string;
  };
  holdings: ReachedHolding[];
};

// weighted reflects the portfolio's current weights; alert exposure was stored when the alert was created.
// When weighted, exposure is a total weight in %. Otherwise it is how many of holdingCount holdings it reaches.
export type AlertsResponse = {
  weighted: boolean;
  holdingCount: number;
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
