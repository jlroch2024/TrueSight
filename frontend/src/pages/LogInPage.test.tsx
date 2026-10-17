// The Log In page's Acceptance Criteria, against a fake backend.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { tokenStore } from '../api/client';

function answer(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status });
}

function renderLogIn() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <App />
    </MemoryRouter>,
  );
}

async function logIn(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Log In' }));
}

describe('LogInPage', () => {
  it('logs in, keeps the token, and opens the first portfolio', async () => {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
      if (url === '/api/auth/login') return Promise.resolve(answer(200, { token: 'new-token', email: 'pm@example.com' }));
      if (url === '/api/portfolios') {
        return Promise.resolve(answer(200, [{ id: 7, name: 'Consumer Goods', createdAt: '2026-10-01T00:00:00Z' }]));
      }
      return Promise.resolve(answer(200, []));
    }));
    renderLogIn();

    await logIn('pm@example.com', 'right-password');

    expect(await screen.findByRole('heading', { name: 'Consumer Goods' })).toBeInTheDocument();
    expect(tokenStore.get()).toBe('new-token');
  });

  it('shows "Wrong email or password" when the login is refused', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(answer(401, { message: 'Wrong email or password' })));
    renderLogIn();

    await logIn('pm@example.com', 'wrong-password');

    expect(await screen.findByText('Wrong email or password')).toBeInTheDocument();
    expect(tokenStore.get()).toBeNull();
  });

  it('shows email and required-password errors on submit without sending a request', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderLogIn();

    await user.type(screen.getByLabelText('Email'), 'gtfsdege');
    await user.click(screen.getByRole('button', { name: 'Log In' }));

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument();
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.queryByText(/characters/i)).not.toBeInTheDocument();
  });

  it('switches to Sign Up with a button and has no account-switching link', async () => {
    const user = userEvent.setup();
    renderLogIn();

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Sign Up' }));
    expect(await screen.findByRole('heading', { name: 'Sign Up' })).toBeInTheDocument();
  });
});
