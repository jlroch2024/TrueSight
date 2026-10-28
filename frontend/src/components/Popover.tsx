// A menu that floats over everything, below the button that opened it, and the hook that closes it when a press lands
// elsewhere or Escape is pressed.
import { AnimatePresence, motion } from 'motion/react';
import { type ReactNode, type RefObject, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

/** Calls away() when a press lands outside the element (or any floating menu), or Escape is pressed. */
export function useDismiss<T extends HTMLElement>(open: boolean, away: () => void) {
  const ref = useRef<T | null>(null);
  useEffect(() => {
    if (!open) return;
    const press = (event: PointerEvent) => {
      const target = event.target as Element;
      if (ref.current?.contains(target) || target.closest?.('.pop')) return;
      away();
    };
    const key = (event: KeyboardEvent) => {
      if (event.key === 'Escape') away();
    };
    window.addEventListener('pointerdown', press);
    window.addEventListener('keydown', key);
    return () => {
      window.removeEventListener('pointerdown', press);
      window.removeEventListener('keydown', key);
    };
  }, [open, away]);
  return ref;
}

/**
 * The menu: drawn at the top of the page (glass inside glass cannot blur what is behind both), lined up with its
 * anchor's left or right edge, growing out of it on a spring. It opens 12px under the bar its anchor sits in (the page's
 * one gap), so its top lines up with the panels under that bar. reachBar stretches it from its anchor to the bar's far
edge, so it lines up with the panel under that bar too.
 */
export function Popover({
  anchor,
  open,
  align,
  children,
  label,
  reachBar = false,
}: {
  anchor: RefObject<HTMLElement | null>;
  open: boolean;
  align: 'left' | 'right';
  children: ReactNode;
  label: string;
  reachBar?: boolean;
}) {
  const [place, setPlace] = useState<{ top: number; left?: number; right?: number; width?: number; minWidth?: number } | null>(null);
  useLayoutEffect(() => {
    if (!open || !anchor.current) return;
    const measure = () => {
      const box = anchor.current!.getBoundingClientRect();
      const bar = anchor.current!.closest('.island')?.getBoundingClientRect() ?? box;
      const top = bar.bottom + 12;
      const size = reachBar
        ? { width: align === 'left' ? bar.right - box.left : box.right - bar.left, minWidth: 0 }
        : {};
      setPlace(
        align === 'left'
          ? { top, left: box.left, ...size }
          : { top, right: window.innerWidth - box.right, ...size },
      );
    };
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [open, anchor, align, reachBar]);
  return createPortal(
    <AnimatePresence>
      {open && place !== null && (
        <motion.div
          className={`pop glass ticks from-${align}`}
          role="menu"
          aria-label={label}
          style={{ position: 'fixed', ...place, transformOrigin: align === 'left' ? 'top left' : 'top right' }}
          initial={{ opacity: 0, y: -8, scale: 0.96, filter: 'blur(4px)' }}
          animate={{ opacity: 1, y: 0, scale: 1, filter: 'blur(0px)' }}
          exit={{ opacity: 0, y: -6, scale: 0.97, filter: 'blur(3px)', transition: { duration: 0.14 } }}
          transition={{ type: 'spring', stiffness: 520, damping: 34 }}
        >
          {children}
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
