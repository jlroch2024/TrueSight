import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HighlightedQuote } from './HighlightedQuote';

/** NVIDIA's own words, from its 10-K in docs/examples. */
const QUOTE =
  'We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC, and Samsung ' +
  'Electronics Co., Ltd., or Samsung, to produce our semiconductor wafers.';

function at(phrase: string, from = 0) {
  const start = QUOTE.indexOf(phrase, from);
  return { start, end: start + phrase.length };
}

describe('HighlightedQuote', () => {
  it('marks each range in the style of its kind, and keeps the rest of the words as they are', () => {
    const { container } = render(
      <HighlightedQuote
        text={QUOTE}
        highlights={[
          { kind: 'RELIANCE', ...at('such as') },
          { kind: 'COMPANY', ...at('TSMC') },
          { kind: 'SUPPLIED', ...at('semiconductor wafers') },
        ]}
      />,
    );

    const marks = [...container.querySelectorAll('mark')];
    expect(marks.map((mark) => mark.textContent)).toEqual(['such as', 'TSMC', 'semiconductor wafers']);
    expect(marks.map((mark) => mark.className)).toEqual([
      'quote-mark reliance',
      'quote-mark company',
      'quote-mark supplied',
    ]);
    expect(container.querySelector('blockquote')).toHaveTextContent(QUOTE);
  });

  it('marks a figure in its own style', () => {
    const text = 'For fiscal year 2024, sales to one direct customer represented 13% of total revenue.';
    const start = text.indexOf('13%');
    const { container } = render(<HighlightedQuote text={text} highlights={[{ kind: 'FIGURE', start, end: start + 3 }]} />);

    expect(container.querySelector('mark')).toHaveClass('quote-mark', 'figure');
    expect(container.querySelector('mark')).toHaveTextContent('13%');
  });

  it('never searches the text: only the range sent is marked, though the word appears twice', () => {
    const second = at('Samsung', QUOTE.indexOf('or Samsung'));
    const { container } = render(<HighlightedQuote text={QUOTE} highlights={[{ kind: 'COMPANY', ...second }]} />);

    const marks = container.querySelectorAll('mark');
    expect(marks).toHaveLength(1);
    expect(marks[0]!.previousSibling?.textContent).toBe(QUOTE.slice(0, second.start));
  });

  it('shows the key of the four kinds under the quote, each in its style', () => {
    const { container } = render(<HighlightedQuote text={QUOTE} highlights={[{ kind: 'COMPANY', ...at('TSMC') }]} />);

    const key = screen.getByLabelText('Highlight Key');
    expect(container.querySelector('blockquote')!.compareDocumentPosition(key)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(within(key).getByText('Company')).toHaveClass('company');
    expect(within(key).getByText('Supplied')).toHaveClass('supplied');
    expect(within(key).getByText('Reliance')).toHaveClass('reliance');
    expect(within(key).getByText('Figure')).toHaveClass('figure');
  });

  it('shows a quote with no highlights plain, with no marks and no key', () => {
    const { container } = render(<HighlightedQuote text={QUOTE} highlights={[]} />);

    expect(container.querySelector('blockquote')).toHaveTextContent(QUOTE);
    expect(container.querySelector('mark')).toBeNull();
    expect(screen.queryByLabelText('Highlight Key')).not.toBeInTheDocument();
  });

  it('gives companies the chain colour of the holding, and leaves it to the stylesheet’s teal when there is none', () => {
    const highlights = [{ kind: 'COMPANY' as const, ...at('TSMC') }];
    const { container, rerender } = render(<HighlightedQuote text={QUOTE} highlights={highlights} companyColour="#a78bfa" />);
    const wrapper = () => container.querySelector<HTMLElement>('.highlighted-quote')!;
    expect(wrapper().style.getPropertyValue('--quote-company')).toBe('#a78bfa');

    rerender(<HighlightedQuote text={QUOTE} highlights={highlights} />);
    expect(wrapper().style.getPropertyValue('--quote-company')).toBe('');
  });

  it('keeps what else is in the quote, such as which report it is from', () => {
    render(
      <HighlightedQuote text={QUOTE} highlights={[]}>
        <footer>From NVIDIA’s 10-K</footer>
      </HighlightedQuote>,
    );

    expect(screen.getByText('From NVIDIA’s 10-K').closest('blockquote')).toHaveTextContent(QUOTE);
  });
});
