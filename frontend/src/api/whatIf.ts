// The Disruption Simulator: what happens to a portfolio if one company stops supplying. Worked out when asked, never
// saved.
import { api } from './client';
import type { CompanySummary, Relationship } from './relationships';

// path is the ids of the relationships from the stopped company to the holding: one for a holding it supplies, two
// for a holding reached in two steps (indirect), none when the stopped company is the holding itself.
export type WhatIfHolding = {
  holdingId: number;
  ticker: string;
  weight: number | null;
  indirect: boolean;
  path: number[];
};

// exposure is the total weight of the holdings reached (12.5 means 12.5%) when weighted, and how many holdings are
// reached otherwise, out of holdingCount. links is every relationship on a path; criticalLinks is those of them the
// report calls a sole or single source. assumptions is the line to show with every result.
export type WhatIf = {
  company: CompanySummary;
  holdings: WhatIfHolding[];
  exposure: number;
  weighted: boolean;
  holdingCount: number;
  criticalLinks: Relationship[];
  links: Relationship[];
  assumptions: string;
};

export const whatIfApi = {
  get: (portfolioId: string, companyId: number) =>
    api<WhatIf>(`/portfolios/${portfolioId}/what-if?companyId=${companyId}`),
};
