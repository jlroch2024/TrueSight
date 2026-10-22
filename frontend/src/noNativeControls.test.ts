// AGENTS.md: no browser-default controls. Only Checkbox and RadioGroup may use a (hidden) native input underneath.
import { describe, expect, it } from 'vitest';

const sources = import.meta.glob(['./**/*.tsx', '!./**/*.test.tsx'], { query: '?raw', import: 'default', eager: true }) as Record<
  string,
  string
>;
const ALLOWED = new Set(['./components/Checkbox.tsx', './components/RadioGroup.tsx']);
// Every source file, .ts too, for the browser's own boxes.
const allSources = import.meta.glob(['./**/*.{ts,tsx}', '!./**/*.test.{ts,tsx}'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

describe('TrueSight controls', () => {
  it('leaves no native select, checkbox or radio button in the website', () => {
    const offenders = Object.entries(sources)
      .filter(([path]) => !ALLOWED.has(path))
      .filter(([, source]) => /<select[\s>]|type=["'](checkbox|radio)["']/.test(source))
      .map(([path]) => path);

    expect(Object.keys(sources).length).toBeGreaterThan(10);
    expect(offenders).toEqual([]);
  });

  it('asks with useConfirm, never the browser\'s confirm, alert or prompt', () => {
    const offenders = Object.entries(allSources)
      .filter(([, source]) => /\b(window\.)?(confirm|alert|prompt)\(/.test(source.replaceAll('await confirm(', '')))
      .map(([path]) => path);

    expect(Object.keys(allSources)).toContain('./components/ConfirmDialog.tsx');
    expect(offenders).toEqual([]);
  });
});
