// The Analyst Desk's Events tool: the judged articles, Approve (stored as Confirmed), and Reject with the judgement it
// should be and a note (stored as Overruled). The headlines are real (the
// saved Google News answer for TSMC), and so is the noise reason (Gemini's saved answer); the event's reason is the
// test's own, and says so.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStore } from '../api/client';
import type { JudgedArticle } from '../api/newsReview';
import { TSMC, sampleNoise } from '../test/sampleNews';
import { EventReviewPage } from './EventReviewPage';

const [eventArticle, noiseArticle] = sampleNoise(2);

function judged(id: number, isEvent: boolean): JudgedArticle {
  const source = isEvent ? eventArticle! : noiseArticle!;
  return {
    id,
    article: { title: source.title, source: source.source, publishedAt: source.publishedAt, url: source.url },
    company: TSMC,
    judgement: isEvent
      ? { isEvent: true, eventType: 'PRODUCTION_STOP', severity: 'HIGH' }
      : { isEvent: false, eventType: null, severity: null },
    aiSaid: isEvent
      ? { isEvent: true, eventType: 'PRODUCTION_STOP', severity: 'HIGH' }
      : { isEvent: false, eventType: null, severity: null },
    confidence: 'MEDIUM',
    summary: null,
    reason: isEvent ? 'Set by the test: the headline reports a stoppage.' : source.assessment!.reason,
    decision: null,
  };
}

let lists: Record<string, JudgedArticle[]>;
let sent: { url: string; body: Record<string, unknown> }[];
let refuse: string | null;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

beforeEach(() => {
  tokenStore.set('a-token');
  lists = { EVENTS: [judged(1, true)], NOISE: [judged(2, false)], DECIDED: [] };
  sent = [];
  refuse = null;
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit = {}) => {
    if (init.method === 'PUT') {
      const body = JSON.parse(String(init.body)) as Record<string, unknown>;
      sent.push({ url, body });
      if (refuse) return json({ message: refuse }, 400);
      const id = Number(url.split('/').at(-1));
      const item = [...lists.EVENTS!, ...lists.NOISE!].find((candidate) => candidate.id === id)!;
      return json({
        ...item,
        decision: { decision: body.decision, decidedBy: 'Ana Analyst', decidedAt: '2026-10-26T03:00:00Z', note: body.note ?? null },
      });
    }
    const filter = new URL(url, 'http://localhost').searchParams.get('filter')!;
    return json(lists[filter]);
  }));
});

async function card(title: string) {
  return (await screen.findByRole('heading', { name: title })).closest('li')!;
}

