// The conversation with Ask TrueSight: the questions asked in this browser tab and the answers to them. It lives here
// (in the page, not the panel) so it survives the panel coming and going, and it starts again for another portfolio.
// Nothing is saved: closing the tab ends it.
import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { ASK_FIXED_ANSWERS, ASK_HISTORY_MAX, ASK_QUESTION_MAX, type AskAnswer, askApi } from '../../api/ask';

export type AskExchange = {
  id: number;
  question: string;
  // Set once the answer has come back.
  answer?: AskAnswer;
  // Set if asking failed: ready to show.
  error?: string;
  pending: boolean;
};

export function useAsk(portfolioId: string) {
  const [exchanges, setExchanges] = useState<AskExchange[]>([]);
  const latest = useRef(exchanges);
  latest.current = exchanges;
  const openPortfolio = useRef(portfolioId);
  const nextId = useRef(1);

  // Another portfolio is another conversation; an answer still on its way for the old one is ignored.
  useEffect(() => {
    openPortfolio.current = portfolioId;
    setExchanges([]);
  }, [portfolioId]);

  const sending = exchanges.some((exchange) => exchange.pending);

  const ask = useCallback(
    async (text: string) => {
      const question = text.trim();
      if (!question || question.length > ASK_QUESTION_MAX || latest.current.some((exchange) => exchange.pending)) return;
      const id = nextId.current++;
      // The last 6 questions that were answered; one that failed was never answered, so it is not context. Nor is a
      // fixed refusal: sent back, it led Gemini to refuse the next question too, even one its records answer.
      const history = latest.current
        .filter((exchange) => exchange.answer && !ASK_FIXED_ANSWERS.includes(exchange.answer.answer))
        .slice(-ASK_HISTORY_MAX)
        .map((exchange) => ({ question: exchange.question, answer: exchange.answer!.answer }));
      const asked = portfolioId;
      setExchanges((all) => [...all, { id, question, pending: true }]);
      const settle = (change: Partial<AskExchange>) => {
        if (openPortfolio.current !== asked) return;
        setExchanges((all) => all.map((exchange) => (exchange.id === id ? { ...exchange, ...change, pending: false } : exchange)));
      };
      try {
        settle({ answer: await askApi.ask(asked, question, history) });
      } catch (e) {
        settle({ error: e instanceof ApiError ? e.message : 'Something went wrong.' });
      }
    },
    [portfolioId],
  );

  // Asks a failed question again, in the place of the failed attempt.
  const retry = useCallback(
    (id: number) => {
      const failed = latest.current.find((exchange) => exchange.id === id);
      if (!failed || latest.current.some((exchange) => exchange.pending)) return;
      setExchanges((all) => all.filter((exchange) => exchange.id !== id));
      void ask(failed.question);
    },
    [ask],
  );

  return { exchanges, sending, ask, retry };
}
