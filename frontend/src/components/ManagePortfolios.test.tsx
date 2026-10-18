// One Portfolio Page, as the user sees it: the header's Portfolio list and Manage Portfolios window, /portfolios, and
// the sidebar. Every Manage My Portfolios criterion is kept, now in the window.
// The backend is a small fake that keeps portfolios in memory and answers like the real one.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { tokenStore } from '../api/client';
import { Layout } from './Layout';

type Stored = { id: number; name: string; createdAt: string };
let stored: Stored[];
let nextId: number;

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

  if (url === '/api/auth/me') return json({ email: 'pm@example.com', role: 'PORTFOLIO_MANAGER' });
  if (url === '/api/portfolios' && method === 'GET') {
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
  return <output aria-label="Address">{useLocation().pathname}</output>;
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

async function openWindow() {
  await userEvent.click(await screen.findByRole('button', { name: 'Manage Portfolios' }));
  const window = await screen.findByRole('dialog', { name: 'Manage Portfolios' });
  await within(window).findByText('Tech Book');
  return window;
}

beforeEach(() => {
  vi.restoreAllMocks();
  tokenStore.set('a-token');
  stored = [portfolio(1, 'Tech Book'), portfolio(2, 'Energy Book')];
  nextId = 10;
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
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
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
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await vi.waitFor(() => expect(screen.getByLabelText('Portfolio')).toHaveTextContent('Pharma Book'));
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

  it('deletes a portfolio only after the user confirms', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    renderAt('/portfolios/1');
    const manage = await openWindow();

    await userEvent.click(within(manage).getByRole('button', { name: 'Delete Energy Book' }));
    expect(confirm).toHaveBeenCalledOnce();
    expect(within(manage).getByText('Energy Book')).toBeInTheDocument();

    await userEvent.click(within(manage).getByRole('button', { name: 'Delete Energy Book' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await vi.waitFor(() => expect(within(manage).queryByText('Energy Book')).not.toBeInTheDocument());
    expect(address()).toHaveTextContent('/portfolios/1');
  });

  it('deleting the open portfolio opens another', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderAt('/portfolios/1');
    const manage = await openWindow();

    await userEvent.click(within(manage).getByRole('button', { name: 'Delete Tech Book' }));

    await vi.waitFor(() => expect(address()).toHaveTextContent('/portfolios/2'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('Open switches to the chosen portfolio', async () => {
    renderAt('/portfolios/1');
    const window = await openWindow();

    await userEvent.click(within(window).getByRole('button', { name: 'Open Energy Book' }));

    await vi.waitFor(() => expect(address()).toHaveTextContent('/portfolios/2'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('/portfolios', () => {
  it('opens the first portfolio', async () => {
    renderAt('/portfolios');

    // Sorted by name, Energy Book (id 2) comes first.
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

describe('header and sidebar', () => {
  it('the sidebar shows Portfolio and Supply Chain for the open portfolio, and no Portfolios page', async () => {
    renderAt('/portfolios/1');

    const sidebar = screen.getByRole('navigation', { name: 'Main' });
    expect(within(sidebar).getByRole('button', { name: 'Portfolio' })).toBeInTheDocument();
    expect(within(sidebar).getByRole('button', { name: 'Supply Chain' })).toBeInTheDocument();
    expect(within(sidebar).queryByRole('button', { name: 'Portfolios' })).not.toBeInTheDocument();
  });

  it('switching portfolios keeps the same kind of page: Supply Chain stays on Supply Chain', async () => {
    render(
      <MemoryRouter initialEntries={['/portfolios/1/supply-chain']}>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/portfolios/:portfolioId/supply-chain" element={<h1>Supply Chain</h1>} />
          </Route>
        </Routes>
        <Address />
      </MemoryRouter>,
    );

    await userEvent.click(await screen.findByLabelText('Portfolio'));
    await userEvent.click(screen.getByRole('option', { name: 'Energy Book' }));

    expect(address()).toHaveTextContent('/portfolios/2/supply-chain');
  });
});
