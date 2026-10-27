// How things move in TrueSight, so every screen moves alike.

/** Spring for things appearing (cards, windows, toasts): quick, settling without a bounce. */
export const PANEL_SPRING = { type: 'spring', stiffness: 300, damping: 34, mass: 0.9 } as const;

/**
 * How the workspace's panels move (the drawer folding, the Inspector sliding in): the graph's view and controls follow
 * them on the same smooth step and in the same time (the graph's INSET_GLIDE_MS), so everything moves as one.
 */
export const PANEL_MOVE = { duration: 0.48, ease: [0.65, 0, 0.35, 1] } as const;

/** Whether the visitor's computer asks for less motion (or cannot say, as in tests): numbers then jump, links stay still. */
export function prefersStill(): boolean {
  return typeof window.matchMedia !== 'function' || window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

export const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`;
