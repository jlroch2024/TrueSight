// How much a company depends on each customer or supplier, as its annual reports tag it.
import { api } from './client';
import type { CompanySummary, ReportSummary } from './relationships';

export type ConcentrationMeasure = 'REVENUE' | 'RECEIVABLES' | 'PURCHASES' | 'OTHER';

// counterparty is null when the report does not name the company ("Customer A"); share is from 0 to 1.
export type Concentration = {
  label: string;
  counterparty: CompanySummary | null;
  kind: 'CUSTOMER' | 'SUPPLIER';
  measure: ConcentrationMeasure;
  share: number;
  periodStart: string | null;
  periodEnd: string;
  report: ReportSummary | null;
};

export const concentrationApi = {
  list: (companyId: number) => api<Concentration[]>(`/companies/${companyId}/concentrations`),
};

/** One party's figure in the latest year, with its share in each year the report tags, oldest first. */
export type Dependence = {
  key: string;
  label: string;
  counterparty: CompanySummary | null;
  kind: 'CUSTOMER' | 'SUPPLIER';
  measure: 'REVENUE' | 'PURCHASES';
  share: number;
  trend: number[];
  report: ReportSummary | null;
};

/**
 * What the Inspector shows: the latest year's revenue and purchases figures, biggest first, each with its trend.
 * Receivables and other measures are stored but never shown.
 */
export function dependenceOf(figures: Concentration[]): Dependence[] {
  const shown = figures.filter((figure) => figure.measure === 'REVENUE' || figure.measure === 'PURCHASES');
  if (shown.length === 0) return [];
  const latest = shown.reduce((newest, figure) => (figure.periodEnd > newest ? figure.periodEnd : newest), '');
  const keyOf = (figure: Concentration) => `${figure.label}|${figure.kind}|${figure.measure}`;
  return shown
    .filter((figure) => figure.periodEnd === latest)
    .map((figure) => ({
      key: keyOf(figure),
      label: figure.label,
      counterparty: figure.counterparty,
      kind: figure.kind,
      measure: figure.measure as 'REVENUE' | 'PURCHASES',
      share: Number(figure.share),
      report: figure.report,
      trend: shown
        .filter((other) => keyOf(other) === keyOf(figure))
        .sort((one, other) => one.periodEnd.localeCompare(other.periodEnd))
        .map((other) => Number(other.share)),
    }))
    .sort((one, other) => other.share - one.share);
}

/**
 * Who a figure is about, in words: the company it names, "One Customer" or "One Supplier" for an anonymous one
 * ("Customer A"), and the filing's own words for a group ("Ten Largest Customers"), which is not one company.
 */
export function partyName(item: Pick<Dependence, 'counterparty' | 'kind' | 'label'>): string {
  if (item.counterparty) return item.counterparty.displayName;
  if (/(^|[^a-z])(customers|suppliers|distributors|vendors|others|largest|top)([^a-z]|$)/i.test(item.label)) return item.label;
  return item.kind === 'CUSTOMER' ? 'One Customer' : 'One Supplier';
}

/** A share as a whole percentage: 0.91 becomes "91%". */
export function percent(share: number): string {
  return `${Math.round(share * 100)}%`;
}

/** "Revenue" or "Purchases", as a label reads it. */
export function measureLabel(measure: 'REVENUE' | 'PURCHASES'): string {
  return measure === 'REVENUE' ? 'Revenue' : 'Purchases';
}
