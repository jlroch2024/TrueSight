// Small images made in a test, as the pixels of a logo (RGBA, row by row), for the tests of how a logo sits on its tile
// (graph/logos.test.ts and graph/camera.test.ts). They are made here, not saved Logo.dev images.
export type Rgba = [number, number, number, number];

export const CLEAR: Rgba = [0, 0, 0, 0];
// Clear too, though its colour is white, as a PNG's clear pixels often are.
export const CLEAR_WHITE: Rgba = [255, 255, 255, 0];
export const BLACK: Rgba = [0, 0, 0, 255];
export const WHITE: Rgba = [255, 255, 255, 255];

/** Every pixel the same colour and opacity, except a centre square of another. */
export function pixels(size: number, edge: Rgba, middle: Rgba) {
  const data = new Uint8ClampedArray(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const inside = x > size / 4 && x < (size * 3) / 4 && y > size / 4 && y < (size * 3) / 4;
      data.set(inside ? middle : edge, (y * size + x) * 4);
    }
  }
  return data;
}

/** A 64-pixel image on a background, with a square from 12 to 52 and a bar across the square's middle. */
export function image(background: Rgba, square: Rgba, bar: Rgba) {
  const size = 64;
  const data = new Uint8ClampedArray(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const inSquare = x >= 12 && x < 52 && y >= 12 && y < 52;
      const onBar = inSquare && y >= 28 && y < 36 && x >= 20 && x < 44;
      data.set(onBar ? bar : inSquare ? square : background, (y * size + x) * 4);
    }
  }
  return data;
}

/** A black frame with a black bar inside it (as General Motors' logo), clear everywhere else. */
export function blackFrameOnClear() {
  const size = 64;
  const data = new Uint8ClampedArray(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const inSquare = x >= 12 && x < 52 && y >= 12 && y < 52;
      const onFrame = inSquare && (x < 17 || x >= 47 || y < 17 || y >= 47);
      const onBar = inSquare && y >= 28 && y < 36 && x >= 22 && x < 42;
      data.set(onFrame || onBar ? BLACK : CLEAR_WHITE, (y * size + x) * 4);
    }
  }
  return data;
}

/** A black square on a clear background with a white mark that covers {@code share} of the square (0 to 1). */
export function blackSquareWithWhiteMark(share: number) {
  const size = 64;
  const side = Math.max(1, Math.round(Math.sqrt(share * 40 * 40)));
  const data = new Uint8ClampedArray(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const inSquare = x >= 12 && x < 52 && y >= 12 && y < 52;
      const onMark = inSquare && x >= 30 && x < 30 + side && y >= 30 && y < 30 + side;
      data.set(onMark ? WHITE : inSquare ? BLACK : CLEAR_WHITE, (y * size + x) * 4);
    }
  }
  return data;
}
