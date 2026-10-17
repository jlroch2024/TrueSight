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
    expect(screen.getByRole('button', { name: 'Checking news…' })).toBeDisabled();

    rerender(
      <CheckNewsButton
        portfolio={{ ...base, newsCheckedAt: '2026-10-14T02:42:00Z' }}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByText(`News checked ${formatDateTime('2026-10-14T02:42:00Z')}`)).toBeInTheDocument();

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
});
