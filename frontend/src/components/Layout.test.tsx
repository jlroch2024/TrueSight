// Proves the sidebar shows a portfolio's own pages only while that portfolio is open, and that the header says who is
// logged in: their email, with "Analyst" under it for an Analyst, and the Analyst-only pages for Analysts alone.
// The list of portfolio pages is replaced with a sample here, because the real one is empty until stories add pages.
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { tokenStore } from '../api/client';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Layout } from './Layout';

vi.mock('../navigation', () => ({
  NAV_ITEMS: [{ label: 'Home', path: '/' }],
  ANALYST_NAV_ITEMS: [{ label: 'Analyst Review', path: '/review' }],
  PORTFOLIO_NAV_ITEMS: [{ label: 'Supply Chain', path: (id: string) => `/portfolios/${id}/supply-chain` }],
}));

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/portfolios/:portfolioId" element={<Layout />}>
          <Route path="supply-chain" element={<h1>Supply Chain</h1>} />
        </Route>
        <Route path="*" element={<Layout />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Layout', () => {
  it("shows the open portfolio's navigation button", async () => {
    renderAt('/portfolios/7');

    const button = screen.getByRole('button', { name: 'Supply Chain' });
    await userEvent.setup().click(button);
    expect(await screen.findByRole('heading', { name: 'Supply Chain' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Supply Chain' })).toHaveAttribute('aria-current', 'page');
  });

  it('Log Out forgets the login and goes to the Log In page', async () => {
    tokenStore.set('a-token');
    vi.stubGlobal('fetch', vi.fn(async (url: string) =>
      new Response(url === '/api/portfolios' ? '[]' : '{"email":"pm@example.com","role":"PORTFOLIO_MANAGER"}')));
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/login" element={<h1>Log In</h1>} />
          <Route path="*" element={<Layout />} />
        </Routes>
      </MemoryRouter>,
    );

    const header = screen.getByRole('banner');
    await userEvent.setup().click(within(header).getByRole('button', { name: 'Log Out' }));

    expect(tokenStore.get()).toBeNull();
    expect(screen.getByRole('heading', { name: 'Log In' })).toBeInTheDocument();
  });

  it('hides portfolio pages when no portfolio is open', () => {
    renderAt('/');

    expect(screen.queryByRole('button', { name: 'Supply Chain' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Analyst Review' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Home' })).toBeInTheDocument();
  });

  it('loads the current role for analyst navigation when logged in', async () => {
    tokenStore.set('a-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) =>
      new Response(url === '/api/portfolios' ? '[]' : '{"email":"analyst@example.com","role":"ANALYST"}'));
    renderAt('/');
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({
      headers: expect.any(Headers),
    })));
    expect(await screen.findByRole('button', { name: 'Analyst Review' })).toBeInTheDocument();
    expect(await screen.findByText('analyst@example.com')).toBeInTheDocument();
    fetchMock.mockRestore();
    tokenStore.clear();
  });

  describe('who is logged in', () => {
    function logInAs(role: 'PORTFOLIO_MANAGER' | 'ANALYST', email: string) {
      tokenStore.set('a-token');
      // The header also lists the portfolios, on every page; this user has none.
      vi.stubGlobal('fetch', vi.fn(async (url: string) =>
        new Response(url === '/api/portfolios' ? '[]' : JSON.stringify({ email, role }))));
    }

    afterEach(() => {
      tokenStore.clear();
      vi.unstubAllGlobals();
    });

    it('shows the email, with Analyst under it, for an Analyst', async () => {
      logInAs('ANALYST', 'analyst@firm.com');

      renderAt('/');

      const email = await screen.findByText('analyst@firm.com');
      const label = screen.getByText('Analyst');
      expect(label).toBeInTheDocument();
      // Both sit together in the account block above Log Out: the email first, then the label under it.
      expect(email.nextElementSibling).toBe(label);
      expect(email.parentElement).toBe(label.parentElement);
    });

    it('shows the email and nothing extra for a Portfolio Manager', async () => {
      logInAs('PORTFOLIO_MANAGER', 'pm@firm.com');

      renderAt('/');

      const email = await screen.findByText('pm@firm.com');
      expect(email.nextElementSibling).toBeNull();
      expect(screen.queryByText('Analyst')).not.toBeInTheDocument();
    });

    describe('the Profile button', () => {
      type Account = { email: string; role: 'PORTFOLIO_MANAGER' | 'ANALYST'; displayName: string | null };

      function logInWith(account: Account) {
        tokenStore.set('a-token');
        vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
          if (url === '/api/portfolios') return new Response('[]');
          if (url === '/api/me' && init?.method === 'PUT') {
            const { displayName } = JSON.parse(String(init.body)) as { displayName: string };
            return new Response(JSON.stringify({ ...account, displayName }));
          }
          return new Response(JSON.stringify(account));
        }));
      }

      it('is the email, as a button, until the user sets a display name', async () => {
        logInWith({ email: 'pm@firm.com', role: 'PORTFOLIO_MANAGER', displayName: null });

        renderAt('/');

        const button = await screen.findByRole('button', { name: 'Profile (pm@firm.com)' });
        expect(button).toHaveTextContent('pm@firm.com');
      });

      it('shows the display name instead of the email once it is set', async () => {
        logInWith({ email: 'jo@firm.com', role: 'ANALYST', displayName: 'Jo Tan' });

        renderAt('/');

        const button = await screen.findByRole('button', { name: 'Profile (Jo Tan)' });
        expect(button).toHaveTextContent('Jo Tan');
        expect(screen.queryByText('jo@firm.com')).not.toBeInTheDocument();
        expect(screen.getByText('Analyst')).toBeInTheDocument();
      });

      it('opens the Profile window, and Close puts the keyboard back on the button', async () => {
        logInWith({ email: 'pm@firm.com', role: 'PORTFOLIO_MANAGER', displayName: null });
        renderAt('/');
        const button = await screen.findByRole('button', { name: 'Profile (pm@firm.com)' });

        await userEvent.click(button);
        const profile = screen.getByRole('dialog', { name: 'Profile' });
        expect(within(profile).getByText('pm@firm.com')).toBeInTheDocument();
        await userEvent.click(within(profile).getByRole('button', { name: 'Close' }));

        expect(screen.queryByRole('dialog', { name: 'Profile' })).not.toBeInTheDocument();
        expect(button).toHaveFocus();
      });

      it('shows a saved display name in the header at once, replacing the email', async () => {
        logInWith({ email: 'pm@firm.com', role: 'PORTFOLIO_MANAGER', displayName: null });
        renderAt('/');
        await userEvent.click(await screen.findByRole('button', { name: 'Profile (pm@firm.com)' }));

        await userEvent.type(screen.getByLabelText('Display Name'), 'Jo Tan');
        await userEvent.click(screen.getByRole('button', { name: 'Save Display Name' }));

        expect(await screen.findByRole('button', { name: 'Profile (Jo Tan)' })).toHaveTextContent('Jo Tan');
        // The window still shows the email, which is never replaced there.
        expect(within(screen.getByRole('dialog', { name: 'Profile' })).getByText('pm@firm.com')).toBeInTheDocument();
      });
    });

    it('shows the Analyst-only items to an Analyst', async () => {
      logInAs('ANALYST', 'analyst@firm.com');

      renderAt('/');

      expect(await screen.findByRole('button', { name: 'Analyst Review' })).toBeInTheDocument();
    });

    it('shows no Analyst-only items to a Portfolio Manager', async () => {
      logInAs('PORTFOLIO_MANAGER', 'pm@firm.com');

      renderAt('/');

      await screen.findByText('pm@firm.com'); // the role has arrived, so the items would be there by now
      expect(screen.queryByRole('button', { name: 'Analyst Review' })).not.toBeInTheDocument();
    });

    it('offers Retry when the account cannot load, then shows the Analyst account and navigation', async () => {
      tokenStore.set('a-token');
      let accountAvailable = false;
      vi.stubGlobal('fetch', vi.fn(async (url: string) => {
        if (url === '/api/portfolios') return new Response('[]');
        if (!accountAvailable) {
          return new Response(JSON.stringify({ message: 'The server is restarting.' }), { status: 503 });
        }
        return new Response(JSON.stringify({ email: 'analyst@firm.com', role: 'ANALYST' }));
      }));

      renderAt('/');

      expect(await screen.findByText('Account Could Not Load')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Analyst Review' })).not.toBeInTheDocument();

      accountAvailable = true;
      await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

      expect(await screen.findByText('analyst@firm.com')).toBeInTheDocument();
      expect(screen.getByText('Analyst')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Analyst Review' })).toBeInTheDocument();
    });

    it('asks who is logged in only once', async () => {
      logInAs('ANALYST', 'analyst@firm.com');

      renderAt('/');
      await screen.findByText('analyst@firm.com');

      const asked = vi.mocked(fetch).mock.calls.filter(([url]) => url === '/api/auth/me');
      expect(asked).toHaveLength(1);
    });
  });
});