describe('Events tab', () => {
  it('lists the undecided events with the AI\'s judgement, its reason and the company, and opens the article', async () => {
    render(<EventReviewPage />);
    const item = await card(eventArticle!.title);

    expect(item).toHaveTextContent(eventArticle!.source!);
    expect(item).toHaveTextContent('Taiwan Semiconductor Manufacturing');
    expect(item).toHaveTextContent('Production Stop · High');
    expect(item).toHaveTextContent('Medium Confidence');
    expect(item).toHaveTextContent('Set by the test: the headline reports a stoppage.');
    expect(item).not.toHaveTextContent('AI Said');
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    await userEvent.click(within(item).getByRole('button', { name: 'Open Article' }));
    expect(open).toHaveBeenCalledWith(eventArticle!.url, '_blank', 'noopener,noreferrer');
  });

  it('filters Events, Noise and Decided', async () => {
    lists.DECIDED = [];
    render(<EventReviewPage />);
    await card(eventArticle!.title);

    await userEvent.click(screen.getByRole('tab', { name: 'Noise' }));
    const noise = await card(noiseArticle!.title);
    expect(noise).toHaveTextContent('Noise');
    expect(noise).toHaveTextContent(noiseArticle!.assessment!.reason);
    expect(screen.queryByRole('heading', { name: eventArticle!.title })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: 'Decided' }));
    expect(await screen.findByText('No articles from the last 7 days have been decided yet.')).toBeInTheDocument();
    expect(vi.mocked(fetch).mock.calls.map(([url]) => String(url))).toEqual([
      '/api/news/assessments?filter=EVENTS',
      '/api/news/assessments?filter=NOISE',
      '/api/news/assessments?filter=DECIDED',
    ]);
  });

  it('confirms the AI\'s judgement with Approve, and the article leaves Events', async () => {
    render(<EventReviewPage />);
    const item = await card(eventArticle!.title);

    await userEvent.click(within(item).getByRole('button', { name: 'Approve' }));

    expect(sent).toEqual([{ url: '/api/news/assessments/1', body: { decision: 'CONFIRMED' } }]);
    expect(await screen.findByText('No undecided events from the last 7 days.')).toBeInTheDocument();
  });

  it('rejects an event as noise only with a note', async () => {
    render(<EventReviewPage />);
    const item = await card(eventArticle!.title);

    await userEvent.click(within(item).getByRole('button', { name: 'Reject' }));
    expect(within(item).getByLabelText(/Make It/)).toHaveTextContent('Noise');
    expect(within(item).queryByLabelText(/Event Type/)).not.toBeInTheDocument();
    const save = within(item).getByRole('button', { name: 'Save Rejection' });
    expect(save).toBeDisabled();

    await userEvent.type(within(item).getByLabelText('Note'), 'An earnings preview, not a disruption.');
    await userEvent.click(save);

    expect(sent).toEqual([{
      url: '/api/news/assessments/1',
      body: {
        decision: 'OVERRULED', isEvent: false, eventType: null, severity: null,
        note: 'An earnings preview, not a disruption.',
      },
    }]);
    expect(await screen.findByText('No undecided events from the last 7 days.')).toBeInTheDocument();
  });

  it('rejects noise as an event, choosing its type and severity', async () => {
    render(<EventReviewPage />);
    await card(eventArticle!.title);
    await userEvent.click(screen.getByRole('tab', { name: 'Noise' }));
    const item = await card(noiseArticle!.title);

    await userEvent.click(within(item).getByRole('button', { name: 'Reject' }));
    expect(within(item).getByLabelText(/Make It/)).toHaveTextContent('Event');
    await userEvent.click(within(item).getByLabelText(/Event Type/));
    await userEvent.click(screen.getByRole('option', { name: 'Labour Action' }));
    await userEvent.click(within(item).getByLabelText(/Severity/));
    await userEvent.click(screen.getByRole('option', { name: 'High' }));
    await userEvent.type(within(item).getByLabelText('Note'), 'The headline reports a strike at the plant.');
    await userEvent.click(within(item).getByRole('button', { name: 'Save Rejection' }));

    expect(sent[0]!.body).toEqual({
      decision: 'OVERRULED', isEvent: true, eventType: 'LABOUR_ACTION', severity: 'HIGH',
      note: 'The headline reports a strike at the plant.',
    });
  });

  it('will not save a rejection that changes nothing', async () => {
    render(<EventReviewPage />);
    const item = await card(eventArticle!.title);

    await userEvent.click(within(item).getByRole('button', { name: 'Reject' }));
    await userEvent.click(within(item).getByLabelText(/Make It/));
    await userEvent.click(screen.getByRole('option', { name: 'Event' }));
    await userEvent.type(within(item).getByLabelText('Note'), 'Same.');

    expect(within(item).getByRole('button', { name: 'Save Rejection' })).toBeDisabled();
    expect(item).toHaveTextContent("Choose a different judgement, or approve the AI's instead.");
  });

  it('shows a decided article\'s AI answer beside the analyst\'s decision', async () => {
    lists.DECIDED = [{
      ...judged(2, false),
      judgement: { isEvent: true, eventType: 'SUPPLY_SHORTAGE', severity: 'LOW' },
      decision: { decision: 'OVERRULED', decidedBy: 'Ana Analyst', decidedAt: '2026-10-26T03:00:00Z', note: 'Chip supply is short.' },
    }];
    render(<EventReviewPage />);
    await card(eventArticle!.title);
    await userEvent.click(screen.getByRole('tab', { name: 'Decided' }));
    const item = await card(noiseArticle!.title);

    expect(item).toHaveTextContent('Supply Shortage · Low');
    expect(item).toHaveTextContent('AI Said: Noise');
    expect(item).toHaveTextContent('Rejected by Ana Analyst on Oct 26, 2026: Chip supply is short.');
  });

  it('shows the backend\'s reason when a decision is refused, and keeps the article', async () => {
    refuse = "Please say why the AI's judgement is overruled.";
    render(<EventReviewPage />);
    const item = await card(eventArticle!.title);

    await userEvent.click(within(item).getByRole('button', { name: 'Approve' }));

    expect(await screen.findByRole('alert')).toHaveTextContent("Please say why the AI's judgement is overruled.");
    expect(screen.getByRole('heading', { name: eventArticle!.title })).toBeInTheDocument();
  });

  it('shows why the list could not be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'Only analysts can review news.' }, 403)));
    render(<EventReviewPage />);

    expect(await screen.findByText('Only analysts can review news.')).toBeInTheDocument();
  });
});
