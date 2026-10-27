import { render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStore } from '../api/client';
import type { Quality } from '../api/quality';
import { QualityPage } from './QualityPage';

// The 8 weeks ending Oct 27, 2026, oldest first.
const WEEKS = Array.from({ length: 8 }, (_, index) => {
  const end = new Date(Date.UTC(2026, 9, 27 - 7 * (7 - index)));
  const start = new Date(end.getTime() - 6 * 86_400_000);
  return { weekStart: start.toISOString().slice(0, 10), weekEnd: end.toISOString().slice(0, 10) };
});

/** Every week empty, then the newest week as given. */
function quality(newest: {
  relationships?: Partial<Quality['relationships'][number]>;
  news?: Partial<Quality['news'][number]>;
  recommendations?: Partial<Quality['recommendations'][number]>;
  calls?: Partial<Quality['calls'][number]>;
}): Quality {
  const last = (index: number) => index === WEEKS.length - 1;
  return {
    relationships: WEEKS.map((week, index) => ({
      ...week, reviewed: 0, approved: 0, rejected: 0, doubtsReviewed: 0, doubtsRejected: 0,
      ...(last(index) ? newest.relationships : {}),
    })),
    news: WEEKS.map((week, index) => ({
      ...week, decided: 0, overruled: 0, noiseMadeEvents: 0, eventsMadeNoise: 0,
      ...(last(index) ? newest.news : {}),
    })),
    recommendations: WEEKS.map((week, index) => ({
      ...week, decided: 0, approvedAsWritten: 0, edited: 0, withheld: 0,
      ...(last(index) ? newest.recommendations : {}),
    })),
    calls: WEEKS.map((week, index) => ({
      ...week, answered: 0, unreadable: 0, failed: 0, medianMs: null,
      ...(last(index) ? newest.calls : {}),
    })),
  };
}

const busyWeek = quality({
  relationships: { reviewed: 5, approved: 3, rejected: 2, doubtsReviewed: 2, doubtsRejected: 1 },
  news: { decided: 10, overruled: 3, noiseMadeEvents: 1, eventsMadeNoise: 1 },
  recommendations: { decided: 4, approvedAsWritten: 2, edited: 1, withheld: 1 },
  calls: { answered: 2, unreadable: 1, failed: 1, medianMs: 250 },
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

/** The rows of one section's table: the header row, then the 8 weeks oldest first. */
async function rowsOf(section: string) {
  return within(await screen.findByRole('region', { name: section })).getAllByRole('row');
}

function widthOf(row: HTMLElement, label: string, nth = 0): string {
  const segment = row.querySelectorAll<HTMLElement>(`.quality-segment[data-label="${label}"]`)[nth];
  return segment?.style.width ?? 'missing';
}

beforeEach(() => {
  tokenStore.set('analyst-token');
});

afterEach(() => {
  vi.unstubAllGlobals();
  tokenStore.clear();
});

describe('AI Quality', () => {
  it('asks for the figures once, and labels each week by its dates', async () => {
    const fetch = vi.fn().mockResolvedValue(json(busyWeek));
    vi.stubGlobal('fetch', fetch);

    render(<QualityPage />);

    const rows = await rowsOf('Finding Relationships');
    expect(rows).toHaveLength(9);
    expect(within(rows[1]!).getByRole('rowheader')).toHaveTextContent('Sep 2 – Sep 8');
    expect(within(rows[8]!).getByRole('rowheader')).toHaveTextContent('Oct 21 – Oct 27');
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch).toHaveBeenCalledWith('/api/quality', expect.anything());
  });

  it('draws finding relationships as bars sized in percent, with the shares written beside them', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(busyWeek)));

    render(<QualityPage />);

    const week = (await rowsOf('Finding Relationships'))[8]!;
    expect(widthOf(week, 'Approved')).toBe('60%');
    expect(widthOf(week, 'Rejected')).toBe('40%');
    expect(week).toHaveTextContent('60% Approved · 40% Rejected · 5 Reviewed');
    // Of the 2 the second check doubted first, 1 was rejected.
    expect(widthOf(week, 'Rejected', 1)).toBe('50%');
    expect(week).toHaveTextContent('50% Rejected · 1 of 2 Doubts');
  });

  it('splits the overruled articles into noise made events and events made noise', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(busyWeek)));

    render(<QualityPage />);

    const week = (await rowsOf('Judging News'))[8]!;
    expect(widthOf(week, 'Noise Made Events')).toBe('10%');
    expect(widthOf(week, 'Events Made Noise')).toBe('10%');
    expect(widthOf(week, 'Type or Severity Changed')).toBe('10%');
    expect(week).toHaveTextContent(
      '30% Overruled · 10% Noise Made Events · 10% Events Made Noise · 10% Type or Severity Changed · 10 Decided',
    );
  });

  it('shows recommendations approved as written, edited and withheld', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(busyWeek)));

    render(<QualityPage />);

    const week = (await rowsOf('Recommendations'))[8]!;
    expect(widthOf(week, 'Approved as Written')).toBe('50%');
    expect(widthOf(week, 'Edited')).toBe('25%');
    expect(widthOf(week, 'Withheld')).toBe('25%');
    expect(week).toHaveTextContent('50% Approved as Written · 25% Edited · 25% Withheld · 4 Decided');
  });

  it('shows calls answered, unreadable and failed, and the median time taken', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(busyWeek)));

    render(<QualityPage />);

    const week = (await rowsOf('Calls'))[8]!;
    expect(widthOf(week, 'Answered')).toBe('50%');
    expect(widthOf(week, 'Unreadable')).toBe('25%');
    expect(widthOf(week, 'Failed')).toBe('25%');
    expect(week).toHaveTextContent('50% Answered · 25% Unreadable · 25% Failed · 4 Calls');
    expect(within(week).getByText('250 ms')).toBeInTheDocument();
  });

  it('says No Decisions for a week with no decisions, rather than 0%', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(busyWeek)));

    render(<QualityPage />);

    for (const section of ['Finding Relationships', 'Judging News', 'Recommendations']) {
      const emptyWeek = (await rowsOf(section))[1]!;
      expect(within(emptyWeek).getByText('No Decisions')).toBeInTheDocument();
      expect(emptyWeek).not.toHaveTextContent('%');
      expect(emptyWeek.querySelector('.quality-bar')).toBeNull();
    }
    const quietWeek = (await rowsOf('Calls'))[1]!;
    expect(within(quietWeek).getByText('No Calls')).toBeInTheDocument();
    expect(quietWeek).not.toHaveTextContent('%');
  });

  it('says No Doubts Reviewed when none of a week’s reviews had a second check doubt', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(json(quality({ relationships: { reviewed: 2, approved: 2, rejected: 0 } }))),
    );

    render(<QualityPage />);

    const week = (await rowsOf('Finding Relationships'))[8]!;
    expect(week).toHaveTextContent('100% Approved · 0% Rejected · 2 Reviewed');
    expect(within(week).getByText('No Doubts Reviewed')).toBeInTheDocument();
  });

  it('explains an empty track record, and shows a backend error', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(json(quality({})))
      .mockResolvedValueOnce(json({ message: 'Only analysts can do this.' }, 403));
    vi.stubGlobal('fetch', fetch);

    const view = render(<QualityPage />);
    expect(await screen.findByText(/Nothing was decided and no AI call was made in the last 8 weeks/)).toBeInTheDocument();
    expect(screen.queryByRole('region')).not.toBeInTheDocument();

    view.unmount();
    render(<QualityPage />);
    expect(await screen.findByText('Only analysts can do this.')).toBeInTheDocument();
  });
});
