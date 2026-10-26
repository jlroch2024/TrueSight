// Client-side checks for authentication forms. The server remains authoritative.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function checkEmail(email: string): string | null {
  if (!EMAIL_PATTERN.test(email.trim())) return 'Enter a valid email address.';
  return null;
}

export function checkLoginPassword(password: string): string | null {
  return password.trim() ? null : 'Enter your password.';
}

export function checkSignUpPassword(password: string): string | null {
  if (password.length < 8) return 'Password must be at least 8 characters.';
  if (!password.trim()) return 'Enter a password.';
  if (password.length > 72) return 'Password must be 72 characters or fewer.';
  return null;
}

export function checkPasswordsMatch(password: string, confirm: string): string | null {
  return password === confirm ? null : 'Passwords do not match.';
}

/** The longest display name the backend accepts. */
export const MAX_DISPLAY_NAME = 100;

export function checkDisplayName(name: string): string | null {
  if (!name.trim()) return 'Enter a display name.';
  if (name.trim().length > MAX_DISPLAY_NAME) return `Display name must be ${MAX_DISPLAY_NAME} characters or fewer.`;
  return null;
}

export function checkCurrentPassword(password: string): string | null {
  return password.trim() ? null : 'Enter your current password.';
}
