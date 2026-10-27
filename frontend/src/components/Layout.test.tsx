// Proves that Layout says who is logged in, through the account menu every screen shows: their initials, then, opened,
// their name (the display name once set, otherwise the email) and their role, Profile and Log Out. There is no sidebar.
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { tokenStore } from '../api/client';
import { AccountMenu } from './AccountMenu';
import { Layout } from './Layout';

type Account = { email: string; role: 'PORTFOLIO_MANAGER' | 'ANALYST'; displayName?: string | null };

function logInWith(account: Account) {
  tokenStore.set('a-token');
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      if (url === '/api/me' && init?.method === 'PUT') {
        const { displayName } = JSON.parse(String(init.body)) as { displayName: string };
        return new Response(JSON.stringify({ ...account, displayName }));
      }
      return new Response(JSON.stringify({ displayName: null, ...account }));
    }),
  );
}

function renderAt(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<h1>Log In</h1>} />
        <Route element={<Layout />}>
          <Route path="*" element={<AccountMenu />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

async function openMenu(name: string) {
  await userEvent.click(await screen.findByRole('button', { name: `Account (${name})` }));
  return screen.getByRole('menu', { name: 'Account' });
}

afterEach(() => {
  tokenStore.clear();
  vi.unstubAllGlobals();
});

describe('Layout', () => {
  it('has no sidebar: a screen is everything inside it', async () => {
    logInWith({ email: 'pm@firm.com', role: 'PORTFOLIO_MANAGER' });
    renderAt('/portfolios/7');

    await screen.findByRole('button', { name: 'Account (pm@firm.com)' });
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
  });

  it('Log Out forgets the login and goes to the Log In page', async () => {
    logInWith({ email: 'pm@firm.com', role: 'PORTFOLIO_MANAGER' });
    renderAt();

    const menu = await openMenu('pm@firm.com');
    await userEvent.click(within(menu).getByRole('menuitem', { name: 'Log Out' }));

    expect(tokenStore.get()).toBeNull();
    expect(screen.getByRole('heading', { name: 'Log In' })).toBeInTheDocument();
  });

  describe('who is logged in', () => {
    it('shows the initials, then the email with Analyst under it, for an Analyst', async () => {
      logInWith({ email: 'analyst@firm.com', role: 'ANALYST' });
      renderAt();

      expect(await screen.findByRole('button', { name: 'Account (analyst@firm.com)' })).toHaveTextContent('AN');
      const menu = await openMenu('analyst@firm.com');
      const email = within(menu).getByText('analyst@firm.com');
      const role = within(menu).getByText('Analyst');
      // Both sit together in the account block: the name first, then the role under it.
      expect(email.nextElementSibling).toBe(role);
    });

    it('says Portfolio Manager for a Portfolio Manager', async () => {
      logInWith({ email: 'pm@firm.com', role: 'PORTFOLIO_MANAGER' });
      renderAt();

      const menu = await openMenu('pm@firm.com');
      expect(within(menu).getByText('Portfolio Manager')).toBeInTheDocument();
      expect(within(menu).queryByText('Analyst')).not.toBeInTheDocument();
    });

    it('shows the display name instead of the email once it is set, and its initials', async () => {
      logInWith({ email: 'jo@firm.com', role: 'ANALYST', displayName: 'Jo Tan' });
      renderAt();

      expect(await screen.findByRole('button', { name: 'Account (Jo Tan)' })).toHaveTextContent('JT');
      const menu = await openMenu('Jo Tan');
      expect(within(menu).getByText('Jo Tan')).toBeInTheDocument();
      expect(screen.queryByText('jo@firm.com')).not.toBeInTheDocument();
    });

    it('opens the Profile window, and Close puts the keyboard back on the account button', async () => {
      logInWith({ email: 'pm@firm.com', role: 'PORTFOLIO_MANAGER' });
      renderAt();
      const menu = await openMenu('pm@firm.com');

      await userEvent.click(within(menu).getByRole('menuitem', { name: 'Profile' }));
      const profile = screen.getByRole('dialog', { name: 'Profile' });
      expect(within(profile).getByText('pm@firm.com')).toBeInTheDocument();
      await userEvent.click(within(profile).getByRole('button', { name: 'Close' }));

      await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Profile' })).not.toBeInTheDocument());
      expect(screen.getByRole('button', { name: 'Account (pm@firm.com)' })).toHaveFocus();
    });

    it('shows a saved display name in the account menu at once, replacing the email', async () => {
      logInWith({ email: 'pm@firm.com', role: 'PORTFOLIO_MANAGER' });
      renderAt();
      await userEvent.click(within(await openMenu('pm@firm.com')).getByRole('menuitem', { name: 'Profile' }));

      await userEvent.type(screen.getByLabelText('Display Name'), 'Jo Tan');
      await userEvent.click(screen.getByRole('button', { name: 'Save Display Name' }));

      expect(await screen.findByRole('button', { name: 'Account (Jo Tan)' })).toHaveTextContent('JT');
      // The window still shows the email, which is never replaced there.
      expect(within(screen.getByRole('dialog', { name: 'Profile' })).getByText('pm@firm.com')).toBeInTheDocument();
    });

    it('offers Retry when the account cannot load, then shows the account', async () => {
      tokenStore.set('a-token');
      let accountAvailable = false;
      vi.stubGlobal(
        'fetch',
        vi.fn(async () =>
          accountAvailable
            ? new Response(JSON.stringify({ email: 'analyst@firm.com', role: 'ANALYST', displayName: null }))
            : new Response(JSON.stringify({ message: 'The server is restarting.' }), { status: 503 }),
        ),
      );
      renderAt();

      await userEvent.click(await screen.findByRole('button', { name: 'Account' }));
      const menu = screen.getByRole('menu', { name: 'Account' });
      expect(await within(menu).findByText('Account Could Not Load')).toBeInTheDocument();

      accountAvailable = true;
      await userEvent.click(within(menu).getByRole('menuitem', { name: /Retry/ }));

      expect(await screen.findByRole('button', { name: 'Account (analyst@firm.com)' })).toBeInTheDocument();
    });

    it('asks who is logged in only once', async () => {
      logInWith({ email: 'analyst@firm.com', role: 'ANALYST' });
      renderAt();
      await screen.findByRole('button', { name: 'Account (analyst@firm.com)' });

      const asked = vi.mocked(fetch).mock.calls.filter(([url]) => url === '/api/auth/me');
      expect(asked).toHaveLength(1);
    });
  });
});
