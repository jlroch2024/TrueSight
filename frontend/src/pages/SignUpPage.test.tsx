// The Sign Up page's Acceptance Criteria, against a fake backend.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { tokenStore } from '../api/client';

function answer(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status });
}

function renderSignUp() {
  return render(
    <MemoryRouter initialEntries={['/signup']}>
      <App />
    </MemoryRouter>,
  );
}

async function signUp(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign Up' }));
}

describe('SignUpPage', () => {
  it('signs up, is then logged in, and goes to Home', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn()
        .mockResolvedValueOnce(answer(201, { token: 'new-token', email: 'new@example.com' }))
        .mockResolvedValue(answer(200, { status: 'UP' })),
    );
    renderSignUp();

    await signUp('new@example.com', 'long-enough');

    expect(await screen.findByText('Running')).toBeInTheDocument();
    expect(tokenStore.get()).toBe('new-token');
  });

  it("shows the backend's message for a password that is too short", async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(answer(400, { message: 'password must be between 8 and 72 characters' })),
    );
    renderSignUp();

    await signUp('new@example.com', 'short');

    expect(await screen.findByText('password must be between 8 and 72 characters')).toBeInTheDocument();
  });

  it('shows "This email is already registered" for an email that has an account', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(answer(409, { message: 'This email is already registered' })));
    renderSignUp();

    await signUp('taken@example.com', 'long-enough');

    expect(await screen.findByText('This email is already registered')).toBeInTheDocument();
  });
});
