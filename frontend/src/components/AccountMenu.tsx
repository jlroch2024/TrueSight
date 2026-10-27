// The account's square at the right of the top bar: who is logged in (their display name, or their email) and their
// role, then Profile and Log Out. If the account could not load, it says so and offers Retry.
import { LogOut, RotateCcw, UserRound } from 'lucide-react';
import { useCallback, useRef, useState } from 'react';
import { nameShown } from '../api/auth';
import { initialsFor, useAccount } from './account';
import { Popover, useDismiss } from './Popover';

export function AccountMenu() {
  const { me, failed, retry, openProfile, logOut } = useAccount();
  const [open, setOpen] = useState(false);
  const close = useCallback(() => setOpen(false), []);
  const ref = useDismiss<HTMLDivElement>(open, close);
  const button = useRef<HTMLButtonElement>(null);
  const analyst = me?.role === 'ANALYST';

  return (
    <div ref={ref} className="account">
      <button
        ref={button}
        type="button"
        className="avatar"
        aria-label={me ? `Account (${nameShown(me)})` : 'Account'}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        {me ? initialsFor(me) : failed ? '!' : ''}
      </button>
      <Popover anchor={ref} open={open} align="right" label="Account">
        {me ? (
          <div className="pop-account">
            <span className="avatar static">{initialsFor(me)}</span>
            <span>
              <span className="pop-account-name">{nameShown(me)}</span>
              <span className={`chip ${analyst ? 'lime' : 'teal'}`}>{analyst ? 'Analyst' : 'Portfolio Manager'}</span>
            </span>
          </div>
        ) : (
          failed && (
            <button
              type="button"
              role="menuitem"
              className="pop-item"
              onClick={() => {
                retry();
                close();
              }}
            >
              <span className="pop-icon"><RotateCcw size={16} /></span>
              <span className="grow">
                Retry
                <span className="pop-sub">Account Could Not Load</span>
              </span>
            </button>
          )
        )}
        <div className="pop-rule" />
        {me && (
          <button
            type="button"
            role="menuitem"
            className="pop-item"
            onClick={() => {
              close();
              openProfile(button.current);
            }}
          >
            <span className="pop-icon"><UserRound size={16} /></span>
            <span className="grow">Profile</span>
          </button>
        )}
        <button type="button" role="menuitem" className="pop-item" onClick={logOut}>
          <span className="pop-icon"><LogOut size={16} /></span>
          <span className="grow">Log Out</span>
        </button>
      </Popover>
    </div>
  );
}
