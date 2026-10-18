// Real headlines for website tests: the saved Google News answer for TSMC, docs/examples/google-news-tsmc.xml, read the
// way the backend reads it (the publisher's name is taken off the end of each title), and shaped as GET .../news answers.
import googleNewsTsmc from '../../../docs/examples/google-news-tsmc.xml?raw';
import geminiNewsAssessment from '../../../docs/examples/gemini-news-assessment.json?raw';
import type { NewsArticle } from '../api/news';

export const TSMC = { id: 11, name: 'Taiwan Semiconductor Manufacturing Company Limited', ticker: 'TSM' };

/** The first {@code count} headlines in the sample, about TSMC, not yet judged by the AI. */
export function sampleNews(count: number): NewsArticle[] {
  const items = [...new DOMParser().parseFromString(googleNewsTsmc, 'text/xml').querySelectorAll('item')];
  return items.slice(0, count).map((item, index) => {
    const text = (tag: string) => item.querySelector(tag)?.textContent ?? '';
    const source = text('source');
    return {
      id: index + 1,
      title: text('title').replace(` - ${source}`, ''),
      source,
      publishedAt: new Date(text('pubDate')).toISOString(),
      url: text('link'),
      company: TSMC,
      isHolding: false,
      assessment: null,
    };
  });
}

/** Real headlines paired with Gemini's real reasons for filtering them out as noise. */
export function sampleNoise(count: number): NewsArticle[] {
  const response = JSON.parse(geminiNewsAssessment) as {
    candidates: { content: { parts: { text?: string; thought?: boolean }[] } }[];
  };
  const answer = response.candidates[0]!.content.parts
    .filter((part) => !part.thought)
    .map((part) => part.text ?? '')
    .join('');
  const judgements = JSON.parse(answer) as {
    isEvent: boolean;
    confidence: 'LOW' | 'MEDIUM' | 'HIGH';
    reason: string;
  }[];
  return sampleNews(count).map((article, index) => ({
    ...article,
    assessment: {
      isEvent: false,
      eventType: null,
      severity: null,
      confidence: judgements[index]!.confidence,
      summary: null,
      reason: judgements[index]!.reason,
    },
  }));
}
