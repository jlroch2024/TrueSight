// The calls for a portfolio's suppliers and customers, found in its holdings' annual reports.
import { api } from './client';

export type RelationshipType = 'SUPPLIER' | 'CUSTOMER';
export type RelationshipSource = 'AI' | 'ANALYST';
// REPORT: found by reading the company's whole report. NAME_SEARCH: found by a search for the counterparty's name, in a
// few passages of the company's report that name it.
export type FoundBy = 'REPORT' | 'NAME_SEARCH';
export type Criticality = 'SOLE_SOURCE' | 'SINGLE_SOURCE';

// Where an Analyst's review of a relationship has got. Rejected relationships never reach a graph.
export type ReviewStatus = 'UNREVIEWED' | 'APPROVED' | 'REJECTED';

// name is as stored; displayName is how every page shows the company. The website never reformats a name.
export type CompanySummary = { id: number; name: string; displayName: string; ticker: string | null };

// form is e.g. "10-K"; filingDate is ISO text; url opens the report on the SEC website.
export type ReportSummary = { form: string; filingDate: string; url: string };

// company is the company whose report it came from (a holding, or another company that names one); counterparty is the
// other company. type SUPPLIER means the
// counterparty supplies company; CUSTOMER means it buys from company. evidence is the report's own words.
// reviewedAt (ISO text) and reviewNote are null until an Analyst decides; the note may stay empty for an approval.
// How confident TrueSight is about a relationship, worked out by the backend from the analyst's review, how much of
// the AI's quote was found in the report, and the second AI check. Each reason says what it did to the level.
export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';
export type ConfidenceEffect = 'RAISES' | 'LOWERS' | 'NONE';
export type Confidence = {
  level: ConfidenceLevel;
  reasons: { text: string; effect: ConfidenceEffect }[];
};

// The words that matter in the evidence: start is where they begin and end is just after they finish, as string indexes.
// In the order they appear, and never overlapping. Empty when the quote has none, or has not been read yet.
export type HighlightKind = 'COMPANY' | 'SUPPLIED' | 'RELIANCE' | 'FIGURE';
export type Highlight = { kind: HighlightKind; start: number; end: number };

export type Relationship = {
  id: number;
  company: CompanySummary;
  counterparty: CompanySummary;
  type: RelationshipType;
  provides: string | null;
  evidence: string;
  source: RelationshipSource;
  foundBy: FoundBy;
  criticality: Criticality | null;
  criticalityEvidence: string | null;
  report: ReportSummary;
  reviewStatus: ReviewStatus;
  reviewedAt: string | null;
  reviewNote: string | null;
  confidence: Confidence;
  highlights: Highlight[];
  // The same relationship as the other company's own report states it; absent or null when only this report names it.
  agreesWith?: { id: number; evidence: string; report: ReportSummary } | null;
  // The latest revenue or purchases share the report's own tags give for this pair; absent or null when none does.
  figure?: { share: number; measure: 'REVENUE' | 'PURCHASES'; periodEnd: string } | null;
};

export type CompanyOption = { id: number; name: string; displayName: string };

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
