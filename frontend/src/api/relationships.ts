// The calls for a portfolio's suppliers and customers, found in its holdings' annual reports.
import { api } from './client';

export type RelationshipType = 'SUPPLIER' | 'CUSTOMER';

export type CompanySummary = { id: number; name: string; ticker: string | null };

// form is e.g. "10-K"; filingDate is ISO text; url opens the report on the SEC website.
export type ReportSummary = { form: string; filingDate: string; url: string };

// company is the holding whose report it came from; counterparty is the other company. type SUPPLIER means the
// counterparty supplies company; CUSTOMER means it buys from company. evidence is the report's own words.
export type Relationship = {
  id: number;
  company: CompanySummary;
  counterparty: CompanySummary;
  type: RelationshipType;
  provides: string | null;
  evidence: string;
  report: ReportSummary;
};

export const relationshipApi = {
  list: (portfolioId: string) => api<Relationship[]>(`/portfolios/${portfolioId}/relationships`),
};
