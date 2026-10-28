// Log In and Sign Up, at /login and /signup: one glass card over a slowly drifting network, the graph's ground before
// there is a portfolio to show (the network is decoration, not data). Switching between the two changes the card in
// place, the same way in either direction: its accent glides from teal (Log In) to lime (Sign Up), its title and button
// change, and Confirm Password opens or closes. The address says which it is.
//
// Each field is checked once it is left, and every field on submit; the backend remains authoritative. On success the
// token is kept in the browser and the user goes to "/", which opens their first portfolio (or asks a new user for one).
import { AnimatePresence, motion } from 'motion/react';
import { type FormEvent, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ApiError, api, tokenStore } from '../api/client';
import { checkEmail, checkLoginPassword, checkPasswordsMatch, checkSignUpPassword } from '../authChecks';
import { MOTTO } from '../brand';
import { FormError, FormField } from '../components/FormField';
import { prefersStill } from '../components/motion';
import { paths } from '../paths';

type AuthResponse = { token: string; email: string };
type Mode = 'login' | 'signup';

// One spring for every part that changes between Log In and Sign Up, so the card changes as one.
const SWITCH = { type: 'spring', stiffness: 380, damping: 34 } as const;
// A size that changes: one smooth step, the panels' own, so nothing overshoots and settles with a jump.
const GROW = { duration: 0.42, ease: [0.65, 0, 0.35, 1] } as const;

const ALREADY_REGISTERED = 'This email is already registered.';

