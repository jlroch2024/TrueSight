import { describe, expect, it } from 'vitest';
import { checkEmail, checkLoginPassword, checkSignUpPassword } from './authChecks';

describe('authentication checks', () => {
  it('accepts valid email addresses and rejects values without an email shape', () => {
    expect(checkEmail('person@example.com')).toBeNull();
    expect(checkEmail('gtfsdege')).toBe('Enter a valid email address.');
  });

  it('requires a login password without imposing a strength rule', () => {
    expect(checkLoginPassword('x')).toBeNull();
    expect(checkLoginPassword('   ')).toBe('Enter your password.');
  });

  it('accepts sign-up passwords from 8 through 72 characters inclusive', () => {
    expect(checkSignUpPassword('a'.repeat(8))).toBeNull();
    expect(checkSignUpPassword('a'.repeat(72))).toBeNull();
    expect(checkSignUpPassword('a'.repeat(7))).toBe('Password should be at least 8 characters');
    expect(checkSignUpPassword('a'.repeat(73))).toBe('Password must be 72 characters or fewer.');
  });
});
