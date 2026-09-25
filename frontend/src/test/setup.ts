// Runs before every frontend test. Adds checks such as expect(element).toBeInTheDocument(), and clears the browser
// storage and fake fetch between tests so one test cannot affect another.
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.unstubAllGlobals();
});
