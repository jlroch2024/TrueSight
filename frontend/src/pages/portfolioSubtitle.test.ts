import { describe, expect, it } from 'vitest';
import type { Holding, HoldingStatus } from '../api/portfolios';
import { portfolioSubtitle } from './portfolioSubtitle';

function holdings(...statuses: HoldingStatus[]): Holding[] {
  return statuses.map((status, i) => ({
    id: i + 1, ticker: `T${i}`, weight: null, companyName: null, companyDisplayName: null, status, statusReason: null,
    reportType: null, reportDate: null, reportLink: null, lastAnalysed: null,
  }));
}

describe('portfolioSubtitle', () => {
  it('asks for a CSV when there are no holdings', () => {
    expect(portfolioSubtitle([])).toEqual({ text: 'Upload a CSV of your holdings to begin.', showSupplyChain: false });
  });

  it('explains Analyse when no holding has been analysed', () => {
    expect(portfolioSubtitle(holdings('WAITING', 'WAITING')).text).toBe(
      "Press Analyse and TrueSight reads each company's latest annual report to find the suppliers and customers it names.",
    );
  });

  it('counts the reports read while analysing, whatever their result', () => {
    expect(portfolioSubtitle(holdings('DONE', 'FAILED', 'ANALYSING', 'WAITING'))).toEqual({
      text: 'Reading Annual Reports: 2 of 4 Done',
      showSupplyChain: false,
    });
  });

  it('counts the analysed holdings and offers the Supply Chain once one is Done', () => {
    expect(portfolioSubtitle(holdings('DONE', 'NO_REPORT_FOUND', 'WAITING'))).toEqual({
      text: '2 of 3 Holdings Analysed',
      showSupplyChain: true,
    });
    expect(portfolioSubtitle(holdings('DONE')).text).toBe('1 of 1 Holding Analysed');
  });

  it('still explains Analyse when every analysed holding failed', () => {
    expect(portfolioSubtitle(holdings('FAILED')).showSupplyChain).toBe(false);
  });
});
