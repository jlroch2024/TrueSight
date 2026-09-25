// The Log In page, at /login. On success the token is kept in the browser, and the user goes to Home.
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError, api, tokenStore } from '../api/client';
import { paths } from '../paths';

type AuthResponse = { token: string; email: string };

export function LogInPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function logIn(event: FormEvent) {
    event.preventDefault();
    setSending(true);
    setError(null);
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
      <form className="auth-card" onSubmit={logIn}>
        <div className="brand">
          <img src="/eye-logo.png" alt="" className="brand-logo" />
          <span>TrueSight</span>
        </div>
        <h1>Log In</h1>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={sending}>
          {sending ? 'Logging In…' : 'Log In'}
        </button>
        <p className="muted">
          No account yet? <Link to={paths.signUp}>Sign Up</Link>
        </p>
      </form>
    </main>
  );
}
