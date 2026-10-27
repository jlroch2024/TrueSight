// The Profile window: the display name, and changing the password. The backend is a small fake that answers as the real
// one does: the display name comes back as saved, and a wrong current password is a 400 with its own message.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Me } from '../api/auth';
import { tokenStore } from '../api/client';
import { ProfileWindow } from './ProfileWindow';

const me: Me = { email: 'jo@example.com', role: 'PORTFOLIO_MANAGER', displayName: null };

type Call = { url: string; method: string; body: Record<string, string> };
let calls: Call[];
let currentPassword: string;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function fakeBackend(url: string, init: RequestInit = {}): Response {
  const body = init.body ? (JSON.parse(String(init.body)) as Record<string, string>) : {};
  calls.push({ url, method: init.method ?? 'GET', body });
  if (url === '/api/me') {
    if (!body.displayName?.trim() || body.displayName.length > 100) {
      return json({ message: 'displayName must be between 1 and 100 characters' }, 400);
    }
    return json({ ...me, displayName: body.displayName });
  }
  if (url === '/api/me/password') {
    if (body.currentPassword !== currentPassword) return json({ message: 'Your current password is not right.' }, 400);
    currentPassword = body.newPassword!;
    return new Response(null, { status: 204 });
  }
  return json({ message: 'Not found.' }, 404);
}

function open(account: Me = me) {
  const onChange = vi.fn();
  const onClose = vi.fn();
  render(<ProfileWindow me={account} onChange={onChange} onClose={onClose} />);
  return { onChange, onClose };
}

beforeEach(() => {
  tokenStore.set('test-token');
  calls = [];
  currentPassword = 'old-password';
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => fakeBackend(url, init)));
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const dialog = () => screen.getByRole('dialog', { name: 'Profile' });
const sent = (url: string) => calls.filter((call) => call.url === url);

describe('Profile window: the account', () => {
  it('shows the email, which cannot be changed, and no way to edit it', () => {
    open();

    expect(within(dialog()).getByText('jo@example.com')).toBeInTheDocument();
    expect(within(dialog()).getByText('Your email cannot be changed.')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /email/i })).not.toBeInTheDocument();
  });

  it('closes with its Close button, with Escape, and by clicking outside it', async () => {
    const { onClose } = open();

    await userEvent.click(screen.getByRole('button', { name: 'Close' }));
    await userEvent.keyboard('{Escape}');
    await userEvent.pointer({ keys: '[MouseLeft]', target: dialog().parentElement! });

    expect(onClose).toHaveBeenCalledTimes(3);
  });
});

describe('Profile window: the display name', () => {
  it('starts with the current display name', () => {
    open({ ...me, displayName: 'Jo Tan' });

    expect(screen.getByLabelText('Display Name')).toHaveValue('Jo Tan');
  });

  it('saves a display name, tells the page the account as it now is, and says it was saved', async () => {
    const { onChange } = open();

    await userEvent.type(screen.getByLabelText('Display Name'), '  Jo Tan ');
    await userEvent.click(screen.getByRole('button', { name: 'Save Display Name' }));

    expect(sent('/api/me')).toEqual([{ url: '/api/me', method: 'PUT', body: { displayName: 'Jo Tan' } }]);
    expect(onChange).toHaveBeenCalledWith({ ...me, displayName: 'Jo Tan' });
    expect(await screen.findByText('Display name saved.')).toBeInTheDocument();
    expect(screen.getByLabelText('Display Name')).toHaveValue('Jo Tan');
  });

  it('refuses a blank name before sending anything', async () => {
    const { onChange } = open();

    await userEvent.type(screen.getByLabelText('Display Name'), '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Save Display Name' }));

    expect(await screen.findByText('Enter a display name.')).toBeInTheDocument();
    expect(sent('/api/me')).toEqual([]);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('limits the name field to 100 characters', () => {
    open();

    expect(screen.getByLabelText('Display Name')).toHaveAttribute('maxLength', '100');
  });

  it("shows the backend's message when it refuses the name", async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'displayName must be between 1 and 100 characters' }, 400)));
    const { onChange } = open();

    await userEvent.type(screen.getByLabelText('Display Name'), 'Jo');
    await userEvent.click(screen.getByRole('button', { name: 'Save Display Name' }));

    expect(await screen.findByText('displayName must be between 1 and 100 characters')).toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });
});

