// Real headlines for website tests: the saved Google News answer for TSMC, docs/examples/google-news-tsmc.xml, read the
// way the backend reads it (the publisher's name is taken off the end of each title), and shaped as GET .../news answers.
import googleNewsTsmc from '../../../docs/examples/google-news-tsmc.xml?raw';
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
