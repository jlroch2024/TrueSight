// Portfolios, as the user sees them: the workspace's portfolio menu and its Manage Portfolios window, /portfolios, and
// no sidebar. Every Manage My Portfolios criterion is kept, in the window.
// The backend is a small fake that keeps portfolios in memory and answers like the real one.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { tokenStore } from '../api/client';

type Stored = { id: number; name: string; createdAt: string };
let stored: Stored[];
let nextId: number;
let role: 'PORTFOLIO_MANAGER' | 'ANALYST';
// While true, listing portfolios fails, as if the backend were restarting.
let backendDown: boolean;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function portfolio(id: number, name: string): Stored {
  return { id, name, createdAt: '2026-10-01T00:00:00Z' };
}

async function fakeBackend(url: string, init: RequestInit = {}): Promise<Response> {
  const method = init.method ?? 'GET';
  const body = typeof init.body === 'string' ? JSON.parse(init.body) : {};
  const id = Number(url.split('/')[3]);
  const clash = (name: string, except?: number) => stored.some((p) => p.name === name && p.id !== except);
  const inUse = json({ message: 'You already have a portfolio with this name.' }, 409);

  if (url === '/api/auth/me') return json({ email: 'pm@example.com', role });
  if (url === '/api/portfolios' && method === 'GET') {
    if (backendDown) {
      return json({ message: 'The server is restarting.' }, 503);
    }
    return json([...stored].sort((a, b) => a.name.localeCompare(b.name)));
  }
  if (url === '/api/portfolios' && method === 'POST') {
    if (clash(body.name)) return inUse;
    const created = portfolio(nextId++, body.name);
    stored.push(created);
    return json(created, 201);
  }
  if (/^\/api\/portfolios\/\d+$/.test(url) && method === 'PUT') {
    if (clash(body.name, id)) return inUse;
    const found = stored.find((p) => p.id === id)!;
    found.name = body.name;
    return json(found);
  }
  if (/^\/api\/portfolios\/\d+$/.test(url) && method === 'DELETE') {
    stored = stored.filter((p) => p.id !== id);
    return new Response(null, { status: 204 });
  }
  // Everything else the portfolio page loads (holdings, news and so on) is empty here.
  return json([]);
}

/** Where the app is, so a test can check the address. */
function Address() {
  const { pathname, search } = useLocation();
  return <output aria-label="Address">{pathname + search}</output>;
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <Address />
    </MemoryRouter>,
  );
}

function address() {
  return screen.getByLabelText('Address');
}

// The open portfolio's menu, in the workspace's top bar: its button is named after the portfolio.
const picker = (name: string) => screen.findByRole('button', { name: `Portfolio: ${name}` });

async function openWindow() {
  await userEvent.click(await picker('Tech Book'));
  await userEvent.click(await screen.findByRole('menuitem', { name: 'Manage Portfolios' }));
  const window = await screen.findByRole('dialog', { name: 'Manage Portfolios' });
  await within(window).findByText('Tech Book');
  return window;
}

beforeEach(() => {
  vi.restoreAllMocks();
  tokenStore.set('a-token');
  stored = [portfolio(1, 'Tech Book'), portfolio(2, 'Energy Book')];
  nextId = 10;
  role = 'PORTFOLIO_MANAGER';
  backendDown = false;
  vi.stubGlobal('fetch', vi.fn(fakeBackend));
});

