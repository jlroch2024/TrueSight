// Ask TrueSight's panel on its own: what it shows for each state, and what it sends up. How it sits in the workspace, and
// what its source buttons open, is tested in WorkspacePage.test.tsx.
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { AskSource } from '../../api/ask';
import { AskPanel } from './AskPanel';
import type { AskExchange } from './useAsk';

const TSMC_NVIDIA: AskSource = { kind: 'RELATIONSHIP', id: 1, label: 'TSMC supplies NVIDIA' };

function panel(exchanges: AskExchange[] = [], overrides: Partial<Parameters<typeof AskPanel>[0]> = {}) {
  const handlers = { onAsk: vi.fn(), onRetry: vi.fn(), onOpen: vi.fn(), canOpen: vi.fn(() => true) };
  const view = render(<AskPanel exchanges={exchanges} sending={false} left={404} right={12} {...handlers} {...overrides} />);
  return { ...view, ...handlers };
}

const answered = (id: number, question: string, answer: string, sources: AskSource[] = []): AskExchange => ({
  id,
  question,
  answer: { answer, sources },
  pending: false,
});

describe('Ask TrueSight panel', () => {
  it('hasALabelledBoxThatTakesAtMostFiveHundredCharacters', () => {
    panel();

    const box = screen.getByRole('textbox', { name: 'Ask TrueSight' });
    expect(box).toHaveAttribute('maxlength', '500');
    expect(screen.getByRole('region', { name: 'Ask TrueSight' })).toHaveStyle({ left: '404px', right: '12px' });
  });

  it('isTheEmptyStateUntilAQuestionIsAskedAPhraseAndAQuestionToStartWith', async () => {
    const { onAsk } = panel();

    expect(screen.getByPlaceholderText('Ask About Your Portfolio')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Which Holdings Share a Supplier' }));

    expect(onAsk).toHaveBeenCalledWith('Which of my holdings share a supplier?');
  });

  it('offersNothingToSuggestOnceAQuestionHasBeenAsked', () => {
    panel([answered(1, 'Who?', 'Two foundries (unreviewed).')]);

    expect(screen.queryByRole('button', { name: 'Which Holdings Share a Supplier' })).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText('Ask About Your Portfolio')).not.toBeInTheDocument();
  });

  it('sendsTheQuestionAndClearsTheBoxOnEnterOrTheAskButton', async () => {
    const { onAsk } = panel();

    await userEvent.type(screen.getByRole('textbox', { name: 'Ask TrueSight' }), 'Who supplies AMD?{Enter}');

    expect(onAsk).toHaveBeenCalledWith('Who supplies AMD?');
    expect(screen.getByRole('textbox', { name: 'Ask TrueSight' })).toHaveValue('');
  });

  it('cannotSendABlankQuestionOrWhileAnotherIsBeingAnswered', async () => {
    const { onAsk, rerender, ...rest } = panel();
    expect(screen.getByRole('button', { name: 'Ask' })).toBeDisabled();

    await userEvent.type(screen.getByRole('textbox', { name: 'Ask TrueSight' }), 'Who?');
    expect(screen.getByRole('button', { name: 'Ask' })).toBeEnabled();
    rerender(<AskPanel exchanges={[]} sending left={404} right={12} onAsk={onAsk} onRetry={rest.onRetry} canOpen={rest.canOpen} onOpen={rest.onOpen} />);

    expect(screen.getByRole('button', { name: 'Asking' })).toBeDisabled();
    fireEvent.submit(screen.getByRole('textbox', { name: 'Ask TrueSight' }).closest('form')!);
    expect(onAsk).not.toHaveBeenCalled();
  });

  it('showsTheQuestionThenItsAnswerWithOneButtonPerSource', async () => {
    const sources: AskSource[] = [TSMC_NVIDIA, { kind: 'HOLDING', id: 2, label: 'NVIDIA (NVDA)' }];
    const { onOpen } = panel([answered(1, 'Who supplies NVIDIA?', 'TSMC does, and it is unreviewed.', sources)]);

    const log = screen.getByRole('log', { name: 'Conversation' });
    expect(within(log).getByText('Who supplies NVIDIA?')).toBeInTheDocument();
    expect(within(log).getByText('TSMC does, and it is unreviewed.')).toBeInTheDocument();
    const buttons = within(screen.getByRole('list', { name: 'Sources' })).getAllByRole('button');
    expect(buttons.map((button) => button.textContent)).toEqual(['TSMC supplies NVIDIA', 'NVIDIA (NVDA)']);
    await userEvent.click(buttons[1]!);

    expect(onOpen).toHaveBeenCalledWith(sources[1]);
  });

  it('showsNoSourceListForTheAnswerThatHasNone', () => {
    panel([answered(1, 'What was the revenue?', "TrueSight's data does not answer that.")]);

    expect(screen.getByText("TrueSight's data does not answer that.")).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Sources' })).not.toBeInTheDocument();
  });

  it('disablesASourceThatCanNoLongerBeOpened', () => {
    const canOpen = vi.fn(() => false);
    panel([answered(1, 'Who?', 'It is unreviewed.', [TSMC_NVIDIA])], { canOpen });

    const button = screen.getByRole('button', { name: 'TSMC supplies NVIDIA' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('title', 'No Longer Available');
    expect(canOpen).toHaveBeenCalledWith(TSMC_NVIDIA);
  });

  it('saysItIsThinkingWhileAnAnswerIsOnItsWay', () => {
    panel([{ id: 1, question: 'Who?', pending: true }], { sending: true });

    expect(screen.getByText('Thinking')).toBeInTheDocument();
  });

  it('showsWhyAQuestionFailedWithRetry', async () => {
    const { onRetry } = panel([{ id: 4, question: 'Who?', error: 'The AI could not be reached. Try again later.', pending: false }]);

    expect(screen.getByRole('alert')).toHaveTextContent('The AI could not be reached. Try again later.');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(onRetry).toHaveBeenCalledWith(4);
  });

  it('hidesAndShowsTheConversationWithoutLosingIt', async () => {
    panel([answered(1, 'Who?', 'Two foundries (unreviewed).')]);

    await userEvent.click(screen.getByRole('button', { name: 'Hide the Conversation' }));
    expect(screen.queryByRole('log')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Show the Conversation' }));

    expect(screen.getByText('Two foundries (unreviewed).')).toBeInTheDocument();
  });

  it('keepsEscapeToItselfSoItDoesNotCloseWhatIsBehindIt', async () => {
    panel([answered(1, 'Who?', 'Two foundries (unreviewed).')]);
    const reachedTheWindow = vi.fn();
    window.addEventListener('keydown', reachedTheWindow);

    await userEvent.type(screen.getByRole('textbox', { name: 'Ask TrueSight' }), '{Escape}');
    window.removeEventListener('keydown', reachedTheWindow);

    expect(reachedTheWindow).not.toHaveBeenCalled();
    expect(screen.queryByRole('log')).not.toBeInTheDocument();
  });

  it('opensTheConversationAgainWhenAnotherQuestionIsAsked', () => {
    const { rerender, ...rest } = panel([answered(1, 'Who?', 'Two foundries (unreviewed).')]);
    fireEvent.click(screen.getByRole('button', { name: 'Hide the Conversation' }));
    expect(screen.queryByRole('log')).not.toBeInTheDocument();

    rerender(
      <AskPanel
        exchanges={[answered(1, 'Who?', 'Two foundries (unreviewed).'), { id: 2, question: 'And?', pending: true }]}
        sending
        left={404}
        right={12}
        onAsk={rest.onAsk}
        onRetry={rest.onRetry}
        canOpen={rest.canOpen}
        onOpen={rest.onOpen}
      />,
    );

    expect(screen.getByText('And?')).toBeInTheDocument();
  });
});
