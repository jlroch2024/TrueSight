// Log In and Sign Up: one card at /login and /signup that switches between the two in place. Every Acceptance
// Criterion of Sign Up and Log In, against a fake backend.
import { render, screen, waitFor, within } from '@testing-library/react';
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

describe('Log In', () => {
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

    expect(await screen.findByRole('button', { name: 'Portfolio: Consumer Goods' })).toBeInTheDocument();
    expect(tokenStore.get()).toBe('new-token');
  });

  it('shows "Wrong email or password." when the login is refused', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(answer(401, { message: 'Wrong email or password.' })));
    renderLogIn();

    await logIn('pm@example.com', 'wrong-password');

    expect(await screen.findByText('Wrong email or password.')).toBeInTheDocument();
    expect(tokenStore.get()).toBeNull();
  });

  it('shows a refused log in in the same style as a field error, announced to screen readers', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(answer(401, { message: 'Wrong email or password.' })));
    renderLogIn();

    await logIn('pm@example.com', 'wrong-password');

    const message = await screen.findByRole('alert');
    expect(message).toHaveTextContent('Wrong email or password.');
    expect(message).toHaveClass('field-error');
  });

  it('shows an unreachable server in the same style as a field error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    renderLogIn();

    await logIn('pm@example.com', 'right-password');

    const message = await screen.findByRole('alert');
    expect(message).toHaveTextContent('Cannot reach the server.');
    expect(message).toHaveClass('field-error');
  });

  it("shows TrueSight's motto above Log In, and no welcome line", () => {
    renderLogIn();

    expect(screen.getByText('Supply-Chain Risk, Traced to the Source')).toBeInTheDocument();
    expect(screen.queryByText(/Welcome back/)).not.toBeInTheDocument();
  });

  it('offers Sign Up under the form, for someone new to TrueSight', () => {
    renderLogIn();

    expect(screen.getByText(/New to TrueSight\?/)).toContainElement(screen.getByRole('button', { name: 'Sign Up' }));
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
    expect(await screen.findByRole('heading', { name: 'Create Your Account' })).toBeInTheDocument();
  });
});

function renderSignUp() {
  return render(
    <MemoryRouter initialEntries={['/signup']}>
      <App />
    </MemoryRouter>,
  );
}

async function signUp(email: string, password: string, confirm = password) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  if (confirm) await user.type(screen.getByLabelText('Confirm Password'), confirm);
  await user.click(screen.getByRole('button', { name: 'Create Account' }));
}

describe('Sign Up', () => {
  it("is the same card in lime: Create Your Account, under TrueSight's motto, with Confirm Password", () => {
    renderSignUp();

    const card = screen.getByRole('heading', { level: 1, name: 'Create Your Account' }).closest('form')!;
    expect(card).toHaveClass('auth-card', 'sign-up');
    expect(within(card).getByText('Supply-Chain Risk, Traced to the Source')).toBeInTheDocument();
    expect(within(card).getByLabelText('Confirm Password')).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Create Account' })).toHaveAttribute('type', 'submit');
    // One card, as Log In is: no side panel beside it (Polish Log In and Sign Up).
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
    expect(screen.getByText(/Already Have an Account\?/)).toContainElement(screen.getByRole('button', { name: 'Log In' }));
  });

  it('refuses a Confirm Password that does not match, and sends nothing', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderSignUp();

    await signUp('new@example.com', 'long-enough', 'long-enougj');

    const confirm = screen.getByLabelText('Confirm Password');
    expect(confirm).toHaveAttribute('aria-invalid', 'true');
    expect(within(confirm.parentElement as HTMLElement).getByText('Passwords do not match.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends only the email and password when Confirm Password matches', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(url === '/api/auth/signup' ? answer(201, { token: 't', email: 'new@example.com' }) : answer(200, [])),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderSignUp();

    await signUp('new@example.com', 'long-enough');

    await screen.findByText(/You have no portfolios yet/);
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/auth/signup') as unknown as [string, RequestInit];
    expect(JSON.parse(init.body as string)).toEqual({ email: 'new@example.com', password: 'long-enough' });
  });

  it('signs up, is then logged in, and is asked to create a first portfolio', async () => {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
      if (url === '/api/auth/signup') return Promise.resolve(answer(201, { token: 'new-token', email: 'new@example.com' }));
      return Promise.resolve(answer(200, []));
    }));
    renderSignUp();

    await signUp('new@example.com', 'long-enough');

    expect(await screen.findByRole('heading', { name: 'Create Your First Portfolio' })).toBeInTheDocument();
    expect(tokenStore.get()).toBe('new-token');
  });

  it('shows the minimum password error before sending a request', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderSignUp();

    await signUp('new@example.com', 'short');

    expect(await screen.findByText('Password must be at least 8 characters.')).toBeInTheDocument();
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
    expect(await screen.findByText('Password must be at least 8 characters.')).toBeInTheDocument();

    await user.clear(password);
    await user.type(password, 'long-enough');
    expect(screen.queryByText('Password must be at least 8 characters.')).not.toBeInTheDocument();
    expect(password).toHaveAttribute('aria-invalid', 'false');
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
    await user.click(screen.getByRole('button', { name: 'Create Account' }));

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument();
    expect(screen.getByText('Password must be at least 8 characters.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows no hint under the password before anything is typed', () => {
    renderSignUp();

    expect(screen.queryByText(/8 characters/)).not.toBeInTheDocument();
    expect(screen.getByLabelText('Password')).not.toHaveAttribute('aria-describedby');
  });

  it('shows the duplicate email response under the email field', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(answer(409, { message: 'This email is already registered.' })));
    renderSignUp();

    await signUp('taken@example.com', 'long-enough');

    const email = screen.getByLabelText('Email');
    expect(email).toHaveAttribute('aria-invalid', 'true');
    expect(email).toHaveAttribute('aria-describedby', 'email-error');
    expect(within(email.parentElement as HTMLElement).getByText('This email is already registered.')).toBeInTheDocument();
  });

  it('switches to Log In with a button and has no account-switching link', async () => {
    const user = userEvent.setup();
    renderSignUp();

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Log In' }));
    expect(await screen.findByRole('heading', { name: 'Log In' })).toBeInTheDocument();
  });
});

describe('one card', () => {
  it('switchesBetweenLogInAndSignUpInPlaceWithConfirmPasswordOpeningAndClosing', async () => {
    const user = userEvent.setup();
    renderLogIn();
    const card = screen.getByRole('heading', { name: 'Log In' }).closest('form')!;
    expect(screen.queryByLabelText('Confirm Password')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Sign Up' }));
    expect(await screen.findByRole('heading', { name: 'Create Your Account' })).toBeInTheDocument();
    // The same card, changed in place: not a new one.
    expect(screen.getByRole('heading', { name: 'Create Your Account' }).closest('form')).toBe(card);
    expect(card).toHaveClass('sign-up');
    expect(within(card).getByLabelText('Confirm Password')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Log In' }));
    expect(await screen.findByRole('heading', { name: 'Log In' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Log In' }).closest('form')).toBe(card);
    expect(card).not.toHaveClass('sign-up');
    await waitFor(() => expect(screen.queryByLabelText('Confirm Password')).not.toBeInTheDocument());
  });
});
