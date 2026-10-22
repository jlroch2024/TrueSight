// Logos for the living graph, from Logo.dev, each looked at once it arrives so every tile looks alike. First any margin
// built into the image is trimmed off (Logo.dev's images differ: some are a mark on a wide white or clear border, some
// fill their square). Then:
// - a logo whose own background is a solid colour (most app-style marks, or AMD's black square once its white border is
//   trimmed) fills the whole tile, edge to edge, in that colour;
// - a light logo on a clear background (white lettering) sits on a dark tile, where it can be seen;
// - any other logo sits on a white tile, at one size whatever margin its image had.
// A company Logo.dev does not know (it answers "not found"), or every company when there is no token, shows its
// initials instead (see the renderer).
import type { LiveNode } from './model';

const LOGO_HOST = 'https://img.logo.dev';
// 128 px, twice over for sharp screens, as PNG so a clear background stays clear; "not found" rather than Logo.dev's
// own letter, so TrueSight can draw its own.
const LOGO_OPTIONS = 'size=128&retina=true&format=png&fallback=404';

export type Treatment = 'bleed' | 'light' | 'plain';
/** The part of a logo's image worth drawing, as shares of its width and height (0 to 1). */
export type Crop = { x: number; y: number; w: number; h: number };
/** How a logo sits on its tile: the treatment, the tile's colour, and the part of the image to draw. */
export type LogoLook = { treatment: Treatment; background: string; crop: Crop };
export type Logo =
  { state: 'loading' } | { state: 'missing' } | ({ state: 'ready'; image: CanvasImageSource } & LogoLook);

const WHOLE: Crop = { x: 0, y: 0, w: 1, h: 1 };

/** Where a company's logo is: by ticker, or by its website's domain; null when there is neither, or no token. */
export function logoSource(node: Pick<LiveNode, 'ticker'>, token: string, domain?: string): string | null {
  if (!token) return null;
  const path = node.ticker ? `ticker/${encodeURIComponent(node.ticker)}` : domain ? encodeURIComponent(domain) : null;
  return path ? `${LOGO_HOST}/${path}?token=${encodeURIComponent(token)}&${LOGO_OPTIONS}` : null;
}

/** A colour's brightness as the eye sees it, from 0 (black) to 1 (white). */
export function luminance(r: number, g: number, b: number): number {
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
}

type Pixel = readonly [number, number, number, number];

const difference = (a: Pixel, b: Pixel) => Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);

/** Whether four pixels are all solid and alike: a background of one colour. */
function solidAndAlike(corners: Pixel[]): boolean {
  return corners.every((corner) => corner[3] > 240 && difference(corner, corners[0]!) < 30);
}

/**
 * How a logo should sit on its tile, from its pixels (RGBA, row by row, width × height). Its margin (whatever matches
 * its corners, white or clear) is trimmed off first; then, if what is left is itself a solid block of one colour, or
 * the image's own background is a solid colour other than white, it fills the tile on that colour ("bleed"); if what
 * is drawn is nearly white, it needs a dark tile ("light"); otherwise it goes on a white tile ("plain").
 */
