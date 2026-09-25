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
  it('logs in, keeps the token, and goes to Home', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn()
        .mockResolvedValueOnce(answer(200, { token: 'new-token', email: 'pm@example.com' }))
        .mockResolvedValue(answer(200, { status: 'UP' })),
    );
    renderLogIn();

    await logIn('pm@example.com', 'right-password');

    expect(await screen.findByText('Running')).toBeInTheDocument();
    expect(tokenStore.get()).toBe('new-token');
  });

  it('shows "Wrong email or password" when the login is refused', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(answer(401, { message: 'Wrong email or password' })));
    renderLogIn();

    await logIn('pm@example.com', 'wrong-password');

    expect(await screen.findByText('Wrong email or password')).toBeInTheDocument();
    expect(tokenStore.get()).toBeNull();
  });
});
