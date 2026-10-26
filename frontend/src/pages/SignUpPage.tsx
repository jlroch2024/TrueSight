// The Sign Up page, at /signup. A new account is logged in straight away, and goes to "/", which asks for a first portfolio.
// Client checks give early feedback; the backend remains authoritative.
//
// The same card as Log In, told apart by its accent: a lime edge and a lime Create Account button (Log In is teal), and the
// heading "Create Your Account".
import { type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router';
import { ApiError, api, tokenStore } from '../api/client';
import { checkEmail, checkPasswordsMatch, checkSignUpPassword } from '../authChecks';
import { FormError, FormField } from '../components/FormField';
import { MOTTO } from '../brand';
import { paths } from '../paths';

type AuthResponse = { token: string; email: string };

export function SignUpPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [emailChecked, setEmailChecked] = useState(false);
  const [passwordChecked, setPasswordChecked] = useState(false);
  const [confirmChecked, setConfirmChecked] = useState(false);
  const [registeredEmailError, setRegisteredEmailError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const emailError = registeredEmailError ?? (emailChecked ? checkEmail(email) : null);
  const passwordError = passwordChecked ? checkSignUpPassword(password) : null;
  const confirmError = confirmChecked ? checkPasswordsMatch(password, confirm) : null;

  async function signUp(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setEmailChecked(true);
    setPasswordChecked(true);
    setConfirmChecked(true);
    if (registeredEmailError || checkEmail(email) || checkSignUpPassword(password)) return;
    if (checkPasswordsMatch(password, confirm)) return;

    setSending(true);
    try {
      // Only the password is sent: Confirm Password is checked here, in the browser.
      const answer = await api<AuthResponse>('/auth/signup', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });
      tokenStore.set(answer.token);
      navigate(paths.home, { replace: true });
    } catch (e) {
      const message = e instanceof ApiError ? e.message : 'Something went wrong.';
      if (message === 'This email is already registered.') setRegisteredEmailError(message);
      else setError(message);
    } finally {
      setSending(false);
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card sign-up" noValidate onSubmit={signUp}>
        <div className="brand">
          <img src="/eye-logo.png" alt="" className="brand-logo" />
          <span>TrueSight</span>
        </div>
        <p className="auth-motto">{MOTTO}</p>
        <h1>Create Your Account</h1>
        <FormField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          error={emailError}
          onBlur={() => setEmailChecked(true)}
          onChange={(event) => {
            setEmail(event.target.value);
            setRegisteredEmailError(null);
          }}
        />
        <FormField
          id="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          value={password}
          error={passwordError}
          onBlur={() => setPasswordChecked(true)}
          onChange={(event) => setPassword(event.target.value)}
        />
        <FormField
          id="confirm-password"
          label="Confirm Password"
          type="password"
          autoComplete="new-password"
          value={confirm}
          error={confirmError}
          onBlur={() => setConfirmChecked(true)}
          onChange={(event) => setConfirm(event.target.value)}
        />
        {error && <FormError message={error} />}
        <button type="submit" disabled={sending}>
          {sending ? 'Creating Account…' : 'Create Account'}
        </button>
        <p className="auth-switch-line">
          Already Have an Account?{' '}
          <button type="button" className="auth-switch" onClick={() => navigate(paths.logIn)}>Log In</button>
        </p>
      </form>
    </main>
  );
}
