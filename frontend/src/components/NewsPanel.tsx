// The Supply Chain page's News tab: Check News, then the last 7 days' headlines about the portfolio's holdings and
// their suppliers and customers, newest first. Each shows its company (teal for a holding), the headline, the
// publisher and how long ago, the AI's judgement once there is one, and Open Article. Clicking a company chooses it on
// the graph. The list can be narrowed to one company, from the Inspector's Show All News.
import type { NewsArticle, NewsAssessment } from '../api/news';
import type { Portfolio } from '../api/portfolios';
import { formatAge, openInNewTab } from '../format';
import { displayName } from '../pages/supplyChain';
import { CheckNewsButton } from './CheckNewsButton';

type Props = {
  portfolio: Portfolio | null;
  onPortfolioChange: (portfolio: Portfolio) => void;
  // undefined while loading.
  articles: NewsArticle[] | undefined;
  error: string | null;
  // The company the list is narrowed to, by name; null for every company.
  companyFilter: string | null;
  onClearFilter: () => void;
  onSelectCompany: (companyName: string) => void;
};

const SEVERITY_LABELS = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' } as const;

/** The AI's judgement as a small tag: "Event · High" in the severity's colour, or "Not an Event". None before. */
export function AssessmentTag({ assessment }: { assessment: NewsAssessment | null }) {
  if (!assessment) return null;
  if (!assessment.isEvent) return <span className="news-tag not-event">Not an Event</span>;
  const severity = assessment.severity ?? 'LOW';
  return <span className={`news-tag severity-${severity.toLowerCase()}`}>Event · {SEVERITY_LABELS[severity]}</span>;
}

/** One headline: its company, the headline, the publisher and age, the AI's tag, and Open Article. */
export function NewsItem({
  article,
  onSelectCompany,
}: {
  article: NewsArticle;
  onSelectCompany?: (companyName: string) => void;
}) {
  const companyLabel = article.company.ticker ?? displayName(article.company.name);
  return (
    <li className="news-item">
      {onSelectCompany && (
        <button
          type="button"
          className={`company-tag${article.isHolding ? ' holding' : ''}`}
          onClick={() => onSelectCompany(article.company.name)}
          title={article.company.name}
        >
          {companyLabel}
        </button>
      )}
      <p className="news-title">{article.title}</p>
      <p className="news-meta">
        {article.source && <span>{article.source} · </span>}
        <span>{formatAge(article.publishedAt)}</span>
        <AssessmentTag assessment={article.assessment} />
      </p>
      <button type="button" className="secondary small-button" onClick={() => openInNewTab(article.url)}>
        Open Article
      </button>
    </li>
  );
}

export function NewsPanel({
  portfolio,
  onPortfolioChange,
  articles,
  error,
  companyFilter,
  onClearFilter,
  onSelectCompany,
}: Props) {
  const shown = companyFilter ? (articles ?? []).filter((a) => a.company.name === companyFilter) : (articles ?? []);
  return (
    <div className="news-panel">
      {portfolio && <CheckNewsButton portfolio={portfolio} onChange={onPortfolioChange} variant="compact" />}
      {companyFilter && (
        <p className="news-filter">
          <span>Only {displayName(companyFilter)}</span>
          <button type="button" className="secondary small-button" onClick={onClearFilter}>
            Clear
          </button>
        </p>
      )}
      {error ? (
        <p className="error">{error}</p>
      ) : articles === undefined ? (
        <p className="muted">Loading…</p>
      ) : articles.length === 0 ? (
        <p className="muted small">
          {portfolio?.newsCheckedAt
            ? 'No news in the last 7 days about these companies.'
            : 'News has not been checked yet. Check News collects the last 7 days of headlines about your holdings and their suppliers and customers.'}
        </p>
      ) : (
        <ul className="news-list">
          {shown.map((article) => (
            <NewsItem key={article.id} article={article} onSelectCompany={onSelectCompany} />
          ))}
        </ul>
      )}
    </div>
  );
}
