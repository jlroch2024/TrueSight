// The Analyst Desk: the open tab lives in the address, and the Relationships tab is today's Review page. The backend is
// a small fake: who is logged in, the portfolios for the header, and one relationship waiting for review.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { tokenStore } from '../api/client';
import type { ReviewItem } from '../api/reviews';

/** NVIDIA's own words about TSMC, from its 10-K in docs/examples. */
const evidence =
  'We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC, and Samsung ' +
  'Electronics Co., Ltd., or Samsung, to produce our semiconductor wafers.';

const waiting: ReviewItem = {
  relationship: {
    id: 1,
    company: { id: 1, name: 'NVIDIA Corporation', displayName: 'NVIDIA', ticker: 'NVDA' },
    counterparty: {
      id: 2,
      name: 'Taiwan Semiconductor Manufacturing Company Limited',
      displayName: 'Taiwan Semiconductor Manufacturing',
      ticker: 'TSM',
    },
    type: 'SUPPLIER',
    provides: 'semiconductor wafers',
    evidence,
    source: 'AI',
    report: { form: '10-K', filingDate: '2026-02-25', url: 'https://www.sec.gov/report-1' },
    reviewStatus: 'UNREVIEWED',
    criticality: null,
    criticalityEvidence: null,
    reviewedAt: null,
    reviewNote: null,
  },
  reviewStatus: 'UNREVIEWED',
  reviewNote: null,
  reviewedBy: null,
  reviewedAt: null,
  usedByPortfolios: 1,
  secondCheck: null,
  secondCheckReason: null,
};

function json(body: unknown) {
  return new Response(JSON.stringify(body));
}

/** Where the app is, so a test can check the address. */
function Address() {
  const { pathname, search } = useLocation();
  return <output aria-label="Address">{pathname + search}</output>;
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <Address />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  tokenStore.set('a-token');
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      if (url === '/api/auth/me') return json({ email: 'analyst@example.com', role: 'ANALYST' });
      if (url.startsWith('/api/reviews')) return json([waiting]);
      if (url.startsWith('/api/ai-calls')) return json({ calls: [], page: 0, totalPages: 0, total: 0 });
      return json([]);
    }),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  tokenStore.clear();
});

describe('Analyst Desk', () => {
  it('opens on the Relationships tab, which is the review list as before', async () => {
    renderAt('/review');

    expect(screen.getByRole('heading', { name: 'Analyst Desk' })).toBeInTheDocument();
    const tab = screen.getByRole('tab', { name: 'Relationships' });
    expect(tab).toHaveAttribute('aria-selected', 'true');
    const panel = screen.getByRole('tabpanel', { name: 'Relationships' });
    expect(within(panel).getByRole('heading', { name: 'Review' })).toBeInTheDocument();
    expect(
      await within(panel).findByRole('heading', {
        name: 'Taiwan Semiconductor Manufacturing → NVIDIA',
      }),
    ).toBeInTheDocument();
    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/reviews', expect.anything());
  });

  it('opens the tab named in the address, so it survives a reload', () => {
    renderAt('/review?tab=relationships');

    expect(screen.getByRole('tab', { name: 'Relationships' })).toHaveAttribute('aria-selected', 'true');
  });

  it('opens the first tab for a tab it does not know', () => {
    renderAt('/review?tab=no-such-tab');

    expect(screen.getByRole('tab', { name: 'Relationships' })).toHaveAttribute('aria-selected', 'true');
  });

  it('opens the AI Calls tab named in the address', async () => {
    renderAt('/review?tab=ai-calls');

    expect(screen.getByRole('tab', { name: 'AI Calls' })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByText('No AI calls match these filters.')).toBeInTheDocument();
    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/ai-calls?page=0', expect.anything());
  });

  it('puts the chosen tab in the address', async () => {
    renderAt('/review');

    await userEvent.click(screen.getByRole('tab', { name: 'Relationships' }));

    expect(screen.getByLabelText('Address')).toHaveTextContent('/review?tab=relationships');
  });

  it('the tabs are buttons, and there are no links', () => {
    renderAt('/review');

    const tab = screen.getByRole('tab', { name: 'Relationships' });
    expect(tab.tagName).toBe('BUTTON');
    expect(screen.getByRole('tablist', { name: 'Analyst Desk' })).toContainElement(tab);
    expect(screen.queryAllByRole('link')).toHaveLength(0);
  });

  it('the sidebar says Analyst Desk', async () => {
    renderAt('/review');

    const sidebar = screen.getByRole('navigation', { name: 'Main' });
    expect(await within(sidebar).findByRole('button', { name: 'Analyst Desk' })).toBeInTheDocument();
    expect(within(sidebar).queryByRole('button', { name: 'Review' })).not.toBeInTheDocument();
  });
});
