import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStore } from '../api/client';
import type { Portfolio } from '../api/portfolios';
import { formatDateTime } from '../format';
import { CheckNewsButton } from './CheckNewsButton';

const base: Portfolio = {
  id: 7,
  name: 'Technology',
  createdAt: '2026-10-01T00:00:00Z',
  newsStatus: 'IDLE',
  newsStatusReason: null,
  newsCheckedAt: null,
};

beforeEach(() => {
  vi.restoreAllMocks();
  tokenStore.set('a-token');
});

describe('Check News button', () => {
  it('starts a news check and changes to the checking state', async () => {
    const changed = vi.fn();
    const fetch = vi.fn(async () => new Response(null, { status: 202 }));
    vi.stubGlobal('fetch', fetch);
    render(<CheckNewsButton portfolio={base} onChange={changed} />);

    await userEvent.click(screen.getByRole('button', { name: 'Check News' }));

    expect(fetch).toHaveBeenCalledWith('/api/portfolios/7/news-check', expect.objectContaining({ method: 'POST' }));
    expect(changed).toHaveBeenCalledWith(expect.objectContaining({ newsStatus: 'CHECKING' }));
  });

  it('shows checking, last-checked, and failure states', () => {
    const { rerender } = render(
      <CheckNewsButton portfolio={{ ...base, newsStatus: 'CHECKING' }} onChange={vi.fn()} />,
    );
    expect(screen.getByRole('button', { name: 'Checking News…' })).toBeDisabled();

    rerender(
      <CheckNewsButton
        portfolio={{ ...base, newsCheckedAt: '2026-10-14T02:42:00Z' }}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByText(`News Checked ${formatDateTime('2026-10-14T02:42:00Z')}`)).toBeInTheDocument();

    rerender(
      <CheckNewsButton
        portfolio={{
          ...base,
          newsStatus: 'FAILED',
          newsStatusReason: 'Google News could not be reached. Please try again later.',
        }}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByText('Google News could not be reached. Please try again later.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Check News' })).toBeEnabled();
  });

  it('adds how many open alerts there are, and See News, when the page asks for them', async () => {
    const seeNews = vi.fn();
    const checkedAt = '2026-10-14T02:42:00Z';
    render(
      <CheckNewsButton portfolio={{ ...base, newsCheckedAt: checkedAt }} onChange={vi.fn()} openAlerts={3} onSeeNews={seeNews} />,
    );

    expect(screen.getByText(`News Checked ${formatDateTime(checkedAt)} · 3 Open Alerts`)).toBeInTheDocument();
    expect(screen.queryByText(/Article/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'See News' }));
    expect(seeNews).toHaveBeenCalled();
  });

  it('says one open alert in the singular, and none as 0 Open Alerts', () => {
    const checkedAt = '2026-10-14T02:42:00Z';
    const { rerender } = render(
      <CheckNewsButton portfolio={{ ...base, newsCheckedAt: checkedAt }} onChange={vi.fn()} openAlerts={1} />,
    );
    expect(screen.getByText(`News Checked ${formatDateTime(checkedAt)} · 1 Open Alert`)).toBeInTheDocument();

    rerender(<CheckNewsButton portfolio={{ ...base, newsCheckedAt: checkedAt }} onChange={vi.fn()} openAlerts={0} />);
    expect(screen.getByText(`News Checked ${formatDateTime(checkedAt)} · 0 Open Alerts`)).toBeInTheDocument();
  });

  it('ends the line with Alerts Could Not Be Counted when the alerts could not be loaded', () => {
    const checkedAt = '2026-10-14T02:42:00Z';
    render(
      <CheckNewsButton
        portfolio={{ ...base, newsCheckedAt: checkedAt }}
        onChange={vi.fn()}
        openAlerts={null}
        alertsCouldNotBeCounted
      />,
    );

    expect(screen.getByText(`News Checked ${formatDateTime(checkedAt)} · Alerts Could Not Be Counted`)).toBeInTheDocument();
  });

  it('shows only the time of the last check while the count has not arrived', () => {
    const checkedAt = '2026-10-14T02:42:00Z';
    render(<CheckNewsButton portfolio={{ ...base, newsCheckedAt: checkedAt }} onChange={vi.fn()} openAlerts={null} />);

    expect(screen.getByText(`News Checked ${formatDateTime(checkedAt)}`)).toBeInTheDocument();
  });

  it('shows a partial failure reason under the News Checked line without the error style', () => {
    const checkedAt = '2026-10-14T02:42:00Z';
    const reason = 'News for 1 of 2 companies could not be fetched: Fabrinet. Check News again to retry it.';
    render(
      <CheckNewsButton
        portfolio={{ ...base, newsCheckedAt: checkedAt, newsStatusReason: reason }}
        onChange={vi.fn()}
      />,
    );

    const checked = screen.getByText(`News Checked ${formatDateTime(checkedAt)}`);
    const explanation = screen.getByText(reason);
    expect(checked.compareDocumentPosition(explanation) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(explanation).toHaveClass('muted', 'small');
    expect(explanation).not.toHaveClass('error');
  });

  it('offers no See News before the first check', () => {
    render(<CheckNewsButton portfolio={base} onChange={vi.fn()} openAlerts={null} onSeeNews={vi.fn()} />);

    expect(screen.queryByRole('button', { name: 'See News' })).not.toBeInTheDocument();
  });
});
