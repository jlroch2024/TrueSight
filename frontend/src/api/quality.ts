import { api } from './client';

// Each week is 7 whole days, weekStart to weekEnd ("2026-10-21" to "2026-10-27"). Every figure is a count; the page
// works out the shares.
type Week = { weekStart: string; weekEnd: string };

export type RelationshipWeek = Week & {
  reviewed: number;
  approved: number;
  rejected: number;
  doubtsReviewed: number;
  doubtsRejected: number;
};

export type NewsWeek = Week & {
  decided: number;
  overruled: number;
  noiseMadeEvents: number;
  eventsMadeNoise: number;
};

export type RecommendationWeek = Week & {
  decided: number;
  approvedAsWritten: number;
  edited: number;
  withheld: number;
};

export type CallWeek = Week & {
  answered: number;
  unreadable: number;
  failed: number;
  medianMs: number | null;
};

// The last 8 weeks of each AI step, oldest first.
export type Quality = {
  relationships: RelationshipWeek[];
  news: NewsWeek[];
  recommendations: RecommendationWeek[];
  calls: CallWeek[];
};

export const qualityApi = {
  get: () => api<Quality>('/quality'),
};
