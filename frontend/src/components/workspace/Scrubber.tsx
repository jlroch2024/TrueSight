// The year toggle, in the graph's top bar: one option for each year of the history, then Today. It is the drawer's
// Holdings and Alerts toggle in teal: every option the same width, the chosen one on a plate that slides to it. Click a
// year, or step with the arrow keys when it has the focus.
import type { KeyboardEvent } from 'react';
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
  const label = (year: Year) => (year === null ? 'Today' : String(year));

  const go = (next: number) => {
    const stop = stops[Math.min(stops.length - 1, Math.max(0, next))]!;
    if (stop !== value) onChange(stop);
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
      className="seg seg-teal year-seg glass ticks"
      role="slider"
      tabIndex={0}
      aria-label="Year of the Supply Chain"
      aria-orientation="horizontal"
      aria-valuemin={0}
      aria-valuemax={stops.length - 1}
      aria-valuenow={index}
      aria-valuetext={label(value)}
      onKeyDown={keys}
    >
      <span ref={plate} className="seg-plate" aria-hidden="true" />
      {stops.map((stop, at) => (
        <button
          key={label(stop)}
          type="button"
          tabIndex={-1}
          aria-selected={stop === value}
          data-chosen={stop === value}
          data-today={stop === null}
          onClick={() => go(at)}
        >
          {/* Where the top row is short of room, a year is shown as '23 (styles.css); its name stays whole. */}
          <span className="seg-label num">
            <span className="year-long">{label(stop)}</span>
            {stop !== null && <span className="year-short" aria-hidden="true">{`’${String(stop).slice(2)}`}</span>}
          </span>
        </button>
      ))}
    </div>
  );
}
