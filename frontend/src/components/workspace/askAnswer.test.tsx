import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AskAnswerText, answerLines } from './askAnswer';

describe('how an answer is set out', () => {
  it('putsEachSentenceAndEachSemicolonClauseOnALineOfItsOwn', () => {
    expect(answerLines('Two do; three do not. It is unreviewed.')).toEqual(['Two do;', 'three do not.', 'It is unreviewed.']);
  });

  it('doesNotEndASentenceAtACompanyAbbreviation', () => {
    expect(answerLines('Apple Inc. Buys chips. It is unreviewed.')).toEqual(['Apple Inc. Buys chips.', 'It is unreviewed.']);
  });

  it('doesNotEndASentenceInsideACompanysLegalEnding', () => {
    expect(answerLines('STMicroelectronics N.V. Buys from TSMC. It is unreviewed.')).toEqual([
      'STMicroelectronics N.V. Buys from TSMC.',
      'It is unreviewed.',
    ]);
    expect(answerLines('Amkor Technology, Inc. Supplies Qualcomm.')).toEqual(['Amkor Technology, Inc. Supplies Qualcomm.']);
  });

  it('drawsSuppliesAsAnArrowAndKeepsTheWordForScreenReaders', () => {
    const { container } = render(<AskAnswerText answer="TSMC supplies NVIDIA." names={['TSMC', 'NVIDIA']} />);

    expect(container.querySelector('.ask-arrow')).toHaveTextContent('supplies');
    expect(container.querySelector('.ask-answer')).toHaveTextContent('TSMC supplies NVIDIA.');
  });

  it('leavesTheNounSupplyAsAWord', () => {
    const { container } = render(<AskAnswerText answer="A supply shortage reaches NVIDIA." names={['NVIDIA']} />);

    expect(container.querySelector('.ask-arrow')).toBeNull();
  });

  it('picksOutCompanyNamesAndFigures', () => {
    render(<AskAnswerText answer="NVIDIA is 18% of the portfolio." names={['NVIDIA']} />);

    expect(screen.getByText('NVIDIA').tagName).toBe('STRONG');
    expect(screen.getByText('18%').tagName).toBe('STRONG');
  });

  it('letsTheUnreviewedNoteStepBack', () => {
    render(<AskAnswerText answer="Two do. All of these are unreviewed." names={[]} />);

    expect(screen.getByText('All of these are unreviewed.')).toHaveClass('ask-caveat');
  });
});
