// The dot a company with open alerts carries at its tile's corner. jsdom cannot draw on a <canvas>, so the drawing is
// watched on a stand-in that remembers what was filled, and in which colour.
import { describe, expect, it } from 'vitest';
import { ALERT_COLOUR } from './model';
import { INK, drawAlertDot } from './render';

function fakeContext() {
  const filled: { colour: string; x: number; y: number }[] = [];
  let arcAt = { x: 0, y: 0 };
  const context = {
    fillStyle: '',
    strokeStyle: '',
    lineWidth: 0,
    beginPath: () => {},
    arc: (x: number, y: number) => {
      arcAt = { x, y };
    },
    fill: () => filled.push({ colour: context.fillStyle, ...arcAt }),
    stroke: () => {},
  };
  return { context: context as unknown as CanvasRenderingContext2D, filled };
}

describe('drawAlertDot', () => {
  it.each(['HIGH', 'MEDIUM', 'LOW'] as const)('fills the dot in the colour of a %s alert, at the corner given', (severity) => {
    const { context, filled } = fakeContext();

    drawAlertDot(context, 40, 25, severity);

    expect(filled).toEqual([{ colour: ALERT_COLOUR[severity], x: 40, y: 25 }]);
  });

  it('rims the dot in the night colour so it stands out against a logo', () => {
    const { context } = fakeContext();

    drawAlertDot(context, 0, 0, 'HIGH');

    expect((context as unknown as { strokeStyle: string }).strokeStyle).toBe(INK.night);
  });
});
