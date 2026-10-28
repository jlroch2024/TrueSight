// The look every screen shares, "Signal and Schematic" (see the top of styles.css), checked where it is written down: one
// typeface, the same four ticks on every glass panel, chamfers only on companies and their marks, the six holding
// colours in their agreed order, and one set of severity colours everywhere.
import { describe, expect, it } from 'vitest';
import graphStyles from './graph/livingGraph.css?raw';
import { ALERT_COLOUR } from './graph/model';
import { FONT, RISK_COLOUR } from './graph/render';
import { CHAIN_COLOURS } from './pages/supplyChain';
import styles from './styles.css?raw';

// Every screen's own code, as text.
const sources = import.meta.glob<string>(['./**/*.tsx', '!./**/*.test.tsx'], { query: '?raw', import: 'default', eager: true });

/** Each CSS rule's selector and body. */
function rules(css: string) {
  return [...css.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/([^{}]+)\{([^{}]*)\}/g)].map(([, selector, body]) => ({
    selector: selector!.trim(),
    body: body!,
  }));
}

describe('one typeface', () => {
  it('isIbmPlexSansEverywhereWithNoMonospace', () => {
    expect(styles).toMatch(/--font:\s*'IBM Plex Sans Variable', 'IBM Plex Sans', system-ui, sans-serif;/);
    for (const css of [styles, graphStyles]) {
      for (const { body } of rules(css)) {
        const family = /font-family:\s*([^;]+)/.exec(body)?.[1];
        if (family) expect(family).toMatch(/^(var\(--font\)|inherit|'IBM Plex Sans Variable', 'IBM Plex Sans', system-ui, sans-serif)$/);
      }
    }
    // The graph draws its labels on a canvas, in the same face.
    expect(FONT).toBe('"IBM Plex Sans Variable", "IBM Plex Sans", system-ui, sans-serif');
    for (const text of [styles, graphStyles, FONT, ...Object.values(sources)]) {
      expect(text).not.toMatch(/Space Grotesk|Geist|monospace/);
    }
  });
});

describe('the marks', () => {
  it('givesEveryGlassPanelTheSameFourTicks', () => {
    const glassy: { panel: string; ticks: boolean }[] = [];
    for (const [file, text] of Object.entries(sources)) {
      for (const [, plain, template] of text.matchAll(/className=(?:"([^"]*)"|\{`([^`]*)`\})/g)) {
        // A class added only sometimes (${...}) is left out: only the ones always there count.
        const classes = (plain ?? template!).replace(/\$\{[^}]*\}/g, ' ').split(/\s+/);
        const panel = `${file}: ${classes.join(' ')}`;
        // The page's glass carries .ticks; the graph's own controls (lg-glass) carry the graph's, .lg-mark.
        if (classes.includes('glass')) glassy.push({ panel, ticks: classes.includes('ticks') });
        if (classes.some((name) => name.startsWith('lg-glass'))) glassy.push({ panel, ticks: classes.some((name) => name.startsWith('lg-mark')) });
      }
    }
    expect(glassy.length).toBeGreaterThan(10);
    expect(glassy.filter((found) => !found.ticks).map((found) => found.panel)).toEqual([]);
    // The ticks: 6px, 42% slate, on the border.
    expect(styles).toMatch(/--tick:\s*rgba\(203, 213, 225, 0\.42\)/);
    expect(styles).toMatch(/\.ticks::before\s*\{\s*--len:\s*6px;/);
  });

  it('chamfersOnlyCompaniesAndTheirMarks', () => {
    const chamfered = rules(styles)
      .filter(({ body }) => /clip-path:\s*polygon/.test(body))
      .map(({ selector }) => selector);
    // An event's mark is drawn as CSS; a company's tile, its beacon and its badge are drawn by the graph's own drawing.
    expect(chamfered).toEqual(['.event-mark']);
  });
});

describe('the colours', () => {
  it('givesHoldingsTheSixAgreedColoursGreenBlueVioletThenCyanMagentaYellow', () => {
    expect(CHAIN_COLOURS).toEqual(['#62cf77', '#4ba3f7', '#8a59da', '#68def4', '#e06dca', '#ebdf5d']);
  });

  it('usesOneSeverityColourSetForBeaconsCardsTagsAndDisruptedLinks', () => {
    const colourOf = (name: string) => new RegExp(`--${name}:\\s*(#[0-9a-f]{6})`).exec(styles)?.[1];
    expect({ HIGH: colourOf('high'), MEDIUM: colourOf('medium'), LOW: colourOf('low') }).toEqual(ALERT_COLOUR);
    expect(RISK_COLOUR).toEqual(ALERT_COLOUR);
  });
});
