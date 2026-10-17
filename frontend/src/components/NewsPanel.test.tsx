// The News tab, with real headlines from the saved Google News sample (see src/test/sampleNews.ts).
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { NewsArticle } from '../api/news';
import type { Portfolio } from '../api/portfolios';
import { sampleNews } from '../test/sampleNews';
import { NewsPanel } from './NewsPanel';

const portfolio: Portfolio = {
  id: 7,
  name: 'Semiconductors',
  createdAt: '2026-10-01T00:00:00Z',
  newsStatus: 'IDLE',
  newsStatusReason: null,
  newsCheckedAt: '2026-10-13T00:00:00Z',
};

function renderPanel(articles: NewsArticle[] | undefined, overrides: Partial<Parameters<typeof NewsPanel>[0]> = {}) {
  const props = {
    portfolio,
    onPortfolioChange: vi.fn(),
    articles,
    error: null,
    companyFilter: null,
    onClearFilter: vi.fn(),
    onSelectCompany: vi.fn(),
    ...overrides,
  };
  render(<NewsPanel {...props} />);
  return props;
}

beforeEach(() => {
  // Only the clock is faked, so "how long ago" is the same on every run.
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-13T00:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('NewsPanel', () => {
  it('lists each headline with its company, publisher, age and Open Article', async () => {
    const [first] = sampleNews(1);
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    renderPanel([first!]);

    const item = screen.getByRole('listitem');
    expect(within(item).getByText(first!.title)).toBeInTheDocument();
    expect(within(item).getByRole('button', { name: 'TSM' })).toBeInTheDocument();
    expect(item).toHaveTextContent('Yahoo Finance · 6 hours ago');

    await userEvent.click(within(item).getByRole('button', { name: 'Open Article' }));
    expect(open).toHaveBeenCalledWith(first!.url, '_blank', 'noopener,noreferrer');
  });

  it('marks a holding in teal, and chooses the company when its tag is clicked', async () => {
    const [first] = sampleNews(1);
    const props = renderPanel([{ ...first!, isHolding: true }]);

    const tag = screen.getByRole('button', { name: 'TSM' });
    expect(tag).toHaveClass('holding');
    await userEvent.click(tag);

    expect(props.onSelectCompany).toHaveBeenCalledWith('Taiwan Semiconductor Manufacturing Company Limited');
  });

  it("shows the AI's judgement as a tag once there is one, and none before", () => {
    const [unjudged, noise, event] = sampleNews(3);
    renderPanel([
      unjudged!,
      { ...noise!, assessment: { isEvent: false, eventType: null, severity: null, confidence: 'HIGH', summary: null, reason: 'Investment commentary.' } },
      { ...event!, assessment: { isEvent: true, eventType: 'PRODUCTION_STOP', severity: 'HIGH', confidence: 'MEDIUM', summary: 'A stoppage.', reason: 'A stoppage.' } },
    ]);

    const [first, second, third] = screen.getAllByRole('listitem');
    expect(first!.querySelector('.news-tag')).toBeNull();
    expect(within(second!).getByText('Not an Event')).toBeInTheDocument();
    expect(within(third!).getByText('Event · High')).toHaveClass('severity-high');
  });

  it('narrows the list to one company, and Clear brings back the rest', async () => {
    const [tsmc, other] = sampleNews(2);
    const nvidia = { ...other!, company: { id: 1, name: 'NVIDIA Corporation', ticker: 'NVDA' } };
    const props = renderPanel([tsmc!, nvidia], { companyFilter: 'NVIDIA Corporation' });

    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    expect(screen.getByText('Only NVIDIA')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));

    expect(props.onClearFilter).toHaveBeenCalled();
  });

  it('says news has not been checked yet, and what Check News does, before the first check', () => {
    renderPanel([], { portfolio: { ...portfolio, newsCheckedAt: null } });

    expect(screen.getByText(/News has not been checked yet\. Check News collects the last 7 days/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Check News' })).toBeInTheDocument();
  });

  it('says there is no recent news when a check found none', () => {
    renderPanel([]);

    expect(screen.getByText('No news in the last 7 days about these companies.')).toBeInTheDocument();
  });

  it('shows the error when the news cannot be loaded', () => {
    renderPanel(undefined, { error: 'Cannot reach the server.' });

    expect(screen.getByText('Cannot reach the server.')).toBeInTheDocument();
  });
});
