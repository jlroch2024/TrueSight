// Show in Report's address. The evidence is real, from docs/examples/relationships.json.
import { describe, expect, it } from 'vitest';
import sampleRelationshipsJson from '../../docs/examples/relationships.json';
import type { Relationship } from './api/relationships';
import { formatAge, formatDate, formatDateTime, showInReportUrl, titleCase } from './format';

const sampleRelationships = sampleRelationshipsJson as Relationship[];
const evidenceOf = (company: string, counterparty: string) =>
  sampleRelationships.find((r) => r.company.name === company && r.counterparty.name === counterparty)!.evidence;

describe('showInReportUrl', () => {
  const url = 'https://www.sec.gov/Archives/edgar/data/937966/000162828026011378/asml-20251231.htm';

  it('uses the first 5 and last 5 words, comma-separated, for evidence over 10 words', () => {
    const asml = evidenceOf('ASML Holding N.V.', 'Carl Zeiss SMT');

    expect(showInReportUrl(url, asml)).toBe(
      `${url}#:~:text=The%20number%20of%20lithography%20systems,we%20refer%20to%20as%20optics).`,
    );
  });

  it('uses evidence of 10 words or fewer whole, writing a hyphen as %2D', () => {
    // The first 10 words of QUALCOMM's sentence about its foundries, which include "analog/mixed-signal".
    const qualcomm = evidenceOf('QUALCOMM Incorporated', 'GLOBALFOUNDRIES Inc.').split(' ').slice(0, 10).join(' ');

    expect(showInReportUrl(url, qualcomm)).toBe(
      `${url}#:~:text=The%20primary%20foundry%20suppliers%20for%20our%20various%20digital%2C%20analog%2Fmixed%2Dsignal%2C%20RF`,
    );
  });

  it('ignores spaces around and inside the evidence', () => {
    expect(showInReportUrl(url, '  Carl Zeiss SMT,\n our sole supplier  ')).toBe(
      `${url}#:~:text=Carl%20Zeiss%20SMT%2C%20our%20sole%20supplier`,
    );
  });
});

describe('formatAge', () => {
  const now = Date.parse('2026-10-13T12:00:00Z');

  it('says how long ago, in the largest whole unit', () => {
    expect(formatAge('2026-10-13T11:59:40Z', now)).toBe('Just Now');
    expect(formatAge('2026-10-13T11:59:00Z', now)).toBe('1 Minute Ago');
    expect(formatAge('2026-10-13T11:15:00Z', now)).toBe('45 Minutes Ago');
    expect(formatAge('2026-10-13T11:00:00Z', now)).toBe('1 Hour Ago');
    expect(formatAge('2026-10-12T17:07:00Z', now)).toBe('18 Hours Ago');
    expect(formatAge('2026-10-09T17:00:00Z', now)).toBe('3 Days Ago');
  });

  it('gives the date itself past a week, and a dash for a bad date', () => {
    expect(formatAge('2026-09-01T12:00:00Z', now)).toBe('Sep 1, 2026');
    expect(formatAge('not a date', now)).toBe('—');
  });
});

describe('formatDate', () => {
  it('shows the short month, day and year', () => {
    expect(formatDate('2026-10-14T12:00:00Z')).toBe('Oct 14, 2026');
  });

  it('keeps the calendar day of a date with no time', () => {
    expect(formatDate('2026-02-24')).toBe('Feb 24, 2026');
  });

  it('uses a dash for a bad date', () => {
    expect(formatDate('not a date')).toBe('—');
  });
});

describe('formatDateTime', () => {
  it('shows the short month, day, year and time', () => {
    const date = new Date(2026, 9, 18, 4, 55);
    expect(formatDateTime(date.toISOString())).toBe('Oct 18, 2026, 4:55 AM');
  });

  it('uses a dash for a bad date', () => {
    expect(formatDateTime('not a date')).toBe('—');
  });
});

describe('titleCase', () => {
  it('capitalises each word and each part of a hyphenated word', () => {
    expect(titleCase('wafer production')).toBe('Wafer Production');
    expect(titleCase('lithium-ion battery cells')).toBe('Lithium-Ion Battery Cells');
  });

  it('keeps small words lower case unless they come first', () => {
    expect(titleCase('assembly, testing and packaging')).toBe('Assembly, Testing and Packaging');
    expect(titleCase('the design of chips')).toBe('The Design of Chips');
  });

  it('never makes a letter lower case', () => {
    expect(titleCase('AMD GPUs')).toBe('AMD GPUs');
    expect(titleCase('mobile SoC designs')).toBe('Mobile SoC Designs');
  });
});
