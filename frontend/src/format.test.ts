// Show in Report's address. The evidence is real, from docs/examples/relationships.json.
import { describe, expect, it } from 'vitest';
import sampleRelationshipsJson from '../../docs/examples/relationships.json';
import type { Relationship } from './api/relationships';
import { showInReportUrl } from './format';

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
