// Open alerts for website tests, about the real TSMC headlines in docs/examples/google-news-tsmc.xml (see
// sampleNews.ts), shaped as GET .../alerts answers. Names are as the API sends them: companyName is the stored name of
// the company the article is about, and the path names are display names.
import type { AlertEvent, AlertStatus, ReachedHolding } from '../api/alerts';
import type { Severity } from '../api/news';
import { TSMC, sampleNews } from './sampleNews';

/** TSMC supplies NVIDIA, as the Alerts page words it: how an alert about TSMC reaches the NVDA holding. */
export function tsmcSuppliesNvidia(holdingId = 1, relationshipId = 1): ReachedHolding {
  return {
    holdingId,
    ticker: 'NVDA',
    weight: 40,
    indirect: false,
    supplierName: TSMC.displayName,
    middleName: null,
    customerName: 'NVIDIA',
    reviewStatus: 'UNREVIEWED',
    relationshipId,
    secondRelationshipId: null,
  };
}

type Options = {
  id?: number;
  // Which of the sample headlines the alert is about, from 0 (the newest).
  headline?: number;
  severity?: Severity;
  status?: AlertStatus;
  // The stored name of the company the article is about; TSMC's by default.
  companyName?: string | null;
  // The days the article is old, so that an old alert can be shown to stay listed.
  daysOld?: number;
  holdings?: ReachedHolding[];
};

export function sampleAlert({
  id = 1,
  headline = 0,
  severity = 'HIGH',
  status = 'NEW',
  companyName = TSMC.name,
  daysOld,
  holdings = [tsmcSuppliesNvidia()],
}: Options = {}): AlertEvent {
  const article = sampleNews(headline + 1)[headline]!;
  const publishedAt =
    daysOld === undefined ? article.publishedAt : new Date(Date.now() - daysOld * 86_400_000).toISOString();
  const source = { title: article.title, source: article.source, publishedAt, url: article.url };
  return {
    alertIds: [id],
    status,
    exposure: 40,
    recommendation: null,
    headline: {
      article: { ...source, companyName },
      assessment: {
        eventType: 'PRODUCTION_STOP',
        severity,
        confidence: 'MEDIUM',
        summary: null,
        reason: 'The headline reports a production stoppage.',
      },
    },
    sources: [source],
    holdings,
  };
}
