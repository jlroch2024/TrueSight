// The Profile window, opened from the account menu's Profile. It shows the email, which cannot
// be changed, and lets the user set the display name shown instead of it, and change their password.
//
// Changing the password asks for the current one, then the new one twice, under the same rules as Sign Up (see
// authChecks.ts); a new password that does not match its confirmation is caught here, before anything is sent. The user
// stays logged in. The backend remains authoritative: a wrong current password comes back as its own message.
import { motion } from 'motion/react';
import { type FormEvent, type KeyboardEvent, useEffect, useRef, useState } from 'react';
import { type Me, authApi } from '../api/auth';
import { ApiError } from '../api/client';
import {
  MAX_DISPLAY_NAME,
  checkCurrentPassword,
  checkDisplayName,
  checkPasswordsMatch,
  checkSignUpPassword,
} from '../authChecks';
import { FormError, FormField } from './FormField';

type Props = {
  me: Me;
  // Called with the account as it now is, after the display name is saved.
  onChange: (me: Me) => void;
  onClose: () => void;
};

const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])';

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong.';
}

export function ProfileWindow({ me, onChange, onClose }: Props) {
  const windowRef = useRef<HTMLDivElement>(null);

  const [name, setName] = useState(me.displayName ?? '');
  const [nameChecked, setNameChecked] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);
  const [nameSaved, setNameSaved] = useState(false);
  const [savingName, setSavingName] = useState(false);

  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [currentChecked, setCurrentChecked] = useState(false);
  const [nextChecked, setNextChecked] = useState(false);
  const [confirmChecked, setConfirmChecked] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordChanged, setPasswordChanged] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);

  useEffect(() => {
    windowRef.current?.focus();
  }, []);

  // Escape closes the window, as Close does.
  useEffect(() => {
    const closeOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [onClose]);

  // Tab and Shift+Tab go round the window's own controls, never to the page behind it.
  function keepFocusInside(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Tab' || !windowRef.current) return;
    const focusable = Array.from(windowRef.current.querySelectorAll<HTMLElement>(FOCUSABLE));
    if (focusable.length === 0) return;
    const first = focusable[0]!;
    const last = focusable[focusable.length - 1]!;
    const active = document.activeElement;
    if (event.shiftKey && (active === first || active === windowRef.current)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  const nameFieldError = nameError ?? (nameChecked ? checkDisplayName(name) : null);
  const currentError = currentChecked ? checkCurrentPassword(current) : null;
  const nextError = nextChecked ? checkSignUpPassword(next) : null;
  const confirmError = confirmChecked ? checkPasswordsMatch(next, confirm) : null;

  async function saveName(event: FormEvent) {
    event.preventDefault();
    setNameChecked(true);
    setNameSaved(false);
    if (checkDisplayName(name)) return;
    setSavingName(true);
    setNameError(null);
    try {
      const saved = await authApi.setDisplayName(name.trim());
      setName(saved.displayName ?? '');
      setNameSaved(true);
      onChange(saved);
    } catch (e) {
      setNameError(messageOf(e));
    } finally {
      setSavingName(false);
    }
  }

  async function savePassword(event: FormEvent) {
    event.preventDefault();
    setCurrentChecked(true);
    setNextChecked(true);
    setConfirmChecked(true);
    setPasswordChanged(false);
    // The new password and its confirmation are compared here: only the new password is ever sent.
    if (checkCurrentPassword(current) || checkSignUpPassword(next) || checkPasswordsMatch(next, confirm)) return;
    setChangingPassword(true);
    setPasswordError(null);
    try {
      await authApi.changePassword(current, next);
      setCurrent('');
      setNext('');
      setConfirm('');
      setCurrentChecked(false);
      setNextChecked(false);
      setConfirmChecked(false);
      setPasswordChanged(true);
    } catch (e) {
      setPasswordError(messageOf(e));
    } finally {
      setChangingPassword(false);
    }
  }

  return (
    <motion.div
      className="window-backdrop"
      initial={false}
      exit={{ opacity: 0, transition: { duration: 0.22 } }}
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <motion.div
        ref={windowRef}
        initial={false}
        exit={{ opacity: 0, y: 10, scale: 0.97, transition: { duration: 0.22, ease: [0.4, 0, 1, 1] } }}
        className="window glass ticks profile-window"
        role="dialog"
        aria-modal="true"
        aria-labelledby="profile-title"
        tabIndex={-1}
        onKeyDown={keepFocusInside}
      >
        <div className="window-heading">
          <h2 id="profile-title">Profile</h2>
          <button type="button" className="btn small" onClick={onClose}>
            Close
          </button>
        </div>

        <dl className="facts profile-email">
          <div>
            <dt>Email</dt>
            <dd>{me.email}</dd>
          </div>
        </dl>
        <p className="muted small">Your email cannot be changed.</p>

        <form className="profile-section" onSubmit={saveName} noValidate aria-label="Display name settings">
          <h3>Display Name</h3>
          <p className="muted small">
            Shown in your account menu, and on the decisions you make, instead of your email. 1 to {MAX_DISPLAY_NAME} characters.
          </p>
          <FormField
            id="display-name"
            label="Display Name"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setNameSaved(false);
              setNameError(null);
            }}
            onBlur={() => setNameChecked(true)}
            error={nameFieldError}
            autoComplete="nickname"
            maxLength={MAX_DISPLAY_NAME}
          />
          <div className="actions">
            <button type="submit" className="btn primary" disabled={savingName}>
              {savingName ? 'Saving…' : 'Save Display Name'}
            </button>
            {nameSaved && (
              <span role="status" className="muted small">
                Display name saved.
              </span>
            )}
          </div>
        </form>

        <form className="profile-section" onSubmit={savePassword} noValidate aria-label="Password settings">
          <h3>Change Password</h3>
          <FormField
            id="current-password"
            label="Current Password"
            type="password"
            autoComplete="current-password"
            value={current}
            onChange={(e) => {
              setCurrent(e.target.value);
              setPasswordError(null);
              setPasswordChanged(false);
            }}
            onBlur={() => setCurrentChecked(true)}
            error={currentError}
          />
          <FormField
            id="new-password"
            label="New Password"
            type="password"
            autoComplete="new-password"
            value={next}
            onChange={(e) => {
              setNext(e.target.value);
              setPasswordChanged(false);
            }}
            onBlur={() => setNextChecked(true)}
            error={nextError}
          />
          <FormField
            id="confirm-new-password"
            label="Confirm New Password"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => {
              setConfirm(e.target.value);
              setPasswordChanged(false);
            }}
            onBlur={() => setConfirmChecked(true)}
            error={confirmError}
          />
          {passwordError && <FormError message={passwordError} />}
          <div className="actions">
            <button type="submit" className="btn primary" disabled={changingPassword}>
              {changingPassword ? 'Changing…' : 'Change Password'}
            </button>
            {passwordChanged && (
              <span role="status" className="muted small">
                Password changed. You are still logged in.
              </span>
            )}
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
}
