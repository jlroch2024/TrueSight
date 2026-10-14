// The Log In page, at /login. On success the token is kept in the browser, and the user goes to Home.
import { type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router';
import { ApiError, api, tokenStore } from '../api/client';
import { FormField } from '../components/FormField';
import { checkEmail, checkLoginPassword } from '../authChecks';
import { paths } from '../paths';

type AuthResponse = { token: string; email: string };

export function LogInPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [emailChecked, setEmailChecked] = useState(false);
  const [passwordChecked, setPasswordChecked] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const emailError = emailChecked ? checkEmail(email) : null;
  const passwordError = passwordChecked ? checkLoginPassword(password) : null;

  async function logIn(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setEmailChecked(true);
    setPasswordChecked(true);
    if (checkEmail(email) || checkLoginPassword(password)) return;

    setSending(true);
    try {
      const answer = await api<AuthResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });
      tokenStore.set(answer.token);
      navigate(paths.home, { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong.');
    } finally {
      setSending(false);
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card" noValidate onSubmit={logIn}>
        <div className="brand">
          <img src="/eye-logo.png" alt="" className="brand-logo" />
          <span>TrueSight</span>
        </div>
        <h1>Log In</h1>
        <FormField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          error={emailError}
          onBlur={() => setEmailChecked(true)}
          onChange={(event) => setEmail(event.target.value)}
        />
        <FormField
          id="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          error={passwordError}
          onBlur={() => setPasswordChecked(true)}
          onChange={(event) => setPassword(event.target.value)}
        />
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={sending}>
          {sending ? 'Logging In…' : 'Log In'}
        </button>
        <button type="button" className="auth-switch" onClick={() => navigate(paths.signUp)}>Sign Up</button>
      </form>
    </main>
  );
}
