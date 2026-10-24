// The calls for a portfolio's suppliers and customers, found in its holdings' annual reports.
import { api } from './client';

export type RelationshipType = 'SUPPLIER' | 'CUSTOMER';
export type RelationshipSource = 'AI' | 'ANALYST';
export type Criticality = 'SOLE_SOURCE' | 'SINGLE_SOURCE';

// Where an Analyst's review of a relationship has got. Rejected relationships never reach a graph.
export type ReviewStatus = 'UNREVIEWED' | 'APPROVED' | 'REJECTED';

export type CompanySummary = { id: number; name: string; ticker: string | null };

// form is e.g. "10-K"; filingDate is ISO text; url opens the report on the SEC website.
export type ReportSummary = { form: string; filingDate: string; url: string };

// company is the holding whose report it came from; counterparty is the other company. type SUPPLIER means the
// counterparty supplies company; CUSTOMER means it buys from company. evidence is the report's own words.
// reviewedAt (ISO text) and reviewNote are null until an Analyst decides; the note may stay empty for an approval.
export type Relationship = {
  id: number;
  company: CompanySummary;
  counterparty: CompanySummary;
  type: RelationshipType;
  provides: string | null;
  evidence: string;
  source: RelationshipSource;
  criticality: Criticality | null;
  criticalityEvidence: string | null;
  report: ReportSummary;
  reviewStatus: ReviewStatus;
  reviewedAt: string | null;
  reviewNote: string | null;
};

export type CompanyOption = { id: number; name: string };

export type AddRelationship = {
  companyId: number;
  counterpartyName: string;
  type: RelationshipType;
  provides: string;
  sentence: string;
};

export const relationshipApi = {
  list: (portfolioId: string) => api<Relationship[]>(`/portfolios/${portfolioId}/relationships`),
  edit: <T>(relationshipId: number, provides: string) =>
    api<T>(`/relationships/${relationshipId}`, {
      method: 'PUT',
      body: JSON.stringify({ provides }),
    }),
  add: <T>(relationship: AddRelationship) =>
    api<T>('/relationships', {
      method: 'POST',
      body: JSON.stringify(relationship),
    }),
  companiesWithReport: () => api<CompanyOption[]>('/companies?withReport=true'),
};
