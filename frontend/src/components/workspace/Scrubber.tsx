// The year scrubber, in the graph's top bar: a stop for each year of the history, then Today. Drag along it, click a
// stop, or step with the arrow keys when it has the focus. The chosen stop sits on a plate that slides to it, as a
// Segmented toggle's does. It is a slider (not a browser-default range input): its value text is the year.
import { type KeyboardEvent, type PointerEvent, useRef } from 'react';
import { useSlidingPlate } from '../useSlidingPlate';
import type { Year } from './timeline';

export function Scrubber({
  years,
  value,
  onChange,
}: {
  // The years of the history, oldest first. Today is added after them.
  years: number[];
  value: Year;
  onChange: (year: Year) => void;
}) {
  const stops: Year[] = [...years, null];
  const index = Math.max(0, stops.indexOf(value));
  const { container, plate } = useSlidingPlate<HTMLDivElement>(index);
  const dragging = useRef(false);
  const label = (year: Year) => (year === null ? 'Today' : String(year));

  const go = (next: number) => {
    const stop = stops[Math.min(stops.length - 1, Math.max(0, next))]!;
    if (stop !== value) onChange(stop);
  };

  /** The stop under the pointer, found from where the stops are on screen. */
  const stopAt = (event: PointerEvent<HTMLDivElement>) => {
    const cells = [...(container.current?.querySelectorAll<HTMLElement>('.scrubber-stop') ?? [])];
    if (cells.length === 0) return;
    // A press on a stop chooses that stop; moving along the track finds the nearest by where the stops are.
    const pressed = event.type === 'pointerdown' ? cells.indexOf((event.target as HTMLElement).closest<HTMLElement>('.scrubber-stop')!) : -1;
    if (pressed >= 0) return go(pressed);
    let nearest = 0;
    let best = Infinity;
    cells.forEach((cell, at) => {
      const box = cell.getBoundingClientRect();
      const apart = Math.abs(event.clientX - (box.left + box.width / 2));
      if (apart < best) {
        best = apart;
        nearest = at;
      }
    });
    go(nearest);
  };

  const keys = (event: KeyboardEvent<HTMLDivElement>) => {
    const moves: Record<string, number> = {
      ArrowLeft: index - 1,
      ArrowDown: index - 1,
      ArrowRight: index + 1,
      ArrowUp: index + 1,
      Home: 0,
      End: stops.length - 1,
    };
    const next = moves[event.key];
    if (next === undefined) return;
    event.preventDefault();
    go(next);
  };

  return (
    <div
      ref={container}
      className="scrubber glass ticks"
      role="slider"
      tabIndex={0}
      aria-label="Year of the Supply Chain"
      aria-orientation="horizontal"
      aria-valuemin={0}
      aria-valuemax={stops.length - 1}
      aria-valuenow={index}
      aria-valuetext={label(value)}
      onKeyDown={keys}
      onPointerDown={(event) => {
        dragging.current = true;
        event.currentTarget.setPointerCapture?.(event.pointerId);
        stopAt(event);
      }}
      onPointerMove={(event) => dragging.current && stopAt(event)}
      onPointerUp={() => (dragging.current = false)}
      onPointerCancel={() => (dragging.current = false)}
    >
      <span ref={plate} className="scrubber-plate" aria-hidden="true" />
      {stops.map((stop) => (
        <span key={label(stop)} className="scrubber-stop" data-chosen={stop === value} data-today={stop === null}>
          {label(stop)}
        </span>
      ))}
    </div>
  );
}