describe('Manage Portfolios window', () => {
  it('lists every portfolio, marks the open one Active, and Close closes it', async () => {
    renderAt('/portfolios/1');

    const window = await openWindow();

    const tech = within(window).getByText('Tech Book').closest('li')!;
    const energy = within(window).getByText('Energy Book').closest('li')!;
    expect(within(tech).getByText('Active')).toBeInTheDocument();
    expect(within(energy).queryByText('Active')).not.toBeInTheDocument();
    for (const name of ['Open', 'Rename', 'Delete']) {
      expect(within(energy).getByRole('button', { name: `${name} Energy Book` })).toBeInTheDocument();
    }

    await userEvent.click(within(window).getByRole('button', { name: 'Close' }));
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('opens over the whole screen, not inside the top bar it was opened from', async () => {
    renderAt('/portfolios/1');

    const window = await openWindow();

    // A frosted-glass bar holds a fixed window inside itself, so the window is drawn at the top of the page.
    expect(window.closest('.window-backdrop')!.parentElement).toBe(document.body);
    expect(window.closest('.island')).toBeNull();
  });

  it('has no links', async () => {
    renderAt('/portfolios/1');

    const window = await openWindow();

    expect(within(window).queryAllByRole('link')).toHaveLength(0);
  });

  it('creates a portfolio from a name, and opens it', async () => {
    renderAt('/portfolios/1');
    const window = await openWindow();

    await userEvent.type(within(window).getByLabelText('New Portfolio Name'), 'Pharma Book');
    await userEvent.click(within(window).getByRole('button', { name: 'Create Portfolio' }));

    await vi.waitFor(() => expect(address()).toHaveTextContent('/portfolios/10'));
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(await picker('Pharma Book')).toBeInTheDocument();
  });

  it('refuses a name the user already uses', async () => {
    renderAt('/portfolios/1');
    const window = await openWindow();

    await userEvent.type(within(window).getByLabelText('New Portfolio Name'), 'Energy Book');
    await userEvent.click(within(window).getByRole('button', { name: 'Create Portfolio' }));

    expect(await within(window).findByText('You already have a portfolio with this name.')).toBeInTheDocument();
    expect(stored).toHaveLength(2);
  });

  it('renames a portfolio', async () => {
    renderAt('/portfolios/1');
    const window = await openWindow();

    await userEvent.click(within(window).getByRole('button', { name: 'Rename Energy Book' }));
    const input = within(window).getByLabelText('New Name');
    await userEvent.clear(input);
    await userEvent.type(input, 'Utilities Book');
    await userEvent.click(within(window).getByRole('button', { name: 'Save' }));

    expect(await within(window).findByText('Utilities Book')).toBeInTheDocument();
    expect(within(window).queryByText('Energy Book')).not.toBeInTheDocument();
  });

  it('passes the renamed open portfolio to the page when reloading the list fails', async () => {
    renderAt('/portfolios/1');
    const window = await openWindow();

    await userEvent.click(within(window).getByRole('button', { name: 'Rename Tech Book' }));
    const input = within(window).getByLabelText('New Name');
    await userEvent.clear(input);
    await userEvent.type(input, 'Technology Book');
    backendDown = true;
    await userEvent.click(within(window).getByRole('button', { name: 'Save' }));

    expect(await picker('Technology Book')).toBeInTheDocument();
    expect(await within(window).findByText('The server is restarting.')).toBeInTheDocument();
  });

  it('refuses renaming to a name the user already uses', async () => {
    renderAt('/portfolios/1');
    const window = await openWindow();

    await userEvent.click(within(window).getByRole('button', { name: 'Rename Energy Book' }));
    const input = within(window).getByLabelText('New Name');
    await userEvent.clear(input);
    await userEvent.type(input, 'Tech Book');
    await userEvent.click(within(window).getByRole('button', { name: 'Save' }));

    expect(await within(window).findByText('You already have a portfolio with this name.')).toBeInTheDocument();
  });

  it('deletes a portfolio only after the user confirms in TrueSight\'s own window', async () => {
    renderAt('/portfolios/1');
    const manage = await openWindow();
    const deletes = () => vi.mocked(fetch).mock.calls.filter(([, init]) => init?.method === 'DELETE');

    await userEvent.click(within(manage).getByRole('button', { name: 'Delete Energy Book' }));
    const ask = screen.getByRole('alertdialog', { name: 'Delete Portfolio' });
    expect(ask).toHaveAccessibleDescription('Delete "Energy Book" and all its holdings? This cannot be undone.');
    expect(within(ask).getByRole('button', { name: 'Delete' })).toHaveClass('danger');
    expect(deletes()).toHaveLength(0);

    await userEvent.click(within(ask).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(within(manage).getByText('Energy Book')).toBeInTheDocument();
    expect(deletes()).toHaveLength(0);

    await userEvent.click(within(manage).getByRole('button', { name: 'Delete Energy Book' }));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete' }));
    await vi.waitFor(() => expect(within(manage).queryByText('Energy Book')).not.toBeInTheDocument());
    expect(deletes()).toHaveLength(1);
    expect(screen.getByRole('dialog', { name: 'Manage Portfolios' })).toBeInTheDocument();
    expect(address()).toHaveTextContent('/portfolios/1');
  });

  it('Escape in the confirmation closes only the confirmation', async () => {
    renderAt('/portfolios/1');
    const manage = await openWindow();

    await userEvent.click(within(manage).getByRole('button', { name: 'Delete Energy Book' }));
    await userEvent.keyboard('{Escape}');

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Manage Portfolios' })).toBeInTheDocument();
    expect(within(manage).getByText('Energy Book')).toBeInTheDocument();

    await userEvent.keyboard('{Escape}');
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('Rename selects the whole name, and Save or Cancel gives the focus back to Rename', async () => {
    renderAt('/portfolios/1');
    const manage = await openWindow();

    await userEvent.click(within(manage).getByRole('button', { name: 'Rename Energy Book' }));
    const input = within(manage).getByLabelText<HTMLInputElement>('New Name');
    expect(input).toHaveFocus();
    expect([input.selectionStart, input.selectionEnd]).toEqual([0, 'Energy Book'.length]);

    await userEvent.click(within(manage).getByRole('button', { name: 'Cancel' }));
    expect(within(manage).getByRole('button', { name: 'Rename Energy Book' })).toHaveFocus();

    await userEvent.click(within(manage).getByRole('button', { name: 'Rename Energy Book' }));
    await userEvent.keyboard('Utilities Book');
    await userEvent.click(within(manage).getByRole('button', { name: 'Save' }));
    expect(await within(manage).findByRole('button', { name: 'Rename Utilities Book' })).toHaveFocus();
  });

  it('deleting the open portfolio opens another', async () => {
    renderAt('/portfolios/1');
    const manage = await openWindow();

    await userEvent.click(within(manage).getByRole('button', { name: 'Delete Tech Book' }));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete' }));

    await vi.waitFor(() => expect(address()).toHaveTextContent('/portfolios/2'));
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('renaming the open portfolio shows the new name in its menu straight away', async () => {
    renderAt('/portfolios/1');
    expect(await picker('Tech Book')).toBeInTheDocument();
    const window = await openWindow();

    await userEvent.click(within(window).getByRole('button', { name: 'Rename Tech Book' }));
    const input = within(window).getByLabelText('New Name');
    await userEvent.clear(input);
    await userEvent.type(input, 'Chips Book');
    await userEvent.click(within(window).getByRole('button', { name: 'Save' }));

    expect(await picker('Chips Book')).toBeInTheDocument();
  });

  it('keeps Tab inside the window, and gives focus back to the portfolio menu when it closes', async () => {
    const user = userEvent.setup();
    renderAt('/portfolios/1');
    const window = await openWindow();

    // Many presses of Tab, then Shift+Tab: focus never leaves the window.
    for (let i = 0; i < 15; i++) {
      await user.tab();
      expect(window).toContainElement(document.activeElement as HTMLElement);
    }
    for (let i = 0; i < 15; i++) {
      await user.tab({ shift: true });
      expect(window).toContainElement(document.activeElement as HTMLElement);
    }

    await user.click(within(window).getByRole('button', { name: 'Close' }));
    expect(await picker('Tech Book')).toHaveFocus();
  });

  it('Open switches to the chosen portfolio', async () => {
    renderAt('/portfolios/1');
    const window = await openWindow();

    await userEvent.click(within(window).getByRole('button', { name: 'Open Energy Book' }));

    await vi.waitFor(() => expect(address()).toHaveTextContent('/portfolios/2'));
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});

describe('/portfolios', () => {
  it('opens the first portfolio', async () => {
    renderAt('/portfolios');

    // Sorted by name, Energy Book (id 2) comes first.
    await vi.waitFor(() => expect(address()).toHaveTextContent('/portfolios/2'));
  });

  it('offers Retry after a failed load, and Retry opens the first portfolio', async () => {
    backendDown = true;
    renderAt('/portfolios');

    expect(await screen.findByText('The server is restarting.')).toBeInTheDocument();
    backendDown = false;
    await userEvent.click(within(screen.getByRole('main')).getByRole('button', { name: 'Retry' }));

    await vi.waitFor(() => expect(address()).toHaveTextContent('/portfolios/2'));
  });

  it('asks a user with none to create one, and creating it opens it', async () => {
    stored = [];
    renderAt('/portfolios');

    expect(await screen.findByRole('heading', { name: 'Create Your First Portfolio' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('New Portfolio Name'), 'Tech Book');
    await userEvent.click(screen.getByRole('button', { name: 'Create Portfolio' }));

    await vi.waitFor(() => expect(address()).toHaveTextContent('/portfolios/10'));
  });
});

describe('no sidebar: the portfolio menu and the Analyst Desk', () => {
  it('has no sidebar of portfolio pages, and no Portfolios page', async () => {
    renderAt('/portfolios/1');

    await screen.findByRole('button', { name: 'Portfolio: Tech Book' });
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument();
    for (const page of ['Supply Chain', 'Alerts', 'Portfolios']) {
      expect(screen.queryByRole('button', { name: page })).not.toBeInTheDocument();
    }
  });

  it('an Analyst on the Analyst Desk can get back to a portfolio', async () => {
    role = 'ANALYST';
    renderAt('/review');

    // Sorted by name, Energy Book (id 2) is the first.
    await userEvent.click(await screen.findByRole('button', { name: 'Energy Book' }));

    await vi.waitFor(() => expect(address()).toHaveTextContent('/portfolios/2'));
  });

  it('switching portfolios from the portfolio menu keeps the open drawer tab', async () => {
    renderAt('/portfolios/1?tab=alerts');

    await userEvent.click(await screen.findByRole('button', { name: 'Portfolio: Tech Book' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Energy Book' }));

    await vi.waitFor(() => expect(address()).toHaveTextContent('/portfolios/2?tab=alerts'));
  });
});