describe('Profile window: the password', () => {
  async function fill(current: string, next: string, confirm: string) {
    await userEvent.type(screen.getByLabelText('Current Password'), current);
    await userEvent.type(screen.getByLabelText('New Password'), next);
    await userEvent.type(screen.getByLabelText('Confirm New Password'), confirm);
  }

  it('changes the password with the right current one, sends only the two passwords, and clears the fields', async () => {
    open();

    await fill('old-password', 'a-brand-new-password', 'a-brand-new-password');
    await userEvent.click(screen.getByRole('button', { name: 'Change Password' }));

    expect(sent('/api/me/password')).toEqual([
      {
        url: '/api/me/password',
        method: 'PUT',
        body: { currentPassword: 'old-password', newPassword: 'a-brand-new-password' },
      },
    ]);
    expect(await screen.findByText('Password changed. You are still logged in.')).toBeInTheDocument();
    expect(screen.getByLabelText('Current Password')).toHaveValue('');
    expect(screen.getByLabelText('New Password')).toHaveValue('');
    expect(screen.getByLabelText('Confirm New Password')).toHaveValue('');
    expect(tokenStore.get()).toBe('test-token');
  });

  it('says "Your current password is not right." when the backend refuses the current password', async () => {
    open();

    await fill('not-my-password', 'a-brand-new-password', 'a-brand-new-password');
    await userEvent.click(screen.getByRole('button', { name: 'Change Password' }));

    expect(await screen.findByText('Your current password is not right.')).toBeInTheDocument();
    expect(screen.queryByText('Password changed. You are still logged in.')).not.toBeInTheDocument();
    // They stay logged in, and can try again.
    expect(tokenStore.get()).toBe('test-token');
    expect(screen.getByRole('button', { name: 'Change Password' })).toBeEnabled();
  });

  it('catches a new password that does not match its confirmation before sending anything', async () => {
    open();

    await fill('old-password', 'a-brand-new-password', 'a-different-password');
    await userEvent.click(screen.getByRole('button', { name: 'Change Password' }));

    expect(await screen.findByText('Passwords do not match.')).toBeInTheDocument();
    expect(sent('/api/me/password')).toEqual([]);
  });

  it('holds the new password to the same rules as Sign Up, before sending anything', async () => {
    open();

    await fill('old-password', 'short', 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Change Password' }));

    expect(await screen.findByText('Password must be at least 8 characters.')).toBeInTheDocument();
    expect(sent('/api/me/password')).toEqual([]);

    await userEvent.clear(screen.getByLabelText('New Password'));
    await userEvent.type(screen.getByLabelText('New Password'), 'x'.repeat(73));
    expect(await screen.findByText('Password must be 72 characters or fewer.')).toBeInTheDocument();
  });

  it('asks for the current password before sending anything', async () => {
    open();

    await userEvent.type(screen.getByLabelText('New Password'), 'a-brand-new-password');
    await userEvent.type(screen.getByLabelText('Confirm New Password'), 'a-brand-new-password');
    await userEvent.click(screen.getByRole('button', { name: 'Change Password' }));

    expect(await screen.findByText('Enter your current password.')).toBeInTheDocument();
    expect(sent('/api/me/password')).toEqual([]);
  });

  it('clears the old "changed" message once the user starts typing another change', async () => {
    open();
    await fill('old-password', 'a-brand-new-password', 'a-brand-new-password');
    await userEvent.click(screen.getByRole('button', { name: 'Change Password' }));
    await screen.findByText('Password changed. You are still logged in.');

    await userEvent.type(screen.getByLabelText('Current Password'), 'a');

    expect(screen.queryByText('Password changed. You are still logged in.')).not.toBeInTheDocument();
  });
});
