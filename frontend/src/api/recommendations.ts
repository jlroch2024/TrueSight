import { api } from './client';
import type { EventType } from './alerts';
import type { Severity } from './news';

export type Recommendation = {
  id: number;
  status: 'PENDING' | 'APPROVED' | 'WITHHELD';
  text: string;
  editedText: string | null;
  note: string | null;
  createdAt: string;
  event: {
    title: string;
    source: string | null;
    publishedAt: string;
    url: string;
    eventType: EventType;
    severity: Severity;
    summary: string | null;
    reason: string;
  } | null;
};

export const recommendationApi = {
  list: () => api<Recommendation[]>('/recommendations?status=PENDING'),
  decide: (id: number, decision: 'APPROVED' | 'WITHHELD', text: string, note: string) =>
    api<Recommendation>(`/recommendations/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ decision, text, note }),
    }),
};
