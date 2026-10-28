// The brand, and the frame of a screen that is not a portfolio (opening one, creating the first, an unknown address):
// the brand floating top left, the account top right, and one glass card in the middle of the night-blue sheet.
import type { ReactNode } from 'react';
import { AccountMenu } from './AccountMenu';

export function Brand({ name = true }: { name?: boolean }) {
  return (
    <div className="brand">
      <span className="brand-mark"><img src="/eye-logo.png" alt="" /></span>
      {name && 'TrueSight'}
    </div>
  );
}

export function PageFrame({ children }: { children: ReactNode }) {
  return (
    <div className="page-frame">
      <header className="island left glass ticks">
        <Brand />
      </header>
      <div className="island right glass ticks">
        <AccountMenu />
      </div>
      <main className="page-card glass ticks">{children}</main>
    </div>
  );
}
