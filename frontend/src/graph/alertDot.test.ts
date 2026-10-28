// The beacon a company with open alerts carries at its tile's corner. jsdom cannot draw on a <canvas>, so the drawing is
// watched on a stand-in that remembers the shape filled, the colours its gradients run to, and the rim's colour.
import { describe, expect, it } from 'vitest';
import { ALERT_COLOUR } from './model';
import { INK, drawAlertBeacon } from './render';

function fakeContext() {
  const gradients: string[][] = [];
  const strokes: string[] = [];
  let path: { x: number; y: number }[] = [];
  const filled: { x: number; y: number }[][] = [];
  const gradient = () => {
    const stops: string[] = [];
    gradients.push(stops);
    return { addColorStop: (_: number, colour: string) => stops.push(colour) };
  };
  const context = {
    fillStyle: '' as unknown,
    strokeStyle: '',
    lineWidth: 0,
    globalCompositeOperation: 'source-over',
    save: () => {},
    restore: () => {},
    beginPath: () => {
      path = [];
    },
    moveTo: (x: number, y: number) => path.push({ x, y }),
    lineTo: (x: number, y: number) => path.push({ x, y }),
    closePath: () => {},
    createRadialGradient: gradient,
    createLinearGradient: gradient,
    fillRect: () => {},
    strokeRect: () => {},
    fill: () => filled.push(path),
    stroke: () => strokes.push(context.strokeStyle),
  };
  return { context: context as unknown as CanvasRenderingContext2D, gradients, strokes, filled };
}

describe('drawAlertBeacon', () => {
  it.each(['HIGH', 'MEDIUM', 'LOW'] as const)('lights the beacon in the colour of a %s alert, at the corner given', (severity) => {
    const { context, gradients, filled } = fakeContext();

    drawAlertBeacon(context, 40, 25, severity, 0, true);

    expect(gradients.at(-1)!.at(-1)).toBe(ALERT_COLOUR[severity]);
    const face = filled.at(-1)!;
    const xs = face.map((point) => point.x);
    const ys = face.map((point) => point.y);
    expect((Math.min(...xs) + Math.max(...xs)) / 2).toBeCloseTo(40);
    expect((Math.min(...ys) + Math.max(...ys)) / 2).toBeCloseTo(25);
  });

  it('rims the beacon in the night colour so it stands out against a logo', () => {
    const { context, strokes } = fakeContext();

    drawAlertBeacon(context, 0, 0, 'HIGH', 0, true);

    expect(strokes.at(-1)).toBe(INK.night);
  });
});
