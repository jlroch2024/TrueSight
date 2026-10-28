// A link between two companies, drawn by the graph's own arrow drawing (drawArrow and drawChevron), so it is the
// graph's arrow exactly: its glow, its rail tapering towards the customer, its conveyor of still segments that light
// up as each wave of light passes, and its chevron flashing as a wave arrives. With a disruption running through it,
// it stalls in the disruption's colour, every segment pulsing together.
import { useEffect, useLayoutEffect, useRef } from 'react';
import type { Severity } from '../api/news';
import { drawArrow, drawChevron } from '../graph/render';
import { prefersStill } from './motion';

// Every link on the page is drawn by one loop, each frame, as the graph draws its own; a link out of sight is skipped.
const linksDrawn = new Set<(now: number) => void>();
let linkLoop = 0;
const linkTick = (now: number) => {
  linksDrawn.forEach((draw) => draw(now));
  linkLoop = linksDrawn.size > 0 ? requestAnimationFrame(linkTick) : 0;
};
// How far the waves of light travel each millisecond: the graph's own pace (Scene's FLOW_SPEED).
const FLOW_SPEED = 0.06;

/**
 * colour is a #rrggbb colour, as the graph's are; risk is the severity of a disruption running through the link. Its
 * canvas reaches beyond its line (see .flow canvas), so the glow fades out rather than being cut off.
 */
export function FlowLink({ colour, risk = null }: { colour: string; risk?: Severity | null }) {
  const canvas = useRef<HTMLCanvasElement | null>(null);
  const look = useRef({ colour, risk });
  useLayoutEffect(() => {
    look.current = { colour, risk };
  });
  useEffect(() => {
    const element = canvas.current;
    const context = element?.getContext('2d');
    if (!element || !context || typeof ResizeObserver === 'undefined') return;
    const still = prefersStill();
    let width = 0;
    let height = 0;
    let inSight = true;
    const resize = new ResizeObserver(([entry]) => {
      width = entry!.contentRect.width;
      height = entry!.contentRect.height;
    });
    resize.observe(element);
    const sight =
      typeof IntersectionObserver === 'undefined'
        ? null
        : new IntersectionObserver(([entry]) => {
            inSight = entry!.isIntersecting;
          });
    sight?.observe(element);
    const draw = (now: number) => {
      if (!inSight || width < 8) return;
      const dpr = window.devicePixelRatio || 1;
      const pixels = [Math.round(width * dpr), Math.round(height * dpr)] as const;
      if (element.width !== pixels[0] || element.height !== pixels[1]) [element.width, element.height] = pixels;
      context.setTransform(1, 0, 0, 1, 0, 0);
      context.clearRect(0, 0, pixels[0], pixels[1]);
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      const y = height / 2;
      const chevron = drawArrow(context, {
        start: { x: 1, y },
        end: { x: width - 3, y },
        grow: 1,
        strength: 1,
        hover: 0,
        colour: look.current.colour,
        risk: look.current.risk,
        reviewed: true,
        critical: false,
        flow: now * FLOW_SPEED,
        still,
        now,
        k: 1,
        dpr,
      });
      if (chevron) drawChevron(context, chevron, 1);
    };
    linksDrawn.add(draw);
    if (!linkLoop) linkLoop = requestAnimationFrame(linkTick);
    return () => {
      linksDrawn.delete(draw);
      resize.disconnect();
      sight?.disconnect();
    };
  }, []);
  return (
    <span className="flow" aria-hidden="true">
      <canvas ref={canvas} />
    </span>
  );
}
