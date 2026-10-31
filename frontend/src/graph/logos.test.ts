// Which address each company's logo is asked for, what is tried when Logo.dev does not know it, and how a logo sits on
// its tile. The images are small ones made here, not saved Logo.dev images.
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LogoBook, domainFallback, isUsTicker, logoSource, treatmentOf } from './logos';

type Rgba = [number, number, number, number];

const CLEAR: Rgba = [0, 0, 0, 0];
// Clear too, though its colour is white, as a PNG's clear pixels often are.
const CLEAR_WHITE: Rgba = [255, 255, 255, 0];
const BLACK: Rgba = [0, 0, 0, 255];
const WHITE: Rgba = [255, 255, 255, 255];

// A 64-pixel image on a background, with a square from 12 to 52 and a bar across the square's middle.
function image(background: Rgba, square: Rgba, bar: Rgba) {
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

// A black frame with a black bar inside it (as General Motors' logo), clear everywhere else.
function blackFrameOnClear() {
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

describe('which address a logo is asked for', () => {
  it('asks for a company with the ticker 005930 by its domain, not by that ticker', () => {
    const source = logoSource({ ticker: '005930' }, 'pk_test', 'samsung.com');

    expect(source).toContain('img.logo.dev/samsung.com?token=pk_test');
    expect(source).not.toContain('ticker/');
    expect(logoSource({ ticker: '005930' }, 'pk_test')).toBeNull();
  });

  it('uses the ticker address only for a US ticker', () => {
    expect(isUsTicker('NVDA')).toBe(true);
    expect(isUsTicker('BRK.B')).toBe(true);
    expect(isUsTicker('005930')).toBe(false);
    expect(isUsTicker('7203.T')).toBe(false);
    expect(isUsTicker(null)).toBe(false);
    expect(logoSource({ ticker: 'BRK.B' }, 'pk_test', 'berkshirehathaway.com')).toContain('/ticker/BRK.B?token=');
  });

  it('falls back to the domain only for a company asked for by its US ticker', () => {
    expect(domainFallback({ ticker: 'NVDA' }, 'NVIDIA Corp', 'pk_test')).toBeTypeOf('function');
    expect(domainFallback({ ticker: '005930' }, 'Samsung Electronics', 'pk_test')).toBeUndefined();
    expect(domainFallback({ ticker: null }, 'Carl Zeiss', 'pk_test')).toBeUndefined();
    expect(domainFallback({ ticker: 'NVDA' }, 'NVIDIA Corp', '')).toBeUndefined();
  });
});

describe('a US ticker Logo.dev does not know', () => {
  // Images that "load" by address: Logo.dev knows no ticker here, and every domain.
  class FakeImage {
    crossOrigin = '';
    decoding = '';
    address = '';
    onload: () => void = () => {};
    onerror: () => void = () => {};
    set src(address: string) {
      this.address = address;
      queueMicrotask(() => (address.includes('/ticker/') ? this.onerror() : this.onload()));
    }
  }

  beforeEach(() => {
    vi.stubGlobal('Image', FakeImage);
    // No drawing in jsdom: a logo whose pixels cannot be read goes on a plain white tile.
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
  });

  it('falls back to its domain before showing initials', async () => {
    const fetch = vi.fn(async () => new Response(JSON.stringify({ 'Acme Corp': 'acme.com' })));
    vi.stubGlobal('fetch', fetch);
    const book = new LogoBook(() => {});
    const source = logoSource({ ticker: 'ACME' }, 'pk_test');

    expect(book.get(source, domainFallback({ ticker: 'ACME' }, 'Acme Corp', 'pk_test')).state).toBe('loading');

    await vi.waitFor(() => expect(book.get(source).state).toBe('ready'));
    expect(fetch).toHaveBeenCalledWith('/api/logos/domains?names=Acme%20Corp', expect.anything());
    const logo = book.get(source);
    expect(logo.state === 'ready' && (logo.image as unknown as FakeImage).address).toContain('img.logo.dev/acme.com?');
  });

  it('shows initials when its domain is not known either', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({}))));
    const book = new LogoBook(() => {});
    const source = logoSource({ ticker: 'ACME' }, 'pk_test');

    book.get(source, domainFallback({ ticker: 'ACME' }, 'Acme Corp', 'pk_test'));

    await vi.waitFor(() => expect(book.get(source).state).toBe('missing'));
  });
});

describe('how a logo sits on its tile', () => {
  it('draws a black logo on a clear background on a white tile, its mark visible', () => {
    const look = treatmentOf(blackFrameOnClear(), 64, 64);

    expect(look.treatment).toBe('dark');
    expect(look.background).toBe('#ffffff');
    // Trimmed to the mark, which is drawn in black on the white.
    expect(look.crop.x).toBeCloseTo(12 / 64);
    expect(look.crop.w).toBeCloseTo(40 / 64);
  });

  it("still fills the tile with a black square with a white mark (AMD's), on a clear or a white background", () => {
    for (const background of [CLEAR, CLEAR_WHITE, WHITE]) {
      const look = treatmentOf(image(background, BLACK, WHITE), 64, 64);

      expect(look.treatment).toBe('bleed');
      expect(look.background).toBe('rgb(0, 0, 0)');
      expect(look.crop.x).toBeCloseTo(12 / 64);
    }
  });

  it('draws logos already drawn correctly as before', () => {
    // A mark on a solid colour of its own (as NVIDIA's green) fills the tile in that colour.
    expect(treatmentOf(image([118, 185, 0, 255], [118, 185, 0, 255], WHITE), 64, 64)).toEqual({
      treatment: 'bleed',
      background: 'rgb(118, 185, 0)',
      crop: { x: 0, y: 0, w: 1, h: 1 },
    });
    // A black mark on white (as Apple's) stays on a plain white tile.
    expect(treatmentOf(image(WHITE, BLACK, BLACK), 64, 64).treatment).toBe('plain');
    // A white mark on clear still goes on a dark tile; a coloured one on a white tile.
    expect(treatmentOf(image(CLEAR, WHITE, WHITE), 64, 64).treatment).toBe('light');
    expect(treatmentOf(image(CLEAR, [20, 60, 160, 255], [20, 60, 160, 255]), 64, 64).treatment).toBe('plain');
  });
});
