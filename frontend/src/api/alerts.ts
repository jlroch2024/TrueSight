import { api } from './client';
import type { Severity } from './news';
import type { ReviewStatus } from './relationships';

// Alert rows may contain a legacy ACKNOWLEDGED status; responses normalize it to NEW.
export type AlertStatus = 'NEW' | 'DISMISSED';
export type LegacyAlertStatus = AlertStatus | 'ACKNOWLEDGED';

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
  indirect: boolean;
  supplierName: string | null;
  middleName: string | null;
  customerName: string | null;
  reviewStatus: ReviewStatus | null;
  relationshipId: number | null;
};

export type AlertEvent = {
  alertIds: number[];
  status: AlertStatus;
  exposure: number;
  headline: {
    // companyName is the stored name of the company the article is about, which is how the graph's companies are keyed.
    article: { title: string; source: string | null; publishedAt: string; url: string; companyName: string | null };
    assessment: {
      eventType: EventType;
      severity: Severity;
      confidence: Severity;
      summary: string | null;
      reason: string;
    };
  };
  sources: Array<{ title: string; source: string | null; publishedAt: string; url: string }>;
  holdings: ReachedHolding[];
};

// weighted describes current portfolio weights, while event exposure is stored when alerts are created.
export type AlertsResponse = { weighted: boolean; holdingCount: number; alerts: AlertEvent[] };

export const alertApi = {
  list: (portfolioId: string | number, includeDismissed = false) =>
    api<AlertsResponse>(
      `/portfolios/${portfolioId}/alerts${includeDismissed ? '?includeDismissed=true' : ''}`,
    ),
  setStatus: (portfolioId: string | number, alertId: number, status: LegacyAlertStatus) =>
    api<unknown>(`/portfolios/${portfolioId}/alerts/${alertId}`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    }),
  setEventStatus: (portfolioId: string | number, alertIds: number[], status: AlertStatus) =>
    api<AlertEvent>(`/portfolios/${portfolioId}/alerts/status`, {
      method: 'PUT',
      body: JSON.stringify({ alertIds, status }),
    }),
};
