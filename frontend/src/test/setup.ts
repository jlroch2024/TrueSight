// Runs before every frontend test. Adds checks such as expect(element).toBeInTheDocument(), and clears the browser
// storage and fake fetch between tests so one test cannot affect another.
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { MotionGlobalConfig } from 'motion/react';
import { afterEach, vi } from 'vitest';

// Panels, cards and numbers move at once rather than over a fraction of a second, so a test sees each screen as it
// ends up, and what leaves is gone straight away.
MotionGlobalConfig.skipAnimations = true;

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.unstubAllGlobals();
});
