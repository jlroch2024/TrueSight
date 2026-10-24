import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStore } from '../api/client';
import { AiCallsPage } from './AiCallsPage';

const calls = [
  {
    id: 2,
    startedAt: '2026-10-25T09:30:00Z',
    purpose: 'JUDGE_NEWS',
    model: 'gemini-new',
    outcome: 'ANSWERED',
    durationMs: 1200,
    kept: 4,
    dropped: 1,
  },
  {
    id: 1,
    startedAt: '2026-10-24T08:00:00Z',
    purpose: 'FIND_RELATIONSHIPS',
    model: 'gemini-old',
    outcome: 'UNREADABLE',
    durationMs: 850,
    kept: null,
    dropped: null,
  },
] as const;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

beforeEach(() => {
  tokenStore.set('analyst-token');
});

afterEach(() => {
  tokenStore.clear();
});

describe('AI Calls', () => {
  it('lists calls newest first with their purpose, outcome, timing and kept and dropped counts', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(json({ calls, page: 0, totalPages: 2, total: 51 })),
    );

    render(<AiCallsPage />);

    const rows = await screen.findAllByRole('row');
    expect(within(rows[1]!).getByText('Judging News')).toBeInTheDocument();
    expect(within(rows[1]!).getByText('gemini-new')).toBeInTheDocument();
    expect(within(rows[1]!).getByText('Answered')).toBeInTheDocument();
    expect(within(rows[1]!).getByText('1.2 s')).toBeInTheDocument();
    expect(within(rows[1]!).getByText('4')).toBeInTheDocument();
    expect(within(rows[1]!).getByText('1')).toBeInTheDocument();
    expect(within(rows[2]!).getByText('Finding Relationships')).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
  });

  it('uses the chosen filters for both the list and the CSV export', async () => {
    const fetch = vi.fn(async (url: string) =>
      url.includes('/export')
        ? new Response('csv', { headers: { 'Content-Type': 'text/csv' } })
        : json({ calls: [], page: 0, totalPages: 0, total: 0 }),
    );
    vi.stubGlobal('fetch', fetch);
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn() });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:csv');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    render(<AiCallsPage />);
    await screen.findByText('No AI calls match these filters.');

    await userEvent.click(screen.getByRole('button', { name: /Purpose Every Purpose/ }));
    await userEvent.click(screen.getByRole('option', { name: 'Common Names' }));
    await userEvent.click(screen.getByRole('button', { name: /Outcome Every Outcome/ }));
    await userEvent.click(screen.getByRole('option', { name: 'Failed' }));
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-10-01' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-10-25' } });

    await userEvent.click(screen.getByRole('button', { name: 'Export CSV' }));

    const expected = 'purpose=COMMON_NAMES&outcome=FAILED&from=2026-10-01&to=2026-10-25';
    expect(fetch).toHaveBeenCalledWith(`/api/ai-calls?${expected}&page=0`, expect.anything());
    expect(fetch).toHaveBeenCalledWith(`/api/ai-calls/export?${expected}`, expect.anything());
  });

  it("opens a call with its report marker, Show Report and Gemini's exact answer", async () => {
    const reportUrl = 'https://www.sec.gov/Archives/report';
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url === '/api/ai-calls/2'
          ? json({
              ...calls[0],
              prompt: 'Read [report 12: 90628 characters] and answer.',
              response: '{\n  "assessment": "EVENT"\n}',
              error: null,
              reportId: 12,
              reportUrl,
            })
          : json({ calls: [calls[0]], page: 0, totalPages: 1, total: 1 }),
      ),
    );
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    render(<AiCallsPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Show' }));

    const dialog = await screen.findByRole('dialog', { name: 'AI Call' });
    expect(within(dialog).getByText(/Read \[report 12: 90628 characters\]/)).toBeInTheDocument();
    expect(within(dialog).getByText(/"assessment": "EVENT"/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Show Report' }));
    expect(open).toHaveBeenCalledWith(reportUrl, '_blank', 'noopener,noreferrer');
  });

  it('shows the empty state and a backend error', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(json({ calls: [], page: 0, totalPages: 0, total: 0 }))
      .mockResolvedValueOnce(json({ message: 'Only analysts can do this.' }, 403));
    vi.stubGlobal('fetch', fetch);
    const view = render(<AiCallsPage />);
    expect(await screen.findByText('No AI calls match these filters.')).toBeInTheDocument();

    view.unmount();
    render(<AiCallsPage />);
    expect(await screen.findByText('Only analysts can do this.')).toBeInTheDocument();
  });
});
