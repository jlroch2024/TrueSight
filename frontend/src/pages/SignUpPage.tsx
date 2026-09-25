// The Sign Up page, at /signup. A new account is logged in straight away, and goes to Home.
// The backend checks the email and the 8-character minimum, and its message is shown as it is.
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError, api, tokenStore } from '../api/client';
import { paths } from '../paths';

type AuthResponse = { token: string; email: string };

export function SignUpPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function signUp(event: FormEvent) {
    event.preventDefault();
    setSending(true);
    setError(null);
    try {
      const answer = await api<AuthResponse>('/auth/signup', {
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
      <form className="auth-card" onSubmit={signUp}>
        <div className="brand">
          <img src="/eye-logo.png" alt="" className="brand-logo" />
          <span>TrueSight</span>
        </div>
        <h1>Sign Up</h1>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        <p className="muted small">At least 8 characters.</p>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={sending}>
          {sending ? 'Signing Up…' : 'Sign Up'}
        </button>
        <p className="muted">
          Already have an account? <Link to={paths.logIn}>Log In</Link>
        </p>
      </form>
    </main>
  );
}
