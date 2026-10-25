import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PortfolioPicker } from './PortfolioPicker';

let backendUp: boolean;

beforeEach(() => {
  vi.restoreAllMocks();
  backendUp = false;
  vi.stubGlobal('fetch', vi.fn(async () => backendUp
    ? new Response(JSON.stringify([{
        id: 7,
        name: 'Technology',
        createdAt: '2026-10-01T00:00:00Z',
        newsStatus: 'IDLE',
        newsStatusReason: null,
        newsCheckedAt: null,
      }]))
    : new Response(JSON.stringify({ message: 'The server is restarting.' }), { status: 503 })));
});

describe('Portfolio picker', () => {
  it('shows a failed load, keeps Manage Portfolios available, and Retry loads the list', async () => {
    render(
      <MemoryRouter>
        <PortfolioPicker />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Portfolios Could Not Load')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Manage Portfolios' }));
    const manage = await screen.findByRole('dialog', { name: 'Manage Portfolios' });
    await userEvent.click(within(manage).getByRole('button', { name: 'Close' }));

    backendUp = true;
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByLabelText('Portfolio')).toHaveTextContent('Choose a Portfolio');
    expect(screen.queryByText('Portfolios Could Not Load')).not.toBeInTheDocument();
  });
});
