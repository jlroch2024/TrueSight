// An event's marks: its kind on a tile in its severity's colour, three rising bars for how serious, three squares for
// how sure.
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Confidence, EventMark, SeverityBars } from './EventMark';

const lit = (element: Element) => element.querySelectorAll('i.on').length;

describe('event marks', () => {
  it('lightsOneTwoOrThreeBarsForLowMediumAndHigh', () => {
    for (const [severity, bars] of [['LOW', 1], ['MEDIUM', 2], ['HIGH', 3]] as const) {
      const { container, unmount } = render(<SeverityBars severity={severity} />);
      expect(lit(container.firstElementChild!)).toBe(bars);
      expect(container.firstElementChild).toHaveAttribute('data-severity', severity);
      unmount();
    }
  });

  it('fillsOneTwoOrThreeSquaresForHowSure', () => {
    const { container } = render(<Confidence level="MEDIUM" />);

    expect(lit(container.firstElementChild!)).toBe(2);
  });

  it('drawsTheEventsKindInItsSeveritysColourWithOneEvenEdge', () => {
    const { container } = render(<EventMark type="LABOUR_ACTION" severity="HIGH" size={40} />);
    const mark = container.firstElementChild!;

    expect(mark).toHaveAttribute('data-severity', 'HIGH');
    expect(mark).toHaveAttribute('aria-hidden', 'true');
    // The edge follows the outline, chamfer included, half a pixel inside it.
    expect(mark.querySelector('.event-mark-edge path')!.getAttribute('d')).toMatch(/^M0\.5 0\.5H/);
  });
});
