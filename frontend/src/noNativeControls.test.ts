// AGENTS.md: no browser-default controls. Only Checkbox and RadioGroup may use a (hidden) native input underneath.
import { describe, expect, it } from 'vitest';

const sources = import.meta.glob(['./**/*.tsx', '!./**/*.test.tsx'], { query: '?raw', import: 'default', eager: true }) as Record<
  string,
  string
>;
const ALLOWED = new Set(['./components/Checkbox.tsx', './components/RadioGroup.tsx']);

describe('TrueSight controls', () => {
  it('leaves no native select, checkbox or radio button in the website', () => {
    const offenders = Object.entries(sources)
      .filter(([path]) => !ALLOWED.has(path))
      .filter(([, source]) => /<select[\s>]|type=["'](checkbox|radio)["']/.test(source))
      .map(([path]) => path);

    expect(Object.keys(sources).length).toBeGreaterThan(10);
    expect(offenders).toEqual([]);
  });
});