export function AuthPage() {
  const navigate = useNavigate();
  const mode: Mode = useLocation().pathname === paths.signUp ? 'signup' : 'login';
  const signUp = mode === 'signup';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [emailChecked, setEmailChecked] = useState(false);
  const [passwordChecked, setPasswordChecked] = useState(false);
  const [confirmChecked, setConfirmChecked] = useState(false);
  const [registeredEmailError, setRegisteredEmailError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  // Confirm Password clips only while it opens or closes, so its focus ring is never cut off.
  const [moving, setMoving] = useState(false);

  const checkPassword = signUp ? checkSignUpPassword : checkLoginPassword;
  const emailError = registeredEmailError ?? (emailChecked ? checkEmail(email) : null);
  const passwordError = passwordChecked ? checkPassword(password) : null;
  const confirmError = signUp && confirmChecked ? checkPasswordsMatch(password, confirm) : null;

  // Switching starts the other form afresh: nothing is checked yet, and no answer from the last one is shown.
  useEffect(() => {
    setEmailChecked(false);
    setPasswordChecked(false);
    setConfirmChecked(false);
    setRegisteredEmailError(null);
    setError(null);
  }, [mode]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setEmailChecked(true);
    setPasswordChecked(true);
    setConfirmChecked(true);
    if (registeredEmailError || checkEmail(email) || checkPassword(password)) return;
    if (signUp && checkPasswordsMatch(password, confirm)) return;

    setSending(true);
    try {
      // Only the password is sent: Confirm Password is checked here, in the browser.
      const answer = await api<AuthResponse>(signUp ? '/auth/signup' : '/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });
      tokenStore.set(answer.token);
      navigate(paths.home, { replace: true });
    } catch (e) {
      const message = e instanceof ApiError ? e.message : 'Something went wrong.';
      if (signUp && message === ALREADY_REGISTERED) setRegisteredEmailError(message);
      else setError(message);
    } finally {
      setSending(false);
    }
  }

  return (
    <main className="auth">
      <Constellation accent={signUp ? [196, 243, 106] : [45, 212, 191]} />
      <motion.form
        className={`auth-card glass ticks${signUp ? ' sign-up' : ''}`}
        initial={{ opacity: 0, y: 16, scale: 0.985 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ type: 'spring', stiffness: 300, damping: 32 }}
        noValidate
        onSubmit={submit}
      >
        <span className="auth-edge" aria-hidden="true" />
        <div className="auth-brand">
          <span className="brand-mark"><img src="/eye-logo.png" alt="" /></span>
          TrueSight
        </div>
        <p className="auth-motto">{MOTTO}</p>
        <div className="auth-heading">
          <AnimatePresence mode="popLayout" initial={false}>
            <motion.h1
              key={mode}
              initial={{ opacity: 0, y: 14, filter: 'blur(6px)' }}
              animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
              exit={{ opacity: 0, y: -14, filter: 'blur(6px)' }}
              transition={SWITCH}
            >
              {signUp ? 'Create Your Account' : 'Log In'}
            </motion.h1>
          </AnimatePresence>
        </div>

        <div className="auth-fields">
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
            autoComplete={signUp ? 'new-password' : 'current-password'}
            value={password}
            error={passwordError}
            onBlur={() => setPasswordChecked(true)}
            onChange={(event) => setPassword(event.target.value)}
          />
          <AnimatePresence initial={false}>
            {signUp && (
              // Its height and the space above it open and close together, on one smooth step with no spring to
              // overshoot, so the card grows and shrinks without a jump at the end.
              <motion.div
                key="confirm"
                initial={{ height: 0, marginTop: 0, opacity: 0 }}
                animate={{ height: 'auto', marginTop: 16, opacity: 1 }}
                exit={{ height: 0, marginTop: 0, opacity: 0 }}
                transition={GROW}
                onAnimationStart={() => setMoving(true)}
                onAnimationComplete={() => setMoving(false)}
                style={{ overflow: moving ? 'hidden' : 'visible' }}
              >
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
              </motion.div>
            )}
          </AnimatePresence>
        </div>
        {error && <FormError message={error} />}

        <button type="submit" className={`btn primary auth-submit${signUp ? ' lime' : ''}`} disabled={sending}>
          <AnimatePresence mode="popLayout" initial={false}>
            <motion.span
              key={`${mode}-${sending}`}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={SWITCH}
            >
              {sending ? (signUp ? 'Creating Account…' : 'Logging In…') : signUp ? 'Create Account' : 'Log In'}
            </motion.span>
          </AnimatePresence>
        </button>
        <p className="auth-switch">
          {signUp ? 'Already Have an Account? ' : 'New to TrueSight? '}
          <button type="button" className="text-btn" onClick={() => navigate(signUp ? paths.logIn : paths.signUp)}>
            {signUp ? 'Log In' : 'Sign Up'}
          </button>
        </p>
      </motion.form>
    </main>
  );
}

/** A slow, drifting network of points: the graph's mood, with nothing in it to read. */
function Constellation({ accent }: { accent: [number, number, number] }) {
  const ref = useRef<HTMLCanvasElement | null>(null);
  const accentRef = useRef(accent);
  accentRef.current = accent;

  useEffect(() => {
    const canvas = ref.current;
    const context = canvas?.getContext('2d');
    if (!canvas || !context) return;
    const still = prefersStill();
    let width = 0;
    let height = 0;
    let dpr = 1;
    const points = Array.from({ length: 64 }, (_, i) => ({
      x: Math.random(),
      y: Math.random(),
      vx: (Math.random() - 0.5) * 0.00012,
      vy: (Math.random() - 0.5) * 0.00012,
      r: i % 9 === 0 ? 2.4 : 1.2,
    }));
    const pointer = { x: 0.5, y: 0.5 };
    const size = () => {
      dpr = window.devicePixelRatio || 1;
      width = canvas.clientWidth;
      height = canvas.clientHeight;
      canvas.width = width * dpr;
      canvas.height = height * dpr;
    };
    size();
    const move = (event: PointerEvent) => {
      pointer.x = event.clientX / Math.max(1, width);
      pointer.y = event.clientY / Math.max(1, height);
    };
    window.addEventListener('resize', size);
    window.addEventListener('pointermove', move);
    let frame = 0;
    let shown = [...accentRef.current];
    const draw = (now: number) => {
      const target = accentRef.current;
      shown = shown.map((value, index) => value + (target[index]! - value) * 0.04);
      const [sr, sg, sb] = shown.map(Math.round);
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      context.clearRect(0, 0, width, height);
      // The aurora, drifting.
      const aurora = context.createRadialGradient(
        width * (0.3 + Math.sin(now / 9000) * 0.08),
        height * 0.2,
        0,
        width * 0.4,
        height * 0.3,
        Math.max(width, height) * 0.7,
      );
      aurora.addColorStop(0, `rgba(${sr}, ${sg}, ${sb}, 0.10)`);
      aurora.addColorStop(1, 'rgba(0, 0, 0, 0)');
      context.fillStyle = aurora;
      context.fillRect(0, 0, width, height);
      // The grid, faint.
      context.strokeStyle = 'rgba(148, 163, 184, 0.05)';
      context.lineWidth = 1;
      for (let x = 0; x < width; x += 48) {
        context.beginPath();
        context.moveTo(x + 0.5, 0);
        context.lineTo(x + 0.5, height);
        context.stroke();
      }
      for (let y = 0; y < height; y += 48) {
        context.beginPath();
        context.moveTo(0, y + 0.5);
        context.lineTo(width, y + 0.5);
        context.stroke();
      }
      const shiftX = (pointer.x - 0.5) * 18;
      const shiftY = (pointer.y - 0.5) * 18;
      if (!still) {
        for (const point of points) {
          point.x = (point.x + point.vx + 1) % 1;
          point.y = (point.y + point.vy + 1) % 1;
        }
      }
      for (let i = 0; i < points.length; i++) {
        for (let j = i + 1; j < points.length; j++) {
          const a = points[i]!;
          const b = points[j]!;
          const distance = Math.hypot((a.x - b.x) * width, (a.y - b.y) * height);
          if (distance > 170) continue;
          context.strokeStyle = `rgba(${sr}, ${sg}, ${sb}, ${0.3 * (1 - distance / 170)})`;
          context.setLineDash([3, 5]);
          context.lineDashOffset = still ? 0 : -now / 60;
          context.beginPath();
          context.moveTo(a.x * width + shiftX, a.y * height + shiftY);
          context.lineTo(b.x * width + shiftX, b.y * height + shiftY);
          context.stroke();
        }
      }
      context.setLineDash([]);
      for (const point of points) {
        context.fillStyle = point.r > 2 ? `rgba(${sr}, ${sg}, ${sb}, 0.75)` : 'rgba(203, 213, 225, 0.35)';
        context.fillRect(point.x * width + shiftX - point.r, point.y * height + shiftY - point.r, point.r * 2, point.r * 2);
      }
      // A vignette, so the card is where the eye goes.
      const vignette = context.createRadialGradient(width / 2, height / 2, Math.min(width, height) * 0.2, width / 2, height / 2, Math.max(width, height) * 0.75);
      vignette.addColorStop(0, 'rgba(5, 7, 13, 0)');
      vignette.addColorStop(1, 'rgba(5, 7, 13, 0.85)');
      context.fillStyle = vignette;
      context.fillRect(0, 0, width, height);
      frame = window.requestAnimationFrame(draw);
    };
    frame = window.requestAnimationFrame(draw);
    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener('resize', size);
      window.removeEventListener('pointermove', move);
    };
  }, []);

  return <canvas ref={ref} aria-hidden="true" />;
}
