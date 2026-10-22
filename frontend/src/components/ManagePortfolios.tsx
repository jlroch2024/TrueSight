// The Manage Portfolios window, opened from the header. It lists every portfolio, marks the open one Active, and gives
// each Open, Rename and Delete. A box at the bottom creates one. Everything is a button: the window has no links.
//
// Creating a portfolio opens it. Deleting asks first, then the open portfolio opens another, or /portfolios when none
// is left.
import { type FormEvent, type KeyboardEvent, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { type Portfolio, portfolioApi, portfolioChanges } from '../api/portfolios';
import { paths } from '../paths';
import { useConfirm } from './ConfirmDialog';

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

/**
 * The same kind of page for another portfolio: on /portfolios/7/supply-chain, portfolio 9 gives
 * /portfolios/9/supply-chain. Anywhere else, it gives the portfolio's own page.
 */
export function samePageFor(pathname: string, openId: string | undefined, nextId: string | number): string {
  const prefix = openId ? paths.portfolio(openId) : null;
  if (prefix && (pathname === prefix || pathname.startsWith(`${prefix}/`))) {
    return paths.portfolio(nextId) + pathname.slice(prefix.length);
  }
  return paths.portfolio(nextId);
}

type Props = {
  /** The open portfolio's id, from the address, or none on a page such as Review. */
  openId?: string;
  onClose: () => void;
};

const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function ManagePortfolios({ openId, onClose }: Props) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [portfolios, setPortfolios] = useState<Portfolio[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [newName, setNewName] = useState('');
  const [renamingId, setRenamingId] = useState<number | null>(null);
  const [renameTo, setRenameTo] = useState('');
  const [busy, setBusy] = useState(false);
  // The portfolio whose Rename button gets the focus back once its rename is saved or cancelled.
  const [renameDone, setRenameDone] = useState<number | null>(null);
  const windowRef = useRef<HTMLDivElement>(null);
  const renameButtons = useRef(new Map<number, HTMLButtonElement>());
  const { confirm, dialog } = useConfirm();

  function load() {
    return portfolioApi
      .list()
      .then((list) => {
        setPortfolios(list);
        return list;
      })
      .catch((e: unknown) => {
        setLoadError(messageOf(e));
        return null;
      });
  }

  useEffect(() => {
    load();
    windowRef.current?.focus();
  }, []);

  // Escape closes the window, as Close does. While a confirmation is open, Escape closes only the confirmation.
  const confirming = dialog !== null;
  useEffect(() => {
    const closeOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape' && !confirming) onClose();
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [onClose, confirming]);

  useEffect(() => {
    if (renameDone === null || renamingId !== null) return;
    renameButtons.current.get(renameDone)?.focus();
    setRenameDone(null);
  }, [renameDone, renamingId, portfolios]);

  function stopRenaming(id: number) {
    setRenamingId(null);
    setRenameDone(id);
  }

  // Tab and Shift+Tab go round the window's own controls, never to the page behind it.
  function keepFocusInside(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Tab' || !windowRef.current) return;
    const focusable = Array.from(windowRef.current.querySelectorAll<HTMLElement>(FOCUSABLE));
    if (focusable.length === 0) return;
    const first = focusable[0]!;
    const last = focusable[focusable.length - 1]!;
    const current = document.activeElement;
    if (event.shiftKey && (current === first || current === windowRef.current)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && current === last) {
      event.preventDefault();
      first.focus();
    }
  }

  // Runs one change, then reloads the list. The buttons are disabled meanwhile, so nothing is sent twice.
  async function run<T>(action: () => Promise<T>): Promise<{ ok: true; value: T } | { ok: false }> {
    setBusy(true);
    setActionError(null);
    try {
      const value = await action();
      portfolioChanges.announce();
      return { ok: true, value };
    } catch (e) {
      setActionError(messageOf(e));
      return { ok: false };
    } finally {
      setBusy(false);
    }
  }

  function open(id: number) {
    onClose();
    if (String(id) !== openId) navigate(samePageFor(pathname, openId, id));
  }

  async function create(event: FormEvent) {
    event.preventDefault();
    const result = await run(() => portfolioApi.create(newName));
    if (result.ok) {
      onClose();
      navigate(paths.portfolio(result.value.id));
    }
  }

  async function rename(event: FormEvent, id: number) {
    event.preventDefault();
    if ((await run(() => portfolioApi.rename(id, renameTo))).ok) {
      await load();
      stopRenaming(id);
    }
  }

  async function remove(portfolio: Portfolio) {
    const confirmed = await confirm({
      title: 'Delete Portfolio',
      message: `Delete "${portfolio.name}" and all its holdings? This cannot be undone.`,
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!confirmed) return;
    if (!(await run(() => portfolioApi.remove(portfolio.id))).ok) return;
    const left = await load();
    if (String(portfolio.id) === openId) {
      // The open portfolio has gone, so open another.
      onClose();
      const next = left?.[0];
      navigate(next ? samePageFor(pathname, openId, next.id) : paths.portfolios, { replace: true });
    }
  }

  return (
    <div className="window-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={windowRef}
        className="window"
        role="dialog"
        aria-modal="true"
        aria-labelledby="manage-portfolios-title"
        tabIndex={-1}
        onKeyDown={keepFocusInside}
      >
        <div className="window-heading">
          <h2 id="manage-portfolios-title">Manage Portfolios</h2>
          <button type="button" className="secondary" onClick={onClose}>
            Close
          </button>
        </div>

        {actionError && <p className="error">{actionError}</p>}

        {loadError ? (
          <p className="error">{loadError}</p>
        ) : !portfolios ? (
          <p className="muted">Loading…</p>
        ) : (
          <ul className="portfolio-list">
            {portfolios.map((portfolio) => {
              const active = String(portfolio.id) === openId;
              return (
                <li key={portfolio.id}>
                  {renamingId === portfolio.id ? (
                    <form className="inline-form" onSubmit={(e) => rename(e, portfolio.id)}>
                      <label htmlFor={`rename-${portfolio.id}`}>New Name</label>
                      <input
                        id={`rename-${portfolio.id}`}
                        value={renameTo}
                        onChange={(e) => setRenameTo(e.target.value)}
                        onFocus={(e) => e.target.select()}
                        maxLength={100}
                        autoFocus
                      />
                      <button type="submit" disabled={busy || !renameTo.trim()}>
                        Save
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => stopRenaming(portfolio.id)}
                        disabled={busy}
                      >
                        Cancel
                      </button>
                    </form>
                  ) : (
                    <>
                      <span className="portfolio-name">
                        {portfolio.name}
                        {active && <span className="active-tag">Active</span>}
                      </span>
                      <span className="actions">
                        <button
                          type="button"
                          className="secondary"
                          disabled={busy || active}
                          aria-label={`Open ${portfolio.name}`}
                          onClick={() => open(portfolio.id)}
                        >
                          Open
                        </button>
                        <button
                          ref={(button) => {
                            if (button) renameButtons.current.set(portfolio.id, button);
                            else renameButtons.current.delete(portfolio.id);
                          }}
                          type="button"
                          className="secondary"
                          disabled={busy}
                          aria-label={`Rename ${portfolio.name}`}
                          onClick={() => {
                            setRenamingId(portfolio.id);
                            setRenameTo(portfolio.name);
                          }}
                        >
                          Rename
                        </button>
                        <button
                          type="button"
                          className="danger"
                          disabled={busy}
                          aria-label={`Delete ${portfolio.name}`}
                          onClick={() => remove(portfolio)}
                        >
                          Delete
                        </button>
                      </span>
                    </>
                  )}
                </li>
              );
            })}
          </ul>
        )}

        <form className="inline-form window-create" onSubmit={create}>
          <label htmlFor="new-portfolio-name">New Portfolio Name</label>
          <input id="new-portfolio-name" value={newName} onChange={(e) => setNewName(e.target.value)} maxLength={100} />
          <button type="submit" disabled={busy || !newName.trim()}>
            Create Portfolio
          </button>
        </form>
      </div>
      {dialog}
    </div>
  );
}
