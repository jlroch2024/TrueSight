// Manage My Portfolios, as the user sees it: the Portfolios page, the portfolio page, and the switcher in the header.
// The backend is a small fake that keeps portfolios in memory and answers like the real one.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';

type Stored = { id: number; name: string; createdAt: string };
let stored: Stored[];

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function fakeBackend(url: string, init: RequestInit = {}): Promise<Response> {
  const method = init.method ?? 'GET';
  const body = init.body ? JSON.parse(init.body as string) : {};
  const id = Number(url.split('/').pop());
  const clash = (name: string, except?: number) => stored.some((p) => p.name === name && p.id !== except);
  const inUse = json({ message: 'You already have a portfolio with this name.' }, 409);

  if (method === 'GET') {
    return Promise.resolve(json([...stored].sort((a, b) => a.name.localeCompare(b.name))));
  }
  if (method === 'POST') {
    if (clash(body.name)) return Promise.resolve(inUse);
    const created = { id: stored.length + 10, name: body.name, createdAt: '2026-09-25T00:00:00Z' };
    stored.push(created);
    return Promise.resolve(json(created, 201));
  }
  if (method === 'PUT') {
    if (clash(body.name, id)) return Promise.resolve(inUse);
    const portfolio = stored.find((p) => p.id === id)!;
    portfolio.name = body.name;
    return Promise.resolve(json(portfolio));
  }
  stored = stored.filter((p) => p.id !== id);
  return Promise.resolve(new Response(null, { status: 204 }));
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
  stored = [];
  vi.stubGlobal('fetch', vi.fn(fakeBackend));
});

describe('Portfolios page', () => {
  it('asks a new user to create a portfolio', async () => {
    renderAt('/portfolios');

    expect(await screen.findByText('You have no portfolios yet. Create one above to get started.')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Main' })).toHaveTextContent('Portfolios');
  });

  it('creates a portfolio from a name', async () => {
    renderAt('/portfolios');

    await userEvent.type(await screen.findByLabelText('New Portfolio Name'), 'Tech Book');
    await userEvent.click(screen.getByRole('button', { name: 'Create Portfolio' }));

    expect(await screen.findByRole('link', { name: 'Tech Book' })).toHaveAttribute('href', '/portfolios/10');
  });

  it('refuses a name the user already uses', async () => {
    stored = [{ id: 1, name: 'Tech Book', createdAt: '' }];
    renderAt('/portfolios');

    await userEvent.type(await screen.findByLabelText('New Portfolio Name'), 'Tech Book');
    await userEvent.click(screen.getByRole('button', { name: 'Create Portfolio' }));

    expect(await screen.findByText('You already have a portfolio with this name.')).toBeInTheDocument();
  });

  it('renames a portfolio', async () => {
    stored = [{ id: 1, name: 'Tech Book', createdAt: '' }];
    renderAt('/portfolios');

    await userEvent.click(await screen.findByRole('button', { name: 'Rename' }));
    const input = screen.getByLabelText('New Name');
    await userEvent.clear(input);
    await userEvent.type(input, 'Energy Book');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('link', { name: 'Energy Book' })).toBeInTheDocument();
  });

  it('refuses renaming to a name the user already uses', async () => {
    stored = [
      { id: 1, name: 'Energy Book', createdAt: '' },
      { id: 2, name: 'Tech Book', createdAt: '' },
    ];
    renderAt('/portfolios');

    const tech = (await screen.findByRole('link', { name: 'Tech Book' })).closest('li')!;
    await userEvent.click(within(tech).getByRole('button', { name: 'Rename' }));
    const input = screen.getByLabelText('New Name');
    await userEvent.clear(input);
    await userEvent.type(input, 'Energy Book');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('You already have a portfolio with this name.')).toBeInTheDocument();
  });

  it('deletes a portfolio only after the user confirms', async () => {
    stored = [{ id: 1, name: 'Tech Book', createdAt: '' }];
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    renderAt('/portfolios');

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
    expect(confirm).toHaveBeenCalledOnce();
    expect(screen.getByRole('link', { name: 'Tech Book' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(await screen.findByText('You have no portfolios yet. Create one above to get started.')).toBeInTheDocument();
  });
});

describe('Portfolio page and switcher', () => {
  it("shows the chosen portfolio's name, and switching changes the address and the name", async () => {
    stored = [
      { id: 1, name: 'Energy Book', createdAt: '' },
      { id: 2, name: 'Tech Book', createdAt: '' },
    ];
    renderAt('/portfolios/2');

    expect(await screen.findByRole('heading', { name: 'Tech Book' })).toBeInTheDocument();

    await userEvent.selectOptions(await screen.findByLabelText('Open Portfolio'), 'Energy Book');

    expect(await screen.findByRole('heading', { name: 'Energy Book' })).toBeInTheDocument();
    expect(screen.getByLabelText('Open Portfolio')).toHaveValue('1');
  });

  it("says so when the portfolio is not one of the user's", async () => {
    stored = [{ id: 1, name: 'Tech Book', createdAt: '' }];
    renderAt('/portfolios/99');

    expect(await screen.findByRole('heading', { name: 'Portfolio Not Found' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Open Portfolio')).not.toBeInTheDocument();
  });
});
