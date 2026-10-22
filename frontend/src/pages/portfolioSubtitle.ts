// The Portfolio page's subtitle: where the portfolio stands, and what to do next.
import type { Holding } from '../api/portfolios';

export type PortfolioSubtitle = { text: string; showSupplyChain: boolean };

/**
 * No holdings: upload some. Holdings, none analysed: press Analyse. While analysing: how many reports are read. Once at
 * least one holding is Done: how many are analysed, and the Supply Chain is worth opening. A holding counts as analysed
 * once it is no longer Waiting or Analysing, whatever the result.
 */
export function portfolioSubtitle(holdings: Holding[]): PortfolioSubtitle {
  const total = holdings.length;
  if (total === 0) return { text: 'Upload a CSV of your holdings to begin.', showSupplyChain: false };

  const analysed = holdings.filter((h) => h.status !== 'WAITING' && h.status !== 'ANALYSING').length;
  if (holdings.some((h) => h.status === 'ANALYSING')) {
    return { text: `Reading Annual Reports: ${analysed} of ${total} Done`, showSupplyChain: false };
  }
  if (holdings.some((h) => h.status === 'DONE')) {
    const noun = total === 1 ? 'Holding' : 'Holdings';
    return { text: `${analysed} of ${total} ${noun} Analysed`, showSupplyChain: true };
  }
  return {
    text: "Press Analyse and TrueSight reads each company's latest annual report to find the suppliers and customers it names.",
    showSupplyChain: false,
  };
}
