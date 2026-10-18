// The Review page: its four states, the cards, Approve, Reject with a required note, and the filter. The backend is
// replaced by a fake fetch that remembers what was asked of it.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStore } from '../api/client';
import type { ReviewItem } from '../api/reviews';
import { ReviewPage } from './ReviewPage';

const sentence =
  'We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC, and Samsung ' +
  'Electronics Co., Ltd., or Samsung, to produce our semiconductor wafers.';

function item(id: number, overrides: Partial<ReviewItem> = {}, type: 'SUPPLIER' | 'CUSTOMER' = 'SUPPLIER'): ReviewItem {
  return {
    relationship: {
      id,
      company: { id: 1, name: 'NVIDIA Corporation', ticker: 'NVDA' },
      counterparty: { id: 10 + id, name: `Supplier ${id}`, ticker: null },
      type,
      provides: 'semiconductor wafers',
      evidence: id === 1 ? sentence : `Evidence ${id}.`,
      report: { form: '10-K', filingDate: '2026-02-25', url: `https://www.sec.gov/report-${id}` },
      reviewStatus: 'UNREVIEWED',
      reviewedAt: null,
      reviewNote: null,
    },
    reviewStatus: 'UNREVIEWED',
    reviewNote: null,
    reviewedBy: null,
    reviewedAt: null,
    usedByPortfolios: 1,
    ...overrides,
  };
}

type Call = { url: string; method: string; body: unknown };
let calls: Call[];

// Answers GET /api/reviews... with `listed(url)`, and PUT /api/relationships/{id}/review with `decided(id, body)`.
function fakeBackend(listed: (url: string) => ReviewItem[], decided?: (id: number, body: Record<string, string>) => ReviewItem) {
  calls = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET';
      const body = init?.body ? JSON.parse(String(init.body)) : undefined;
      calls.push({ url, method, body });
      if (method === 'PUT') {
        const id = Number(/relationships\/(\d+)\/review/.exec(url)![1]);
        return new Response(JSON.stringify(decided!(id, body)));
      }
      return new Response(JSON.stringify(listed(url)));
    }),
  );
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ReviewPage />
    </MemoryRouter>,
  );
}

const approvedBy = (found: ReviewItem): ReviewItem => ({
  ...found,
  reviewStatus: 'APPROVED',
  reviewedBy: 'analyst@firm.com',
  reviewedAt: '2026-10-15T01:00:00Z',
});

beforeEach(() => {
  tokenStore.set('test-token');
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  tokenStore.clear();
});

