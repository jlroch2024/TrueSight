// A company's tile, drawn by the graph's own drawing, so a company looks the same in every list, in the Inspector and on
// the Analyst Desk as it does on the graph. Its logo comes from Logo.dev, as the graph's do: a screen that shows tiles
// puts the Logo.dev token and the domains of companies without a ticker in <LogoSettingsContext> (see useLogoSettings).
import { type CSSProperties, createContext, useContext, useEffect, useRef, useState } from 'react';
import type { Severity } from '../api/news';
import { LogoBook, logoSource } from '../graph/logos';
import { hueOf, initialsOf } from '../graph/model';
import { drawTileMark } from '../graph/render';

/** The Logo.dev token (empty for none: every tile then shows initials) and company name to website domain. */
export type LogoSettings = { token: string; domains: Record<string, string> };

export const LogoSettingsContext = createContext<LogoSettings>({ token: '', domains: {} });

// One book of logos for every tile on the page, as the graph keeps its own: each logo is loaded and measured once, and
// every tile showing it is drawn again when it is ready.
const tilesWaiting = new Set<() => void>();
const book = new LogoBook(() => tilesWaiting.forEach((redraw) => redraw()));
// Initials are drawn in the page's font, so every tile is drawn again once the fonts have arrived.
void document.fonts?.ready.then(() => tilesWaiting.forEach((redraw) => redraw()));
// Around a tile's canvas, room for its ring's glow.
const TILE_ROOM = 6;

// A holding's tile is drawn smaller, inside its ring; the beacon's half-side, as drawTileMark draws it.
const tileSide = (size: number, holding: boolean) => (holding ? size - 8 : size);
const beaconHalf = (side: number) => (side >= 26 ? 5 : 4.2);

/**
 * A company's tile: its logo on the logo's own background (or its initials on a gradient of its own); for a holding,
 * the chamfer and its ring, in its chain colour or slate; and its marks flush in its corners, as on the graph: the
 * beacon of its most severe open alert, sending its slow signal, and the badge of how many holdings share it. size is
 * the whole tile with its ring. name is the company's stored name, as the graph keys its companies.
 */
export function Tile({
  name,
  ticker,
  size = 32,
  colour,
  beacon,
  shared,
  holding = false,
}: {
  name: string;
  ticker: string | null;
  size?: number;
  colour?: string | null;
  beacon?: Severity | null;
  shared?: number;
  holding?: boolean;
}) {
  const canvas = useRef<HTMLCanvasElement | null>(null);
  const [version, setVersion] = useState(0);
  const { token, domains } = useContext(LogoSettingsContext);
  const source = logoSource({ ticker }, token, domains[name]);
  useEffect(() => {
    const redraw = () => setVersion((value) => value + 1);
    tilesWaiting.add(redraw);
    return () => {
      tilesWaiting.delete(redraw);
    };
  }, []);
  useEffect(() => {
    const element = canvas.current;
    const context = element?.getContext('2d');
    if (!element || !context) return;
    const dpr = window.devicePixelRatio || 1;
    const whole = size + TILE_ROOM * 2;
    element.width = Math.round(whole * dpr);
    element.height = Math.round(whole * dpr);
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    context.clearRect(0, 0, whole, whole);
    drawTileMark(
      context,
      { isHolding: holding, hue: hueOf(name), initials: initialsOf(name) },
      book.get(source),
      whole / 2,
      whole / 2,
      tileSide(size, holding),
      colour ?? null,
      { beacon, shared },
    );
  }, [size, holding, colour, name, source, version, beacon, shared]);
  return (
    <span className="tile-wrap" style={{ '--size': `${size}px` } as CSSProperties} aria-hidden="true">
      <canvas
        ref={canvas}
        className="tile-canvas"
        style={{ width: size + TILE_ROOM * 2, height: size + TILE_ROOM * 2, margin: -TILE_ROOM }}
      />
      {beacon && (
        <span
          className="beacon-signal"
          data-severity={beacon}
          style={{ '--beacon': `${beaconHalf(tileSide(size, holding)) * 2}px` } as CSSProperties}
        />
      )}
    </span>
  );
}
