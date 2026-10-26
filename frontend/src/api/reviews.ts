// The calls for the Analyst's review of the relationships the AI found. Analysts only: anyone else is answered 403.
import { api } from './client';
import type { Relationship, ReviewStatus } from './relationships';

export type ReviewFilter = ReviewStatus | 'ALL';

// relationship is the same shape the graph uses. reviewedBy is the email of the Analyst who made the latest decision.
// usedByPortfolios only counts the portfolios that rely on it; it never says which, or whose.
// secondCheck is what a second AI made of the relationship, and secondCheckReason why; both are null until it has been
// second-checked. The backend sends the Unreviewed ones it doubts first.
export type SecondCheck = 'AGREES' | 'DOUBTS';

export type ReviewItem = {
  relationship: Relationship;
  reviewStatus: ReviewStatus;
  reviewNote: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  usedByPortfolios: number;
  secondCheck: SecondCheck | null;
  secondCheckReason: string | null;
};

export const reviewApi = {
  list: (filter: ReviewFilter) => api<ReviewItem[]>(filter === 'ALL' ? '/reviews' : `/reviews?status=${filter}`),
  decide: (relationshipId: number, decision: 'APPROVED' | 'REJECTED', note: string) =>
    api<ReviewItem>(`/relationships/${relationshipId}/review`, {
      method: 'PUT',
      body: JSON.stringify({ decision, note }),
    }),
};