describe('Review page', () => {
  it('shows a loading message before the relationships arrive', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));

    renderPage();

    expect(screen.getByText('Loading…')).toBeInTheDocument();
  });

  it("shows the backend's message when the user is not an Analyst", async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ message: 'Only analysts can do this.' }), { status: 403 })),
    );

    renderPage();

    expect(await screen.findByText('Only analysts can do this.')).toBeInTheDocument();
  });

  it('says there is nothing to review, and what to do next, when the list is empty', async () => {
    fakeBackend(() => []);

    renderPage();

    expect(
      await screen.findByText('Nothing to review. Analyse a portfolio to find more relationships.'),
    ).toBeInTheDocument();
  });

  it('says that every decision applies to every portfolio manager', async () => {
    fakeBackend(() => [item(1)]);

    renderPage();

    await screen.findByText(sentence);
    expect(screen.getByText('Your decisions apply to every portfolio manager.')).toBeInTheDocument();
  });

  it('shows each relationship as a card with the companies, what is provided, the sentence, the report and the count', async () => {
    fakeBackend(() => [item(1, { usedByPortfolios: 3 })]);

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Supplier 1 → NVIDIA' })).toBeInTheDocument();
    expect(screen.getByText('Supplier 1 supplies NVIDIA: semiconductor wafers.')).toBeInTheDocument();
    expect(screen.getByText(sentence)).toBeInTheDocument();
    expect(screen.getByText('10-K, Filed 2026-02-25')).toBeInTheDocument();
    expect(screen.getByText('Used by 3 Portfolios')).toBeInTheDocument();
  });

  it('says "1 portfolio", not "1 portfolios"', async () => {
    fakeBackend(() => [item(1, { usedByPortfolios: 1 })]);

    renderPage();

    expect(await screen.findByText('Used by 1 Portfolio')).toBeInTheDocument();
  });

  it('draws the arrow from supplier to customer when the other company buys from the holding', async () => {
    fakeBackend(() => [item(1, {}, 'CUSTOMER')]);

    renderPage();

    expect(await screen.findByRole('heading', { name: 'NVIDIA → Supplier 1' })).toBeInTheDocument();
    expect(screen.getByText('Supplier 1 buys from NVIDIA: semiconductor wafers.')).toBeInTheDocument();
  });

  it('lists the relationships in the order the backend gives them', async () => {
    fakeBackend(() => [item(2, { usedByPortfolios: 3 }), item(1, { usedByPortfolios: 1 })]);

    renderPage();

    await screen.findByText('Used by 3 Portfolios');
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);
    expect(headings).toEqual(['Supplier 2 → NVIDIA', 'Supplier 1 → NVIDIA']);
  });

  it('opens the report at the evidence when Show in Report is pressed', async () => {
    fakeBackend(() => [item(1)]);
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Show in Report' }));

    expect(open).toHaveBeenCalledWith(
      'https://www.sec.gov/report-1#:~:text=We%20utilize%20foundries%2C%20such%20as,to%20produce%20our%20semiconductor%20wafers.',
      '_blank',
      'noopener,noreferrer',
    );
  });

  it('approves a relationship, and it leaves the Unreviewed list', async () => {
    fakeBackend(() => [item(1), item(2)], (id) => approvedBy(item(id)));
    renderPage();
    await screen.findByText(sentence);

    const card = screen.getByRole('heading', { name: 'Supplier 1 → NVIDIA' }).closest('li')!;
    await userEvent.click(within(card).getByRole('button', { name: 'Approve' }));

    expect(screen.queryByRole('heading', { name: 'Supplier 1 → NVIDIA' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Supplier 2 → NVIDIA' })).toBeInTheDocument();
    const put = calls.find((call) => call.method === 'PUT')!;
    expect(put.url).toBe('/api/relationships/1/review');
    expect(put.body).toMatchObject({ decision: 'APPROVED' });
  });

  it('says there is nothing left to review once the last one is decided', async () => {
    fakeBackend(() => [item(1)], (id) => approvedBy(item(id)));
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Approve' }));

    expect(
      await screen.findByText('Nothing to review. Analyse a portfolio to find more relationships.'),
    ).toBeInTheDocument();
  });

  it('asks for a note when Reject is pressed, and refuses to reject without one', async () => {
    fakeBackend(() => [item(1)]);
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Reject' }));
    expect(screen.getByLabelText('Why is this rejected?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Confirm Reject' }));

    expect(screen.getByText('Please say why this is rejected.')).toBeInTheDocument();
    expect(calls.some((call) => call.method === 'PUT')).toBe(false);

    // A note of only spaces is no note.
    await userEvent.type(screen.getByLabelText('Why is this rejected?'), '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm Reject' }));
    expect(calls.some((call) => call.method === 'PUT')).toBe(false);
  });

  it('rejects with the note, and the relationship leaves the Unreviewed list', async () => {
    fakeBackend(
      () => [item(1)],
      (id, body) => ({
        ...item(id),
        reviewStatus: 'REJECTED',
        reviewNote: body.note,
        reviewedBy: 'analyst@firm.com',
        reviewedAt: '2026-10-15T01:00:00Z',
      }),
    );
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Reject' }));
    await userEvent.type(screen.getByLabelText('Why is this rejected?'), 'Planned partner, not a supplier.');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm Reject' }));

    const put = calls.find((call) => call.method === 'PUT')!;
    expect(put.body).toEqual({ decision: 'REJECTED', note: 'Planned partner, not a supplier.' });
    expect(await screen.findByText(/Nothing to review/)).toBeInTheDocument();
  });

  it('closes the note box with Cancel and sends nothing', async () => {
    fakeBackend(() => [item(1)]);
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Reject' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByLabelText('Why is this rejected?')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Approve' })).toBeInTheDocument();
    expect(calls.some((call) => call.method === 'PUT')).toBe(false);
  });

  it("shows the backend's message when a decision is refused", async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init?: RequestInit) =>
        init?.method === 'PUT'
          ? new Response(JSON.stringify({ message: 'Relationship not found.' }), { status: 404 })
          : new Response(JSON.stringify([item(1)])),
      ),
    );
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Approve' }));

    expect(await screen.findByText('Relationship not found.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Supplier 1 → NVIDIA' })).toBeInTheDocument();
  });

  it('shows the Unreviewed ones first, and asks the backend for each filter when it is changed', async () => {
    fakeBackend(() => []);
    renderPage();
    await screen.findByText(/Nothing to review/);
    expect(calls.at(-1)!.url).toBe('/api/reviews?status=UNREVIEWED');
    expect(screen.getByRole('group', { name: 'Show' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: 'Approved' }));
    expect(calls.at(-1)!.url).toBe('/api/reviews?status=APPROVED');

    await userEvent.click(screen.getByRole('radio', { name: 'Rejected' }));
    expect(calls.at(-1)!.url).toBe('/api/reviews?status=REJECTED');

    await userEvent.click(screen.getByRole('radio', { name: 'All' }));
    expect(calls.at(-1)!.url).toBe('/api/reviews');
  });

  it('shows who decided, when, and why on a decided relationship, and keeps it in the All list when changed', async () => {
    const rejected = item(1, {
      reviewStatus: 'REJECTED',
      reviewNote: 'Planned partner, not a supplier.',
      reviewedBy: 'analyst@firm.com',
      reviewedAt: '2026-10-15T01:00:00Z',
    });
    fakeBackend(() => [rejected], (id) => approvedBy(item(id)));
    renderPage();
    await userEvent.click(await screen.findByRole('radio', { name: 'All' }));

    expect(await screen.findByText(/Rejected by analyst@firm.com.*Planned partner, not a supplier\./)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Approve' }));

    expect(await screen.findByText(/Approved by analyst@firm.com/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Supplier 1 → NVIDIA' })).toBeInTheDocument();
  });
});