export function treatmentOf(pixels: Uint8ClampedArray, width: number, height: number): LogoLook {
  const at = (x: number, y: number): Pixel => {
    const i = (y * width + x) * 4;
    return [pixels[i]!, pixels[i + 1]!, pixels[i + 2]!, pixels[i + 3]!];
  };
  const inset = Math.max(1, Math.round(width * 0.03));
  const corners = [
    at(inset, inset),
    at(width - 1 - inset, inset),
    at(inset, height - 1 - inset),
    at(width - 1 - inset, height - 1 - inset),
  ];

  // The background, from the whole border rather than its corners (a mark can reach a corner, as AMD's arrow does):
  // clear when most of the border is clear, or the colour most of the border shares when that is solid.
  const border: Pixel[] = [];
  for (let i = inset; i < width - inset; i++) border.push(at(i, inset), at(i, height - 1 - inset));
  for (let i = inset; i < height - inset; i++) border.push(at(inset, i), at(width - 1 - inset, i));
  const clear = border.filter((pixel) => pixel[3] < 24).length >= border.length * 0.7;
  const opaque = border.filter((pixel) => pixel[3] > 240);
  let background: Pixel = corners[0]!;
  let sharing = 0;
  for (const candidate of opaque.filter((_, index) => index % 4 === 0)) {
    const alike = opaque.filter((pixel) => difference(pixel, candidate) < 30).length;
    if (alike > sharing) {
      sharing = alike;
      background = candidate;
    }
  }
  const solid = !clear && sharing >= border.length * 0.7;
  // An image that fills its whole square, though not in one flat colour (AMD's dark gradient): its border is nearly all
  // opaque, and not mostly white.
  const whiteBorder = border.filter((pixel) => pixel[3] > 240 && luminance(pixel[0], pixel[1], pixel[2]) > 0.93);
  const filled = !clear && opaque.length >= border.length * 0.9 && whiteBorder.length < border.length * 0.5;
  const average = (pixels: Pixel[]) =>
    [0, 1, 2].map((channel) => Math.round(pixels.reduce((sum, pixel) => sum + pixel[channel]!, 0) / pixels.length));
  const isLogo = (pixel: Pixel) =>
    clear ? pixel[3] > 24 : solid ? pixel[3] < 200 || difference(pixel, background) > 40 : pixel[3] > 24;

  // The logo's extent, its margin trimmed off.
  let x1 = width;
  let y1 = height;
  let x2 = -1;
  let y2 = -1;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (!isLogo(at(x, y))) continue;
      x1 = Math.min(x1, x);
      y1 = Math.min(y1, y);
      x2 = Math.max(x2, x);
      y2 = Math.max(y2, y);
    }
  }
  const trimmed: Crop =
    x2 < 0 ? WHOLE : { x: x1 / width, y: y1 / height, w: (x2 - x1 + 1) / width, h: (y2 - y1 + 1) / height };

  // What is left is a solid block of its own (AMD's black square on a white border): it fills the tile, in its colour.
  if (x2 >= 0 && x2 - x1 > width * 0.3 && y2 - y1 > height * 0.3) {
    const edge = Math.max(1, Math.round((x2 - x1) * 0.06));
    const inner = [
      at(x1 + edge, y1 + edge),
      at(x2 - edge, y1 + edge),
      at(x1 + edge, y2 - edge),
      at(x2 - edge, y2 - edge),
    ];
    const square = Math.abs(x2 - x1 - (y2 - y1)) <= Math.max(2, (x2 - x1) * 0.12);
    // A block is only the logo's own background if something is drawn on it, as on AMD's: a bare square is a mark.
    let marked = 0;
    for (let y = y1 + edge; y <= y2 - edge; y++) {
      for (let x = x1 + edge; x <= x2 - edge; x++) {
        if (difference(at(x, y), inner[0]!) > 60) marked++;
      }
    }
    const drawnOn = marked > (x2 - x1) * (y2 - y1) * 0.03;
    if (square && drawnOn && solidAndAlike(inner) && (clear || difference(inner[0]!, background) > 40)) {
      const [r, g, b] = inner[0]!;
      return { treatment: 'bleed', background: `rgb(${r}, ${g}, ${b})`, crop: trimmed };
    }
  }
  // Its own background is a solid colour (Tesla's red): the whole image fills the tile, on that colour.
  if (solid && luminance(background[0], background[1], background[2]) <= 0.96) {
    const [r, g, b] = background;
    return { treatment: 'bleed', background: `rgb(${r}, ${g}, ${b})`, crop: WHOLE };
  }
  // It fills its square, on a background of its own that is not one colour: it fills the tile, on its border's colour.
  if (filled) {
    const [r, g, b] = average(opaque);
    return { treatment: 'bleed', background: `rgb(${r}, ${g}, ${b})`, crop: WHOLE };
  }

  // A mark on white or clear: how bright what is drawn is.
  let total = 0;
  let count = 0;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const pixel = at(x, y);
      if (!isLogo(pixel) || pixel[3] < 128) continue;
      total += luminance(pixel[0], pixel[1], pixel[2]);
      count++;
    }
  }
  if (clear && count > 0 && total / count > 0.86) return { treatment: 'light', background: '#111827', crop: trimmed };
  return { treatment: 'plain', background: '#ffffff', crop: trimmed };
}

/**
 * Loads logos once each, and keeps them: asked for a company's logo, it answers what it has (loading, missing or
 * ready) and starts loading the first time. onChange is called whenever one arrives, so the graph can redraw.
 */
export class LogoBook {
  private readonly logos = new Map<string, Logo>();

  constructor(private readonly onChange: () => void) {}

  get(source: string | null): Logo {
    if (!source) return { state: 'missing' };
    const known = this.logos.get(source);
    if (known) return known;
    this.logos.set(source, { state: 'loading' });
    if (typeof Image === 'undefined') return { state: 'loading' };
    const image = new Image();
    // Logo.dev allows reading its images from a page, which looking at a logo's pixels needs.
    image.crossOrigin = 'anonymous';
    image.decoding = 'async';
    image.onload = () => {
      this.logos.set(source, { state: 'ready', image, ...this.look(image) });
      this.onChange();
    };
    image.onerror = () => {
      this.logos.set(source, { state: 'missing' });
      this.onChange();
    };
    image.src = source;
    return { state: 'loading' };
  }

  /** How a loaded logo should sit; a plain white tile when its pixels cannot be read. */
  private look(image: HTMLImageElement): LogoLook {
    try {
      const size = 64;
      const canvas = document.createElement('canvas');
      canvas.width = size;
      canvas.height = size;
      const context = canvas.getContext('2d', { willReadFrequently: true });
      if (!context) return { treatment: 'plain', background: '#ffffff', crop: WHOLE };
      context.drawImage(image, 0, 0, size, size);
      return treatmentOf(context.getImageData(0, 0, size, size).data, size, size);
    } catch {
      return { treatment: 'plain', background: '#ffffff', crop: WHOLE };
    }
  }
}
