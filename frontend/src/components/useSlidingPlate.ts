import { useLayoutEffect, useRef } from 'react';

/**
 * The plate under a toggle's chosen option, sliding from one option to the next. It is one element, placed over the
 * option marked data-chosen="true" inside the container, and moved by a CSS transition of its transform, which the
 * browser runs apart from the page's own work: so it keeps its pace even while the page draws what the choice brings
 * in (a long list, a different tab). With nothing chosen it fades out, and it fades in where the next choice is, without
 * sliding there. chosen is whatever identifies the choice, so the plate moves when it changes.
 */
export function useSlidingPlate<C extends HTMLElement, P extends HTMLElement = HTMLSpanElement>(chosen: unknown) {
  const container = useRef<C | null>(null);
  const plate = useRef<P | null>(null);
  const shown = useRef(false);

  useLayoutEffect(() => {
    const box = container.current;
    const element = plate.current;
    if (!box || !element) return;
    const place = (slide: boolean) => {
      const target = box.querySelector<HTMLElement>('[data-chosen="true"]');
      if (!target) {
        element.style.opacity = '0';
        shown.current = false;
        return;
      }
      const outer = box.getBoundingClientRect();
      const inner = target.getBoundingClientRect();
      const glide = slide && shown.current;
      if (!glide) element.style.transition = 'none';
      element.style.width = `${inner.width}px`;
      element.style.height = `${inner.height}px`;
      element.style.transform = `translate3d(${inner.left - outer.left - box.clientLeft}px, ${inner.top - outer.top - box.clientTop}px, 0)`;
      element.style.opacity = '1';
      if (!glide) {
        // Apply the jump before the transition comes back, so only later moves slide.
        void element.offsetWidth;
        element.style.transition = '';
      }
      shown.current = true;
    };
    place(true);
    if (typeof ResizeObserver === 'undefined') return;
    // When the toggle or any of its options changes size (a count arriving widens an option, and moves the ones after
    // it), the plate follows at once. (The observer also reports the sizes it starts with, which must not cut short the
    // slide just begun.)
    const watched = [box, ...Array.from(box.children).filter((child) => child !== element)] as HTMLElement[];
    const sizeOf = () => watched.map((item) => `${item.offsetWidth}x${item.offsetHeight}`).join(',');
    let size = sizeOf();
    const observer = new ResizeObserver(() => {
      const now = sizeOf();
      if (now !== size) place(false);
      size = now;
    });
    watched.forEach((item) => observer.observe(item));
    return () => observer.disconnect();
  }, [chosen]);

  return { container, plate };
}
