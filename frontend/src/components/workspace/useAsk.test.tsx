// The conversation behind Ask TrueSight: what is sent back as context, and when it starts again. The backend is a fake
// that answers each request when the test says so.
import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStore } from '../../api/client';
import { useAsk } from './useAsk';

type Pending = { body: { question: string; history: { question: string; answer: string }[] }; url: string; reply: (response: Response) => void };
let requests: Pending[];

const answer = (text: string) => new Response(JSON.stringify({ answer: text, sources: [] }));
const failure = (message: string) => new Response(JSON.stringify({ message }), { status: 502 });

beforeEach(() => {
  tokenStore.set('a-token');
  requests = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(
      (url: string, init: RequestInit) =>
        new Promise<Response>((resolve) => {
          requests.push({ url, body: JSON.parse(init.body as string), reply: resolve });
        }),
    ),
  );
});

afterEach(() => tokenStore.clear());

/** Asks, and answers it at once. */
async function askAndAnswer(ask: (text: string) => Promise<void>, question: string, reply: Response) {
  let asking: Promise<void>;
  act(() => {
    asking = ask(question);
  });
  await waitFor(() => expect(requests.some((request) => request.body.question === question)).toBe(true));
  await act(async () => {
    requests.find((request) => request.body.question === question)!.reply(reply);
    await asking;
  });
}

describe('useAsk', () => {
  it('asksTheOpenPortfolioAndKeepsTheQuestionWithItsAnswer', async () => {
    const { result } = renderHook(() => useAsk('7'));

    await askAndAnswer(result.current.ask, 'Who supplies NVIDIA?', answer('TSMC (unreviewed).'));

    expect(requests[0]!.url).toBe('/api/portfolios/7/questions');
    expect(result.current.exchanges).toHaveLength(1);
    expect(result.current.exchanges[0]).toMatchObject({ question: 'Who supplies NVIDIA?', pending: false });
    expect(result.current.exchanges[0]!.answer!.answer).toBe('TSMC (unreviewed).');
  });

  it('neverSendsARefusalBackAsContextSoItCannotTeachTheNextAnswerToRefuse', async () => {
    const { result } = renderHook(() => useAsk('7'));

    await askAndAnswer(result.current.ask, 'Who is the biggest customer?', answer("TrueSight's data does not answer that."));
    await askAndAnswer(result.current.ask, 'Who supplies NVIDIA?', answer('TSMC (unreviewed).'));
    await askAndAnswer(result.current.ask, 'How many memory suppliers?', answer('Three (unreviewed).'));

    expect(requests[2]!.body.history).toEqual([{ question: 'Who supplies NVIDIA?', answer: 'TSMC (unreviewed).' }]);
  });

  it('showsAQuestionAsPendingAndBlocksAnotherUntilItIsAnswered', async () => {
    const { result } = renderHook(() => useAsk('7'));

    act(() => void result.current.ask('First?'));
    await waitFor(() => expect(result.current.sending).toBe(true));
    act(() => void result.current.ask('Second?'));

    expect(requests).toHaveLength(1);
    expect(result.current.exchanges).toHaveLength(1);
    await act(async () => requests[0]!.reply(answer('Done (unreviewed).')));
    await waitFor(() => expect(result.current.sending).toBe(false));
  });

  it('ignoresABlankQuestionAndOneOverFiveHundredCharacters', async () => {
    const { result } = renderHook(() => useAsk('7'));

    act(() => void result.current.ask('   '));
    act(() => void result.current.ask('x'.repeat(501)));

    expect(requests).toHaveLength(0);
    expect(result.current.exchanges).toHaveLength(0);
  });

  it('sendsOnlyTheLastSixAnsweredQuestionsBackAsContext', async () => {
    const { result } = renderHook(() => useAsk('7'));
    for (let i = 1; i <= 8; i++) {
      await askAndAnswer(result.current.ask, `Question ${i}`, answer(`Answer ${i} (unreviewed).`));
    }

    await askAndAnswer(result.current.ask, 'Question 9', answer('Answer 9 (unreviewed).'));

    const history = requests.find((request) => request.body.question === 'Question 9')!.body.history;
    expect(history.map((turn) => turn.question)).toEqual([
      'Question 3', 'Question 4', 'Question 5', 'Question 6', 'Question 7', 'Question 8',
    ]);
    expect(history[0]!.answer).toBe('Answer 3 (unreviewed).');
  });

  it('doesNotSendAQuestionThatFailedAsContext', async () => {
    const { result } = renderHook(() => useAsk('7'));
    await askAndAnswer(result.current.ask, 'Fails?', failure('The AI could not be reached. Try again later.'));
    expect(result.current.exchanges[0]).toMatchObject({ error: 'The AI could not be reached. Try again later.', pending: false });

    await askAndAnswer(result.current.ask, 'Works?', answer('Yes (unreviewed).'));

    expect(requests.find((request) => request.body.question === 'Works?')!.body.history).toEqual([]);
  });

  it('asksAFailedQuestionAgainInPlaceOfTheFailedAttempt', async () => {
    const { result } = renderHook(() => useAsk('7'));
    await askAndAnswer(result.current.ask, 'Retry me?', failure('The AI could not be reached. Try again later.'));
    const failed = result.current.exchanges[0]!;

    act(() => result.current.retry(failed.id));
    await waitFor(() => expect(requests).toHaveLength(2));
    await act(async () => requests[1]!.reply(answer('Now (unreviewed).')));

    await waitFor(() => expect(result.current.exchanges).toHaveLength(1));
    expect(result.current.exchanges[0]!.question).toBe('Retry me?');
    expect(result.current.exchanges[0]!.error).toBeUndefined();
    expect(result.current.exchanges[0]!.answer!.answer).toBe('Now (unreviewed).');
  });

  it('startsAgainForAnotherPortfolioAndIgnoresAnAnswerStillOnItsWayForTheOldOne', async () => {
    const { result, rerender } = renderHook(({ id }) => useAsk(id), { initialProps: { id: '7' } });
    await askAndAnswer(result.current.ask, 'Old?', answer('Old answer (unreviewed).'));
    act(() => void result.current.ask('Still asking?'));
    await waitFor(() => expect(requests).toHaveLength(2));

    rerender({ id: '9' });
    await waitFor(() => expect(result.current.exchanges).toHaveLength(0));
    await act(async () => requests[1]!.reply(answer('Too late (unreviewed).')));

    expect(result.current.exchanges).toHaveLength(0);
    // The new conversation has no context from the old one.
    await askAndAnswer(result.current.ask, 'New?', answer('New (unreviewed).'));
    const sent = requests.find((request) => request.body.question === 'New?')!;
    expect(sent.url).toBe('/api/portfolios/9/questions');
    expect(sent.body.history).toEqual([]);
  });
});
