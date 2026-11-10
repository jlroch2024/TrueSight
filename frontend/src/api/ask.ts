// Ask TrueSight: questions about one portfolio, answered from TrueSight's own data for it. Nothing here is saved on the
// server; the website keeps the conversation in the browser tab and sends the last 6 questions and answers back.
import { api } from './client';

// What an answer rests on: a relationship, an alert (an event, found by any of its alerts' ids) or a holding.
export type AskSourceKind = 'RELATIONSHIP' | 'ALERT' | 'HOLDING';

// id is the item's own id; label is the words its button shows.
export type AskSource = { kind: AskSourceKind; id: number; label: string };

export type AskAnswer = { answer: string; sources: AskSource[] };

export type AskTurn = { question: string; answer: string };

// The most the backend takes: a question of this many characters, and this many earlier turns.
export const ASK_QUESTION_MAX = 500;
export const ASK_HISTORY_MAX = 6;

// The backend's two fixed answers (AskGuards): they answer nothing, so they are never sent back as context.
export const ASK_FIXED_ANSWERS = [
  "TrueSight's data does not answer that.",
  'TrueSight offers research support, not investment advice, so it cannot say what to trade.',
];

export const askApi = {
  ask: (portfolioId: string | number, question: string, history: AskTurn[]) =>
    api<AskAnswer>(`/portfolios/${portfolioId}/questions`, {
      method: 'POST',
      body: JSON.stringify({ question, history }),
    }),
};
