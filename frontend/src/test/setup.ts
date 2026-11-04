// Runs before every frontend test. Adds checks such as expect(element).toBeInTheDocument(), and clears the browser
// storage and fake fetch between tests so one test cannot affect another.
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { MotionGlobalConfig } from 'motion/react';
import { afterEach, vi } from 'vitest';

// Panels, cards and numbers move at once rather than over a fraction of a second, so a test sees each screen as it
// ends up, and what leaves is gone straight away.
MotionGlobalConfig.skipAnimations = true;

// A computer's window, wide enough for the workspace's full layout (jsdom's own 1024 is under its phone layout's width).
window.innerWidth = 1440;

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.unstubAllGlobals();
});
