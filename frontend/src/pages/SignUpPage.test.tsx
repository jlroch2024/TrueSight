// The Sign Up page's Acceptance Criteria, against a fake backend.
import { render, screen, within } from '@testing-library/react';
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
  it('signs up, is then logged in, and is asked to create a first portfolio', async () => {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
      if (url === '/api/auth/signup') return Promise.resolve(answer(201, { token: 'new-token', email: 'new@example.com' }));
      return Promise.resolve(answer(200, []));
    }));
    renderSignUp();

    await signUp('new@example.com', 'long-enough');

    expect(await screen.findByText('You have no portfolios yet. Create one above to get started.')).toBeInTheDocument();
    expect(tokenStore.get()).toBe('new-token');
  });

  it('shows the minimum password error before sending a request', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderSignUp();

    await signUp('new@example.com', 'short');

    expect(await screen.findByText(/Password should be at least 8 characters/)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('validates an email on blur and clears the error as the user corrects it', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderSignUp();

    const email = screen.getByLabelText('Email');
    await user.type(email, 'gtfsdege');
    await user.tab();

    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    await user.clear(email);
    await user.type(email, 'new@example.com');
    expect(screen.queryByText('Enter a valid email address.')).not.toBeInTheDocument();
  });

  it('clears the short-password error as the user fixes the password', async () => {
    const user = userEvent.setup();
    renderSignUp();

    const password = screen.getByLabelText('Password');
    await user.type(password, 'short');
    await user.tab();
    expect(await screen.findByText(/Password should be at least 8 characters/)).toBeInTheDocument();

    await user.clear(password);
    await user.type(password, 'long-enough');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows a maximum-length error for sign-up passwords over 72 characters', async () => {
    const user = userEvent.setup();
    renderSignUp();

    const password = screen.getByLabelText('Password');
    await user.type(password, 'a'.repeat(73));
    await user.tab();

    expect(await screen.findByText('Password must be 72 characters or fewer.')).toBeInTheDocument();
  });

  it('shows every field error and sends nothing when submitted with invalid values', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderSignUp();

    await user.type(screen.getByLabelText('Email'), 'gtfsdege');
    await user.type(screen.getByLabelText('Password'), 'short');
    await user.click(screen.getByRole('button', { name: 'Sign Up' }));

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument();
    expect(screen.getByText(/Password should be at least 8 characters/)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows the duplicate email response under the email field', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(answer(409, { message: 'This email is already registered' })));
    renderSignUp();

    await signUp('taken@example.com', 'long-enough');

    const email = screen.getByLabelText('Email');
    expect(email).toHaveAttribute('aria-invalid', 'true');
    expect(email).toHaveAttribute('aria-describedby', 'email-error');
    expect(within(email.parentElement as HTMLElement).getByText('This email is already registered')).toBeInTheDocument();
  });

  it('switches to Log In with a button and has no account-switching link', async () => {
    const user = userEvent.setup();
    renderSignUp();

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Log In' }));
    expect(await screen.findByRole('heading', { name: 'Log In' })).toBeInTheDocument();
  });
});
